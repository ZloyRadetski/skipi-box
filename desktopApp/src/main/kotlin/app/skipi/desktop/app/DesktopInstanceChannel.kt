// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Keeps SKIPI to one running instance per user. The first start listens on a unix socket in the
 * user's runtime directory; a later start sends its links there (one per line) and exits, so a
 * keyboard shortcut running `SKIPI skipi://toggle` acts on the application that is already up.
 */
internal class DesktopInstanceChannel private constructor(
    private val server: ServerSocketChannel,
    private val path: Path,
) : AutoCloseable {
    @Volatile
    private var closed = false

    /** Delivers every line other starts send, on a background thread. */
    fun listen(onMessage: (String) -> Unit) {
        Thread({
            while (!closed) {
                val client = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                runCatching {
                    client.use { connection ->
                        Channels.newReader(connection, StandardCharsets.UTF_8).buffered().lineSequence()
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .forEach(onMessage)
                    }
                }.onFailure { error -> DesktopLogger.error("Instance", "Failed to read a message", error) }
            }
        }, "skipi-instance-channel").apply { isDaemon = true }.start()
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { server.close() }
        runCatching { Files.deleteIfExists(path) }
    }

    companion object {
        fun defaultPath(): Path {
            val runtime = System.getenv("XDG_RUNTIME_DIR")?.takeIf(String::isNotBlank)?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), "SKIPI")
            return runtime.resolve("skipi").resolve("instance.sock")
        }

        /** Becomes the running instance, or hands [messages] to the one already running and returns null. */
        fun claimOrForward(path: Path, messages: List<String>): DesktopInstanceChannel? {
            repeat(2) {
                if (forward(path, messages)) return null
                // Nothing answers: a socket left by a crashed instance, or no instance at all.
                Files.deleteIfExists(path)
                Files.createDirectories(path.parent)
                val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                try {
                    server.bind(UnixDomainSocketAddress.of(path))
                    return DesktopInstanceChannel(server, path)
                } catch (_: IOException) {
                    // Another start bound it first; try to reach that one.
                    server.close()
                }
            }
            error("Cannot reach or start the SKIPI instance channel at $path")
        }

        private fun forward(path: Path, messages: List<String>): Boolean {
            if (!Files.exists(path)) return false
            return try {
                SocketChannel.open(UnixDomainSocketAddress.of(path)).use { channel ->
                    val writer = Channels.newWriter(channel, StandardCharsets.UTF_8)
                    messages.forEach { message -> writer.write(message + "\n") }
                    writer.flush()
                }
                true
            } catch (_: IOException) {
                false
            }
        }
    }
}
