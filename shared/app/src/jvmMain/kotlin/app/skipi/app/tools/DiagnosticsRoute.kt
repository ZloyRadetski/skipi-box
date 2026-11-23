// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import engine.network.withSocksProxyAuthenticator
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** How a diagnostics request reaches the network; chosen by the host, never by the tool. */
sealed interface DiagnosticsRoute {
    /** Plain connection through the host's default network. */
    data object Direct : DiagnosticsRoute

    /** Through the tunnel's local SOCKS5 listener. */
    data class Socks(
        val host: String,
        val port: Int,
        val username: String = "",
        val password: String = "",
    ) : DiagnosticsRoute
}

/**
 * Hooks for hosts that must bind probes to one specific network (Android's VPN network). The
 * defaults leave sockets and connections untouched.
 */
interface DiagnosticsNetworkBinding {
    /** Opens [url] over the bound network, or null to use the default network; throwing means binding failed. */
    fun openConnection(url: URL): HttpURLConnection? = null

    /** Binds a UDP probe socket to the network; throwing fails the probe instead of using the default route. */
    fun bind(socket: DatagramSocket) = Unit

    companion object {
        val None = object : DiagnosticsNetworkBinding {}
    }
}

internal fun DiagnosticsRoute.Socks.javaProxy(): Proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))

/**
 * Registers the SOCKS credentials for the duration of [block] so the JDK can authenticate the
 * proxy handshake. Shares the process-wide authenticator of [withSocksProxyAuthenticator], so
 * parallel probes are not serialised and no other default authenticator is wiped.
 */
internal fun <T> DiagnosticsRoute.withSocksAuthenticator(block: () -> T): T {
    val socks = this as? DiagnosticsRoute.Socks
    if (socks == null || socks.username.isBlank()) return block()
    return withSocksProxyAuthenticator(socks.port, socks.username, socks.password, block)
}
