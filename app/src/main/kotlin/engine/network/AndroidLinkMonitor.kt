// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.R
import features.logs.AppLog
import features.multilink.MultilinkHostLink
import features.multilink.MultilinkLinkKind
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Watches the networks the phone is connected to and reports them as the links link merging can spread connections
 * over. Android keeps only the best network up for the apps, and lets a mobile connection sleep while Wi-Fi works, so
 * the monitor asks for Wi-Fi and mobile data to stay connected for as long as it runs: that is what merging needs,
 * and it is also what costs mobile data and battery, which is why the monitor only runs while merging is on.
 */
internal class AndroidLinkMonitor(
    context: Context,
    private val onLinks: (List<MultilinkHostLink>) -> Unit,
) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val lock = Any()
    private val networks = LinkedHashMap<Network, NetworkSnapshot>()
    private var running = false

    private class NetworkSnapshot(val capabilities: NetworkCapabilities?, val properties: LinkProperties?)

    private val listCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh(network)
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh(network)
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refresh(network)
        override fun onLost(network: Network) {
            synchronized(lock) { networks.remove(network) }
            report()
        }
    }

    // Requests that keep Wi-Fi and mobile data connected; they carry no traffic themselves.
    private val keepCallbacks = listOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_CELLULAR)
        .associateWith { object : ConnectivityManager.NetworkCallback() {} }

    fun start() {
        val manager = connectivity ?: return
        synchronized(lock) {
            if (running) return
            running = true
        }
        runCatching {
            manager.registerNetworkCallback(baseRequest().build(), listCallback)
        }.onFailure { error -> AppLog.warn(LogTag, "Could not watch the networks for link merging", error) }
        keepCallbacks.forEach { (transport, callback) ->
            runCatching {
                manager.requestNetwork(baseRequest().addTransportType(transport).build(), callback)
            }.onFailure { error -> AppLog.warn(LogTag, "Could not keep transport $transport connected", error) }
        }
    }

    fun stop() {
        val manager = connectivity
        synchronized(lock) {
            if (!running) return
            running = false
            networks.clear()
        }
        if (manager != null) {
            runCatching { manager.unregisterNetworkCallback(listCallback) }
            keepCallbacks.values.forEach { callback -> runCatching { manager.unregisterNetworkCallback(callback) } }
        }
    }

    private fun baseRequest() = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

    private fun refresh(network: Network) {
        val manager = connectivity ?: return
        synchronized(lock) {
            if (!running) return
            networks[network] = NetworkSnapshot(manager.getNetworkCapabilities(network), manager.getLinkProperties(network))
        }
        report()
    }

    private fun report() {
        val links = synchronized(lock) { if (running) buildLinks(networks.toMap()) else return }
        onLinks(links)
    }

    private fun buildLinks(snapshot: Map<Network, NetworkSnapshot>): List<MultilinkHostLink> {
        val used = HashSet<String>()
        val out = ArrayList<MultilinkHostLink>()
        for ((network, info) in snapshot) {
            val capabilities = info.capabilities ?: continue
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val (baseId, kind) = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi" to MultilinkLinkKind.WiFi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile" to MultilinkLinkKind.Mobile
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet" to MultilinkLinkKind.Ethernet
                else -> continue
            }
            val properties = info.properties ?: continue
            val interfaceName = properties.interfaceName.orEmpty()
            // The id outlives a reconnect (a new Network every time), so settings stay with the link; a second
            // network of the same kind is told apart by its interface.
            val id = if (used.add(baseId)) baseId else "$baseId:$interfaceName"
            if (id != baseId && !used.add(id)) continue
            val addresses = addressesOf(properties)
            val ipv4 = addresses.filterIsInstance<Inet4Address>().firstOrNull { !it.isLinkLocalAddress && !it.isLoopbackAddress }
            val ipv6 = addresses.filterIsInstance<Inet6Address>().firstOrNull(::isGlobalIpv6)
            if (ipv4 == null && ipv6 == null) continue
            out += MultilinkHostLink(
                id = id,
                name = nameOf(kind, interfaceName, id != baseId),
                kind = kind,
                ipv4 = ipv4?.hostAddress.orEmpty(),
                ipv6 = ipv6?.hostAddress?.substringBefore("%").orEmpty(),
                handle = network.networkHandle,
                phone = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            )
        }
        return out.sortedBy { it.id }
    }

    private fun nameOf(kind: MultilinkLinkKind, interfaceName: String, second: Boolean): String {
        val base = when (kind) {
            MultilinkLinkKind.WiFi -> "Wi-Fi"
            MultilinkLinkKind.Mobile -> appContext.getString(R.string.multilink_kind_mobile)
            MultilinkLinkKind.Ethernet -> "Ethernet"
            else -> interfaceName
        }
        return if (second && interfaceName.isNotEmpty()) "$base ($interfaceName)" else base
    }

    // An IPv6-only mobile network (NAT64) shows no IPv4 address here, so it takes part in merging over IPv6 only.
    private fun addressesOf(properties: LinkProperties): List<InetAddress> = properties.linkAddresses.map { it.address }

    /** A global unicast address: not link-local, not a unique local one (fc00::/7), not loopback. */
    private fun isGlobalIpv6(address: Inet6Address): Boolean =
        !address.isLinkLocalAddress && !address.isLoopbackAddress && !address.isSiteLocalAddress &&
            !address.isMulticastAddress && (address.address[0].toInt() and 0xFE) != 0xFC

    private companion object {
        const val LogTag = "LinkMonitor"
    }
}
