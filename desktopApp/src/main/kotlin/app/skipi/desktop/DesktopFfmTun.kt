// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** TUN device owned by SKIPI Core: NetworkManager creates it, Core attaches and keeps it. */
internal class DesktopCoreTunDevice(private val core: DesktopCoreFfmNative) : DesktopTunDevice {
    override fun prepare(interfaceName: String): Result<Unit> = runCatching { core.prepareTun(interfaceName) }

    override fun open(planJson: String): Result<Int> = runCatching { core.openTun(planJson) }

    override fun close(): Result<Unit> = runCatching { core.closeTun() }
}

/**
 * hev-socks5-tunnel loaded in-process. `hev_socks5_tunnel_main_from_str` blocks until
 * `hev_socks5_tunnel_quit`, so it runs on its own thread; with a ready tun descriptor it needs no
 * privileges because it never touches the interface's addresses.
 */
internal class DesktopHevFfmTunnel(
    libraryPath: Path,
    private val startupGraceMillis: Long = 300,
    private val stopTimeoutMillis: Long = 5_000,
) : DesktopHevTunnel {
    private val linker = Linker.nativeLinker()
    private val lookup = SymbolLookup.libraryLookup(libraryPath, Arena.global())
    private val mainFromString = linker.downcallHandle(
        lookup.findOrThrow("hev_socks5_tunnel_main_from_str"),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
    )
    private val quit = linker.downcallHandle(
        lookup.findOrThrow("hev_socks5_tunnel_quit"),
        FunctionDescriptor.ofVoid(),
    )

    @Volatile
    private var thread: Thread? = null

    @Volatile
    private var exitCode: Int? = null

    @Synchronized
    override fun start(configYaml: String, tunFd: Int): Result<Unit> = runCatching {
        check(thread?.isAlive != true) { "hev-socks5-tunnel is already running" }
        exitCode = null
        val bytes = configYaml.toByteArray(Charsets.UTF_8)
        val worker = Thread({
            Arena.ofShared().use { arena ->
                val config = arena.allocate(bytes.size.toLong())
                config.asByteBuffer().put(bytes)
                exitCode = mainFromString.invokeWithArguments(config, bytes.size, tunFd) as Int
            }
        }, "hev-socks5-tunnel").apply { isDaemon = true }
        thread = worker
        worker.start()
        // hev reports nothing when it is up; a failed start returns immediately instead.
        worker.join(startupGraceMillis)
        check(worker.isAlive) { "hev-socks5-tunnel exited during start (code ${exitCode ?: "unknown"})" }
    }

    @Synchronized
    override fun stop(): Result<Unit> = runCatching {
        val worker = thread ?: return@runCatching
        if (worker.isAlive) {
            quit.invokeWithArguments()
            worker.join(TimeUnit.MILLISECONDS.toMillis(stopTimeoutMillis))
            check(!worker.isAlive) { "hev-socks5-tunnel did not stop in time" }
        }
        thread = null
    }

    override fun isRunning(): Boolean = thread?.isAlive == true
}
