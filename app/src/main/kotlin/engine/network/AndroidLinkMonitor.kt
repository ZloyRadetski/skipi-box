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
import features.multilink.HostNetworkSnapshot
import features.multilink.HostTransport
import features.multilink.MultilinkHostLink
import features.multilink.buildHostLinks

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

    /**
     * Starts watching. False when the networks cannot be watched at all (the callback was refused): the monitor is then
     * not running, so a later start may try again.
     */
    fun start(): Boolean {
        val manager = connectivity ?: return false
        synchronized(lock) {
            if (running) return true
            running = true
        }
        val watching = runCatching {
            manager.registerNetworkCallback(baseRequest().build(), listCallback)
        }.onFailure { error -> AppLog.warn(LogTag, "Could not watch the networks for link merging", error) }.isSuccess
        if (!watching) {
            synchronized(lock) { running = false; networks.clear() }
            return false
        }
        keepCallbacks.forEach { (transport, callback) ->
            runCatching {
                manager.requestNetwork(baseRequest().addTransportType(transport).build(), callback)
            }.onFailure { error -> AppLog.warn(LogTag, "Could not keep transport $transport connected", error) }
        }
        return true
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
        val hostNetworks = snapshot.mapNotNull { (network, info) -> hostNetworkOf(network, info) }
        return buildHostLinks(hostNetworks, mobileName = appContext.getString(R.string.multilink_kind_mobile))
    }

    private fun hostNetworkOf(network: Network, info: NetworkSnapshot): HostNetworkSnapshot? {
        val capabilities = info.capabilities ?: return null
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return null
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> HostTransport.WiFi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> HostTransport.Cellular
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> HostTransport.Ethernet
            else -> return null
        }
        val properties = info.properties ?: return null
        // An IPv6-only mobile network (NAT64) shows no IPv4 address here, so it takes part in merging over IPv6 only.
        return HostNetworkSnapshot(
            handle = network.networkHandle,
            transport = transport,
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            interfaceName = properties.interfaceName.orEmpty(),
            addresses = properties.linkAddresses.mapNotNull { it.address.hostAddress },
        )
    }

    private companion object {
        const val LogTag = "LinkMonitor"
    }
}
