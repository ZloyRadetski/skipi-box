// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration

/**
 * Bounded loopback readiness check used before changing OS-wide proxy settings.
 * It deliberately connects to TCP only: SKIPI Core is responsible for its own HTTP
 * protocol handling, while this guard prevents Windows from targeting an
 * endpoint which has not bound yet (or whose process has already exited).
 */
class DesktopLocalProxyReadiness(
    private val tcpConnect: (String, Int, Int) -> Unit = { host, port, timeoutMillis ->
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMillis)
        }
    },
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    fun awaitLoopbackHttpEndpoint(
        port: Int,
        timeout: Duration = DefaultTimeout,
        coreState: () -> DesktopCoreState,
    ): Result<Unit> = runCatching {
        require(port in 1..65_535) { "HTTP proxy port must be in 1..65535" }
        require(!timeout.isNegative && !timeout.isZero) { "Readiness timeout must be positive" }
        val deadline = monotonicMillis() + timeout.toMillis()
        var lastFailure: Throwable? = null
        while (monotonicMillis() < deadline) {
            check(coreState().isRunning) { "SKIPI Core stopped before its local HTTP proxy became ready" }
            try {
                tcpConnect(LoopbackHost, port, ConnectTimeoutMillis)
            } catch (error: Throwable) {
                lastFailure = error
                val remaining = deadline - monotonicMillis()
                if (remaining > 0) sleep(minOf(PollIntervalMillis, remaining))
                continue
            }
            // A conflicting localhost service can accept a connection just as
            // Core exits after a bind error. Re-check the in-process session before allowing
            // Windows to target the endpoint.
            check(coreState().isRunning) { "SKIPI Core stopped while its local HTTP proxy was becoming ready" }
            return@runCatching
        }
        check(coreState().isRunning) { "SKIPI Core stopped before its local HTTP proxy became ready" }
        throw IllegalStateException(
            "SKIPI Core did not open the local HTTP proxy at $LoopbackHost:$port within ${timeout.toSeconds()} seconds",
            lastFailure,
        )
    }

    /**
     * Verifies the SOCKS inbound the TUN's hev will connect to with a real SOCKS5 handshake, and the login
     * when [username] is set. A TUN whose inbound is not where hev looks (a dynamic port that moved, a wrong
     * login) is up and carries nothing, and nothing in it says so; this turns that into a failed start.
     */
    fun awaitSocksEndpoint(
        host: String = LoopbackHost,
        port: Int,
        username: String,
        password: String,
        timeout: Duration = DefaultTimeout,
        coreState: () -> DesktopCoreState,
    ): Result<Unit> = runCatching {
        require(port in 1..65_535) { "SOCKS port must be in 1..65535" }
        val deadline = monotonicMillis() + timeout.toMillis()
        var lastFailure: Throwable? = null
        while (true) {
            check(coreState().isRunning) { "SKIPI Core stopped before its local SOCKS proxy became ready" }
            try {
                socksHandshake(host, port, username, password)
                return@runCatching
            } catch (error: SocksRejected) {
                throw error
            } catch (error: Throwable) {
                lastFailure = error
            }
            val remaining = deadline - monotonicMillis()
            if (remaining <= 0) break
            sleep(minOf(PollIntervalMillis, remaining))
        }
        check(coreState().isRunning) { "SKIPI Core stopped before its local SOCKS proxy became ready" }
        throw IllegalStateException(
            "SKIPI Core does not answer SOCKS on $host:$port, where the tunnel is sent",
            lastFailure,
        )
    }

    private class SocksRejected(message: String) : IllegalStateException(message)

    private fun socksHandshake(host: String, port: Int, username: String, password: String) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), ConnectTimeoutMillis)
            socket.soTimeout = HandshakeTimeoutMillis
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            val auth = username.isNotEmpty()
            output.write(if (auth) byteArrayOf(5, 2, 0, 2) else byteArrayOf(5, 1, 0))
            output.flush()
            val choice = input.readNBytes(2)
            if (choice.size < 2 || choice[0].toInt() != 5) {
                throw SocksRejected("$host:$port is not a SOCKS5 proxy: the port is taken by something else")
            }
            when (choice[1].toInt()) {
                0 -> if (auth) throw SocksRejected("The SOCKS proxy at $host:$port does not ask for the login the tunnel uses")
                2 -> {
                    if (!auth) throw SocksRejected("The SOCKS proxy at $host:$port asks for a login and the tunnel has none")
                    val user = username.toByteArray()
                    val pass = password.toByteArray()
                    output.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
                    output.flush()
                    val status = input.readNBytes(2)
                    if (status.size < 2 || status[1].toInt() != 0) {
                        throw SocksRejected("The SOCKS proxy at $host:$port rejected the login the tunnel uses")
                    }
                }
                else -> throw SocksRejected("The SOCKS proxy at $host:$port wants an unsupported authentication method")
            }
        }
    }

    private companion object {
        const val HandshakeTimeoutMillis = 1_500
        const val LoopbackHost = "127.0.0.1"
        const val ConnectTimeoutMillis = 250
        const val PollIntervalMillis = 50L
        val DefaultTimeout: Duration = Duration.ofSeconds(5)
    }
}
