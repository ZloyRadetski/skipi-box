// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
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
        val vpnNetwork = locateVpnNetwork(context)
        return vpnNetwork?.openConnection(url) as? HttpURLConnection
            ?: url.openConnection() as HttpURLConnection
    }
}
