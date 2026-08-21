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
