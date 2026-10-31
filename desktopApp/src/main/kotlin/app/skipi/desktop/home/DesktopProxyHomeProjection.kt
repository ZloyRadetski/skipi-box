// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyGroupSummary
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeGroupKind
import app.skipi.app.home.ProxyServerSummary
import app.skipi.app.home.ProxySubscriptionSummary
import app.skipi.app.home.proxyHomeServerHeadline
import app.skipi.app.home.proxyHomeServerSearchText
import app.skipi.app.home.proxyHomeServerSortKey
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.UrlProxyServer
import features.proxy.server.model.getTransportDisplay
import features.proxy.server.presentation.ProxyServerPresentationFormatter
import features.proxy.server.presentation.ProxyServerPresentationNode

/** Host-renderable Home rows projected from the canonical Desktop grouping catalog. */
internal data class DesktopProxyHomeProjection(
    val groups: List<ProxyGroupSummary>,
    val servers: List<ProxyServerSummary>,
)

/** Binds shared group memberships and search/sort policy to Desktop presentation/runtime data. */
internal fun projectDesktopProxyHomeProjection(
    groupCatalog: DesktopProxyGroupCatalog,
    decodedServers: List<Pair<DesktopStoredProxyServer, ProxyServer<*>?>>,
    subscriptionLibrary: DesktopSubscriptionLibrary,
    selectedServerId: Int?,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    testingServerIds: Set<Int>,
    presentationFormatter: ProxyServerPresentationFormatter,
    presentationNodes: List<ProxyServerPresentationNode>,
    refreshingSubscriptionIds: Set<Int>,
    pingingSubscriptionIds: Set<Int>,
): DesktopProxyHomeProjection {
    val subscriptionById = HashMap<Int, DesktopStoredSubscription>(subscriptionLibrary.subscriptions.size)
    for (candidate in subscriptionLibrary.subscriptions) subscriptionById.putIfAbsent(candidate.id, candidate)
    val groups = groupCatalog.groups.map { group ->
        val subscriptionId = group.id.removePrefix("subscription:").toIntOrNull()
        val subscription = subscriptionId?.let(subscriptionById::get)
        val isPersistedGroup = subscription != null
        val kind = when (group.kind) {
            DesktopProxyGroupKind.All -> ProxyHomeGroupKind.All
            DesktopProxyGroupKind.Manual -> ProxyHomeGroupKind.Manual
            DesktopProxyGroupKind.Subscription -> ProxyHomeGroupKind.Subscription
            DesktopProxyGroupKind.AutoBalancer -> ProxyHomeGroupKind.AutoBalancer
        }
        ProxyGroupSummary(
            id = group.id,
            title = group.title,
            serverCount = group.serverCount,
            enabled = group.enabled,
            serverIds = group.serverIds.map(Int::toString).toSet(),
            canEdit = isPersistedGroup,
            canDelete = isPersistedGroup,
            canMove = isPersistedGroup,
            kind = kind,
            subscription = if (subscription != null && subscription.url.isNotBlank()) {
                ProxySubscriptionSummary(
                    id = subscription.id.toString(),
                    title = subscription.name,
                    serverCount = group.serverCount,
                    enabled = subscription.enabled,
                    refreshing = subscription.id in refreshingSubscriptionIds,
                    pinging = subscription.id in pingingSubscriptionIds,
                    updateIntervalHours = subscription.updateInterval,
                    usedBytes = desktopSubscriptionUsedBytes(
                        subscription.metadata.trafficUploadBytes,
                        subscription.metadata.trafficDownloadBytes,
                    ),
                    totalBytes = subscription.metadata.trafficTotalBytes.takeIf { it >= 0 },
                    expireAtSeconds = subscription.metadata.trafficExpireAtSeconds.takeIf { it > 0 },
                    description = subscription.metadata.description.takeIf(String::isNotBlank),
                    announcement = subscription.metadata.announce.takeIf(String::isNotBlank),
                    announcementUrl = subscription.metadata.announceUrl.takeIf(String::isNotBlank),
                    supportUrl = subscription.metadata.supportUrl.takeIf(String::isNotBlank)
                        ?: subscription.metadata.supportEmail.takeIf(String::isNotBlank)?.let { "mailto:$it" },
                    siteUrl = subscription.metadata.profileWebPageUrl.takeIf(String::isNotBlank),
                    lastUpdatedAtMillis = subscription.metadata.lastUpdatedAtMillis.takeIf { it > 0 },
                )
            } else null,
        )
    }
    val visibleServerIds = groupCatalog.groups.flatMapTo(linkedSetOf()) { group -> group.serverIds }
    // The first non-All group that lists a server, and the first node of an id, as a linear scan would find them.
    val firstGroupByServerId = HashMap<Int, DesktopProxyGroup>(visibleServerIds.size)
    for (candidate in groupCatalog.groups) {
        if (candidate.kind == DesktopProxyGroupKind.All) continue
        for (serverId in candidate.serverIds) firstGroupByServerId.putIfAbsent(serverId, candidate)
    }
    val nodeById = HashMap<Int, ProxyServerPresentationNode>(presentationNodes.size)
    for (node in presentationNodes) nodeById.putIfAbsent(node.id, node)
    val servers = decodedServers.mapNotNull { (stored, server) ->
        val current = server ?: return@mapNotNull null
        if (stored.id !in visibleServerIds) return@mapNotNull null

        val info = current.getInfo()
        val headline = proxyHomeServerHeadline(
            remarks = info.remarks,
            protocol = info.protocol,
            serverId = stored.id,
            isStrategyGroup = current is StrategyGroup,
        )
        val group = firstGroupByServerId[stored.id]
        val node = nodeById[stored.id]
        val address = node?.let { presentationFormatter.displayOf(it, presentationNodes).summary } ?: info.address
        val latencyResult = latencyByServerId[stored.id]
        ProxyServerSummary(
            id = stored.id.toString(),
            title = headline.title,
            address = address,
            protocol = info.protocol,
            transport = current.getTransportDisplay(),
            flag = headline.flag,
            selected = stored.id == selectedServerId,
            latencyMs = (latencyResult as? DesktopServerLatencyResult.Success)?.milliseconds,
            latencyTesting = stored.id in testingServerIds,
            latencyError = latencyResult is DesktopServerLatencyResult.Error || latencyResult is DesktopServerLatencyResult.Timeout,
            canTest = current.desktopTcpEndpointOrNull() != null,
            availableCopyFormats = buildSet {
                add(ProxyHomeCopyFormat.FullJson)
                if (current is UrlProxyServer<*>) {
                    add(ProxyHomeCopyFormat.Url)
                    add(ProxyHomeCopyFormat.QrCode)
                }
            },
            isStrategyGroup = current is StrategyGroup,
            groupId = group?.id,
            searchText = proxyHomeServerSearchText(
                title = headline.title,
                remarks = info.remarks,
                address = info.address,
                protocol = info.protocol,
                groupName = group?.title.orEmpty(),
            ),
            sortKey = proxyHomeServerSortKey(remarks = info.remarks, title = headline.title),
        )
    }
    return DesktopProxyHomeProjection(groups = groups, servers = servers)
}
