// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.time.Duration

/**
 * The outcome of one non-invasive TCP reachability probe.
 *
 * A successful TCP handshake says that the endpoint accepted a connection; it
 * does not validate the proxy protocol, credentials, or a tunnel route.
 */
sealed interface DesktopServerLatencyResult {
    data class Success(val milliseconds: Long) : DesktopServerLatencyResult

    data object Timeout : DesktopServerLatencyResult

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : DesktopServerLatencyResult
}

/** Concrete TCP endpoint extracted from a normal SKIPI proxy server. */
data class DesktopServerTcpEndpoint(
    val host: String,
    val port: Int,
)

/** Strategy/chain/custom entries have no single endpoint and therefore cannot be TCP-probed directly. */
fun ProxyServer<*>.desktopTcpEndpointOrNull(): DesktopServerTcpEndpoint? {
    val endpoint = when (this) {
        is HTTP -> server to port
        is Socks -> server to port
        is Shadowsocks -> server to port
        is VMess -> server to port
        is VLESS -> server to port
        is Trojan -> server to port
        is Hysteria2 -> server to port
        is Wireguard -> server to port
        else -> return null
    }
    return endpoint.second.toIntOrNull()?.let { port ->
        DesktopServerTcpEndpoint(host = endpoint.first, port = port)
    }
}

/** Opens and closes a TCP connection. Kept injectable so UI tests never need a network. */
fun interface DesktopTcpConnector {
    @Throws(IOException::class)
    fun connect(host: String, port: Int, timeoutMillis: Int)
}

/** Monotonic time source used exclusively for elapsed latency. */
fun interface DesktopMonotonicClock {
    fun nowNanos(): Long
}

private object SocketDesktopTcpConnector : DesktopTcpConnector {
    override fun connect(host: String, port: Int, timeoutMillis: Int) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMillis)
        }
    }
}

private object SystemDesktopMonotonicClock : DesktopMonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

/**
 * Performs a bounded TCP connect check for a proxy endpoint.
 *
 * This deliberately has no persistence, retries, proxy traffic, or side
 * effects beyond the short TCP handshake. Invalid input and connection errors
 * are represented as [DesktopServerLatencyResult.Error] so a server card can
 * render a failure without crashing the screen.
 */
class DesktopServerLatencyTester(
    private val connector: DesktopTcpConnector = SocketDesktopTcpConnector,
    private val clock: DesktopMonotonicClock = SystemDesktopMonotonicClock,
) {
    fun measure(
        host: String,
        port: Int,
        timeout: Duration = DefaultDesktopServerLatencyTimeout,
    ): DesktopServerLatencyResult {
        val normalizedHost = host.trim()
        if (normalizedHost.isEmpty()) {
            return DesktopServerLatencyResult.Error("Server host is empty")
        }
        if (port !in 1..65535) {
            return DesktopServerLatencyResult.Error("Server port must be between 1 and 65535")
        }
        val timeoutMillis = timeout.toConnectTimeoutMillisOrNull()
            ?: return DesktopServerLatencyResult.Error("Latency timeout must be greater than zero")

        val startedAtNanos = clock.nowNanos()
        return try {
            connector.connect(normalizedHost, port, timeoutMillis)
            val elapsedNanos = (clock.nowNanos() - startedAtNanos).coerceAtLeast(0)
            DesktopServerLatencyResult.Success(elapsedNanos / NanosPerMillisecond)
        } catch (error: SocketTimeoutException) {
            DesktopServerLatencyResult.Timeout
        } catch (error: IOException) {
            DesktopServerLatencyResult.Error(error.message.orEmpty().ifBlank { error.javaClass.simpleName }, error)
        } catch (error: SecurityException) {
            DesktopServerLatencyResult.Error(error.message.orEmpty().ifBlank { error.javaClass.simpleName }, error)
        } catch (error: IllegalArgumentException) {
            DesktopServerLatencyResult.Error(error.message.orEmpty().ifBlank { error.javaClass.simpleName }, error)
        }
    }
}

val DefaultDesktopServerLatencyTimeout: Duration = Duration.ofSeconds(5)

private const val NanosPerMillisecond = 1_000_000L

private fun Duration.toConnectTimeoutMillisOrNull(): Int? {
    if (isZero || isNegative) return null
    val millis = try {
        toMillis()
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
    return millis.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
