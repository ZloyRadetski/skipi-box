// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * hev-socks5-tunnel on Windows, run as its own process: its MSYS runtime cannot be loaded into the
 * JVM the way the Linux library is. hev creates and configures the Wintun adapter itself, so no
 * descriptor is passed; the process runs from its own directory to find `wintun.dll` and the MSYS
 * runtime next to it.
 */
internal class DesktopHevProcessTunnel(
    private val executable: Path,
    private val startProcess: (command: List<String>, directory: Path) -> Process = ::startHevProcess,
    private val startupGraceMillis: Long = 1_500,
    private val stopTimeoutMillis: Long = 5_000,
) : DesktopHevTunnel {
    private var process: Process? = null
    private var configFile: Path? = null

    @Synchronized
    override fun start(configYaml: String, tunFd: Int): Result<Unit> = runCatching {
        check(process?.isAlive != true) { "hev-socks5-tunnel is already running" }
        val config = Files.createTempFile("skipi-hev-", ".yml")
        Files.writeString(config, configYaml)
        val started = try {
            startProcess(listOf(executable.toString(), config.toString()), executable.parent)
        } catch (error: Throwable) {
            Files.deleteIfExists(config)
            throw error
        }
        process = started
        configFile = config
        // hev reports nothing when it is up; a failed start exits at once instead.
        if (started.waitFor(startupGraceMillis, TimeUnit.MILLISECONDS)) {
            val output = runCatching { started.inputStream.readNBytes(MaxOutputBytes).decodeToString().trim() }
                .getOrDefault("")
            cleanUp()
            error(
                "hev-socks5-tunnel exited during start (code ${started.exitValue()})" +
                    if (output.isNotEmpty()) ": $output" else "",
            )
        }
        drainOutput(started)
    }

    @Synchronized
    override fun stop(): Result<Unit> = runCatching {
        val running = process ?: return@runCatching
        try {
            if (running.isAlive) {
                running.destroy()
                if (!running.waitFor(stopTimeoutMillis, TimeUnit.MILLISECONDS)) {
                    running.destroyForcibly()
                    check(running.waitFor(stopTimeoutMillis, TimeUnit.MILLISECONDS) || !running.isAlive) {
                        "hev-socks5-tunnel did not stop in time"
                    }
                }
            }
        } finally {
            cleanUp()
        }
    }

    @Synchronized
    override fun isRunning(): Boolean = process?.isAlive == true

    private fun cleanUp() {
        process = null
        configFile?.let { file -> runCatching { Files.deleteIfExists(file) } }
        configFile = null
    }

    private fun drainOutput(started: Process) {
        Thread({
            runCatching {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> DesktopLogger.info("Hev", line) }
                }
            }
        }, "hev-socks5-tunnel-output").apply { isDaemon = true }.start()
    }

    private companion object {
        const val MaxOutputBytes = 4_096
    }
}

private fun startHevProcess(command: List<String>, directory: Path): Process =
    ProcessBuilder(command)
        .directory(directory.toFile())
        .redirectErrorStream(true)
        .start()
