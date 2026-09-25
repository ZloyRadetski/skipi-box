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
        options = options,
    )
}
