// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL

/**
 * Helpers for routing the app's own traffic through the active VPN tunnel.
 *
 * The app excludes itself from its own VPN (see SkipiVpnService) to keep
 * Xray's outbound connections from looping back into the tun interface, so
 * plain sockets created by this app bypass the tunnel. While a tunnel is up,
 * sockets must be explicitly bound to the VPN network to traverse it exactly
 * like any other app's traffic.
 */
object TunnelNetworks {

    /**
     * Runs a local SOCKS operation with credentials while the connection is
     * established. A SOCKS proxy authenticates lazily at connect time, so
     * merely configuring the [Proxy] object is not enough.
     */
    fun <T> withLocalProxyAuthenticator(block: () -> T): T {
        val options = LocalProxyRuntime.current() ?: return block()
        return withSocksProxyAuthenticator(
            port = options.port,
            username = options.username,
            password = options.password,
            block = block,
        )
    }

    /** Shared by resource downloads that use an explicit local SOCKS proxy. */
    fun <T> withSocksProxyAuthenticator(
        port: Int,
        username: String,
        password: String,
        block: () -> T,
    ): T {
        if (username.isBlank()) return block()
        synchronized(SocksAuthenticatorLock) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    if (!isSocksProxyAuthenticationRequest(requestingPort, port)) return null
                    return PasswordAuthentication(username, password.toCharArray())
                }
            })
            return try {
                block()
            } finally {
                Authenticator.setDefault(null)
            }
        }
    }

    /** The device's active VPN network, or null when no tunnel is up. */
    fun locateVpnNetwork(context: Context?): Network? {
        val connectivity = context?.getSystemService(ConnectivityManager::class.java) ?: return null
        return connectivity.allNetworks?.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    /**
     * Opens an HTTP connection to [url] through the active VPN tunnel when one
     * is up, otherwise through the default (direct) network path.
     */
    fun openHttpConnection(context: Context?, url: URL): HttpURLConnection {
        val runtimeOptions = LocalProxyRuntime.current()
        if (runtimeOptions != null) {
            val listenHost = if (runtimeOptions.listenAddress == NetworkDefaults.IPV4_ANY_ADDRESS) {
                LocalProxyLoopbackAddress
            } else {
                runtimeOptions.listenAddress
            }
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(listenHost, runtimeOptions.port))
            return url.openConnection(proxy) as HttpURLConnection
        }

        val vpnNetwork = locateVpnNetwork(context)
        if (vpnNetwork != null) {
            val connection = runCatching { vpnNetwork.openConnection(url) as? HttpURLConnection }.getOrNull()
            if (connection != null) return connection
        }

        return url.openConnection() as HttpURLConnection
    }
}

/**
 * Android's SOCKS implementation does not consistently expose the proxy host
 * to [Authenticator], but the dynamically allocated local port is unique.
 */
internal fun isSocksProxyAuthenticationRequest(requestingPort: Int, proxyPort: Int): Boolean {
    return requestingPort == proxyPort && proxyPort in 1..65_535
}

private val SocksAuthenticatorLock = Any()
