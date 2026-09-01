// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration

/**
 * Bounded loopback readiness check used before changing OS-wide proxy settings.
 * It deliberately connects to TCP only: Xray is responsible for its own HTTP
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
        processState: () -> DesktopXrayProcessState,
    ): Result<Unit> = runCatching {
        require(port in 1..65_535) { "HTTP proxy port must be in 1..65535" }
        require(!timeout.isNegative && !timeout.isZero) { "Readiness timeout must be positive" }
        val deadline = monotonicMillis() + timeout.toMillis()
        var lastFailure: Throwable? = null
        while (monotonicMillis() < deadline) {
            check(processState().isRunning) { "Xray stopped before its local HTTP proxy became ready" }
            try {
                tcpConnect(LoopbackHost, port, ConnectTimeoutMillis)
            } catch (error: Throwable) {
                lastFailure = error
                val remaining = deadline - monotonicMillis()
                if (remaining > 0) sleep(minOf(PollIntervalMillis, remaining))
                continue
            }
            // A conflicting localhost service can accept a connection just as
            // Xray exits after a bind error. Re-check the child before allowing
            // Windows to target the endpoint.
            check(processState().isRunning) { "Xray stopped while its local HTTP proxy was becoming ready" }
            return@runCatching
        }
        check(processState().isRunning) { "Xray stopped before its local HTTP proxy became ready" }
        throw IllegalStateException(
            "Xray did not open the local HTTP proxy at $LoopbackHost:$port within ${timeout.toSeconds()} seconds",
            lastFailure,
        )
    }

    private companion object {
        const val LoopbackHost = "127.0.0.1"
        const val ConnectTimeoutMillis = 250
        const val PollIntervalMillis = 50L
        val DefaultTimeout: Duration = Duration.ofSeconds(5)
    }
}
