// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.presentation.ProxyGroup
import features.proxy.server.presentation.ProxyGroupCatalog
import features.proxy.server.presentation.ProxyGroupIds
import features.proxy.server.presentation.ProxyGroupKind
import features.proxy.server.presentation.ProxyGroupLabels
import features.proxy.server.presentation.ProxyGroupOptions
import features.proxy.server.presentation.ProxyGroupSelection
import features.proxy.server.presentation.ProxyGroupServer
import features.proxy.server.presentation.ProxyGroupSubscription
import features.proxy.server.presentation.createProxyGroupCatalog

/** Compatibility aliases keep the Desktop UI source stable while grouping lives in shared core. */
typealias DesktopProxyGroup = ProxyGroup
typealias DesktopProxyGroupCatalog = ProxyGroupCatalog
typealias DesktopProxyGroupKind = ProxyGroupKind
typealias DesktopProxyGroupLabels = ProxyGroupLabels
typealias DesktopProxyGroupOptions = ProxyGroupOptions
typealias DesktopProxyGroupSelection = ProxyGroupSelection

object DesktopProxyGroupIds {
    const val All = ProxyGroupIds.All
    const val Manual = ProxyGroupIds.Manual
    const val AutoBalancers = ProxyGroupIds.AutoBalancers

    fun subscription(subscriptionId: Int): String = ProxyGroupIds.subscription(subscriptionId)
}

/** Desktop only adapts persisted records; grouping and search are platform-neutral. */
object DesktopProxyGroups {
    fun create(
        serverLibrary: DesktopServerLibrary,
        subscriptionLibrary: DesktopSubscriptionLibrary,
        options: DesktopProxyGroupOptions = DesktopProxyGroupOptions(),
        enableAllProxyGroup: Boolean = options.enableAllProxyGroup,
    ): DesktopProxyGroupCatalog = createProxyGroupCatalog(
        servers = serverLibrary.servers
            .distinctBy(DesktopStoredProxyServer::id)
            .map { stored ->
                ProxyGroupServer(
                    id = stored.id,
                    subscriptionId = stored.subscriptionId,
                    server = stored.decode().getOrNull(),
                )
            },
        subscriptions = subscriptionLibrary.subscriptions.map { stored ->
            ProxyGroupSubscription(
                id = stored.id,
                url = stored.url,
                name = stored.name,
                enabled = stored.enabled,
            )
        },
        options = options.copy(enableAllProxyGroup = enableAllProxyGroup),
    )
}

internal fun <T> List<T>.reorderItem(fromIndex: Int, offset: Int): List<T>? {
    if (fromIndex !in indices) return null
    val toIndex = fromIndex + offset
    if (toIndex !in indices || fromIndex == toIndex) return null
    val mutable = toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}

internal fun reorderServerInLibrary(
    servers: List<DesktopStoredProxyServer>,
    serverId: Int,
    offset: Int,
): List<DesktopStoredProxyServer>? {
    val target = servers.firstOrNull { it.id == serverId } ?: return null
    val groupServers = servers.filter { it.subscriptionId == target.subscriptionId }
    val fromPeerIndex = groupServers.indexOfFirst { it.id == serverId }
    if (fromPeerIndex == -1) return null
    val toPeerIndex = fromPeerIndex + offset
    if (toPeerIndex !in groupServers.indices || fromPeerIndex == toPeerIndex) return null

    val targetPeer = groupServers[toPeerIndex]
    val fromGlobalIndex = servers.indexOfFirst { it.id == serverId }
    val toGlobalIndex = servers.indexOfFirst { it.id == targetPeer.id }
    if (fromGlobalIndex == -1 || toGlobalIndex == -1) return null

    val result = servers.toMutableList()
    val temp = result[fromGlobalIndex]
    result[fromGlobalIndex] = result[toGlobalIndex]
    result[toGlobalIndex] = temp
    return result
}
