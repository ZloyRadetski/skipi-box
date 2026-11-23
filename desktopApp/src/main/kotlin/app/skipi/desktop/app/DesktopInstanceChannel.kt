// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

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
                // A stalled or flooding client must never hold up accept(): each connection is read on its
                // own short-lived thread under a deadline and a size cap, and the number in flight is capped.
                if (!inFlight.tryAcquire()) {
                    runCatching { client.close() }
                    continue
                }
                Thread({
                    try {
                        handle(client, onMessage)
                    } finally {
                        inFlight.release()
                    }
                }, "skipi-instance-client").apply { isDaemon = true }.start()
            }
        }, "skipi-instance-channel").apply { isDaemon = true }.start()
    }

    private fun handle(client: SocketChannel, onMessage: (String) -> Unit) {
        val deadline = watchdog.schedule({ runCatching { client.close() } }, ClientReadTimeoutSeconds, TimeUnit.SECONDS)
        try {
            val text = client.use { connection -> readBounded(connection) } ?: return
            text.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach(onMessage)
        } catch (error: Exception) {
            DesktopLogger.error("Instance", "Failed to read a message", error)
        } finally {
            deadline.cancel(false)
        }
    }

    /** Everything up to EOF, or null (the message is dropped) when it is larger than [MaxMessageBytes]. */
    private fun readBounded(connection: SocketChannel): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(4096)
        while (true) {
            buffer.clear()
            val read = connection.read(buffer)
            if (read < 0) break
            if (out.size() + read > MaxMessageBytes) return null
            out.write(buffer.array(), 0, read)
        }
        return out.toString(StandardCharsets.UTF_8)
    }

    private val inFlight = Semaphore(MaxConcurrentClients)

    override fun close() {
        if (closed) return
        closed = true
        runCatching { server.close() }
        runCatching { Files.deleteIfExists(path) }
    }

    companion object {
        private const val ClientReadTimeoutSeconds = 5L
        private const val MaxMessageBytes = 64 * 1024
        private const val MaxConcurrentClients = 8

        private val watchdog: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "skipi-instance-watchdog").apply { isDaemon = true }
        }

        /** Directory and socket readable by the owner only; the default umask would let other local users in. */
        private fun restrictToOwner(path: Path, directory: Boolean) {
            val permissions = if (directory) "rwx------" else "rw-------"
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
            } catch (_: UnsupportedOperationException) {
                // Not a POSIX file system (Windows): the profile directory is already per-user.
            } catch (_: IOException) {
            }
        }

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
                restrictToOwner(path.parent, directory = true)
                val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                try {
                    server.bind(UnixDomainSocketAddress.of(path))
                    restrictToOwner(path, directory = false)
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
