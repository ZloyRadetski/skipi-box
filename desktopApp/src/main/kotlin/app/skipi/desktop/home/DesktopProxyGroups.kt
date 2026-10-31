// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeGroupMembership
import app.skipi.app.home.moveProxyHomeItem
import app.skipi.app.proxy.ProxyServerGroupInput
import app.skipi.app.proxy.ProxyServerGroupServerInput
import app.skipi.app.proxy.ProxyServerGroupingRequest
import app.skipi.app.proxy.ProxyServerGroupingResult
import app.skipi.app.proxy.groupProxyServersForHome
import app.skipi.app.home.proxyHomeGroupMemberships
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode

/** Desktop string IDs remain storage/UI aliases; shared/app owns grouping and visibility policy. */
data class DesktopProxyGroup(
    val id: String,
    val kind: DesktopProxyGroupKind,
    val title: String,
    val serverIds: List<Int>,
    val enabled: Boolean = true,
) {
    val serverCount: Int get() = serverIds.size
}

enum class DesktopProxyGroupKind { All, Manual, Subscription, AutoBalancer }

data class DesktopProxyGroupLabels(
    val allProxies: String = "Все прокси",
    val manualServers: String = "Ручные серверы",
    val autoBalancers: String = "Автобалансировщики",
)

data class DesktopProxyGroupOptions(
    val subscriptionEnabledById: Map<Int, Boolean> = emptyMap(),
    val activeTrafficConfigId: Int? = null,
    val enableAllProxyGroup: Boolean = true,
    val labels: DesktopProxyGroupLabels = DesktopProxyGroupLabels(),
)

data class DesktopProxyGroupSelection(val group: DesktopProxyGroup?, val serverIds: List<Int>)

/** Desktop-only string adapter over the canonical numeric shared grouping result. */
class DesktopProxyGroupCatalog internal constructor(
    val groups: List<DesktopProxyGroup>,
    private val request: ProxyServerGroupingRequest,
    private val result: ProxyServerGroupingResult,
) {
    val defaultGroupId: String?
        get() = groups.firstOrNull { group -> group.kind != DesktopProxyGroupKind.All && group.enabled }?.id
            ?: groups.firstOrNull { group -> group.kind != DesktopProxyGroupKind.All }?.id

    fun group(groupId: String?): DesktopProxyGroup? = groups.firstOrNull { it.id == groupId }

    fun select(groupId: String? = null): DesktopProxyGroupSelection = selection(groupId, query = "")

    fun filter(groupId: String? = null, query: String = ""): DesktopProxyGroupSelection = selection(groupId, query)

    private fun selection(groupId: String?, query: String): DesktopProxyGroupSelection {
        val requestedGroupId = when {
            groupId != null -> sharedGroupId(groupId) ?: Int.MIN_VALUE
            else -> defaultGroupId?.let(::sharedGroupId) ?: Int.MIN_VALUE
        }
        val selected = groupProxyServersForHome(
            request.copy(selectedGroupId = requestedGroupId, searchQuery = query),
        )
        return DesktopProxyGroupSelection(
            group = group(desktopGroupId(selected.selectedTabId)),
            serverIds = selected.currentFilteredServerIds,
        )
    }
}

object DesktopProxyGroupIds {
    /** Desktop keeps manual servers outside the positive subscription ID namespace. */
    const val DefaultManualSubscriptionId = -1
    const val All = "all"
    const val Manual = "manual"
    const val AutoBalancers = "auto-balancers"

    fun subscription(subscriptionId: Int): String {
        require(subscriptionId > 0) { "Subscription ID must be positive" }
        return "subscription:$subscriptionId"
    }
}

private const val SharedDesktopManualGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId

private fun sharedGroupId(desktopGroupId: String): Int? = when (desktopGroupId) {
    DesktopProxyGroupIds.All -> 0
    DesktopProxyGroupIds.AutoBalancers -> -2
    DesktopProxyGroupIds.Manual -> SharedDesktopManualGroupId
    else -> desktopGroupId.removePrefix("subscription:").toIntOrNull()?.takeIf { id -> id > 0 }
}

private fun desktopGroupId(sharedGroupId: Int): String = when (sharedGroupId) {
    0 -> DesktopProxyGroupIds.All
    -2 -> DesktopProxyGroupIds.AutoBalancers
    SharedDesktopManualGroupId -> DesktopProxyGroupIds.Manual
    else -> DesktopProxyGroupIds.subscription(sharedGroupId)
}

object DesktopProxyGroups {
    fun create(
        serverLibrary: DesktopServerLibrary,
        subscriptionLibrary: DesktopSubscriptionLibrary,
        options: DesktopProxyGroupOptions = DesktopProxyGroupOptions(),
        enableAllProxyGroup: Boolean = options.enableAllProxyGroup,
    ): DesktopProxyGroupCatalog {
        val subscriptions = subscriptionLibrary.subscriptions
        val subscriptionById = subscriptions.associateBy { subscription -> subscription.id }
        val groups = listOf(
            ProxyServerGroupInput(
                id = SharedDesktopManualGroupId,
                name = options.labels.manualServers,
                enabled = true,
                builtIn = true,
            ),
        ) + subscriptions.map { subscription ->
            ProxyServerGroupInput(
                id = subscription.id,
                name = subscription.name.trim().ifBlank { subscription.url },
                enabled = options.subscriptionEnabledById[subscription.id] ?: subscription.enabled,
            )
        }
        val servers = serverLibrary.servers
            .distinctBy(DesktopStoredProxyServer::id)
            .mapNotNull { stored ->
                val server = stored.decode().getOrNull() ?: return@mapNotNull null
                val strategy = server as? StrategyGroup
                // The persisted group marker is authoritative for every server type:
                // -2 is Auto, null is Manual, and positive IDs identify subscriptions.
                val groupId = stored.subscriptionId ?: SharedDesktopManualGroupId
                ProxyServerGroupServerInput(
                    id = stored.id,
                    groupId = groupId,
                    remarks = server.getInfo().remarks,
                    isStrategyGroup = strategy != null,
                    sourceTrafficConfigId = strategy?.sourceTrafficConfigId,
                    displayMode = strategy?.displayMode ?: StrategyGroupDisplayMode.ALWAYS,
                )
            }
        val request = ProxyServerGroupingRequest(
            groups = groups,
            servers = servers,
            activeTrafficConfigId = options.activeTrafficConfigId,
            selectedGroupId = SharedDesktopManualGroupId,
            searchQuery = "",
            enableAllGroup = enableAllProxyGroup,
            allGroupName = options.labels.allProxies,
            defaultGroupName = options.labels.manualServers,
            autoBalancerGroupName = options.labels.autoBalancers,
            defaultGroupId = SharedDesktopManualGroupId,
            autoBalancerGroupId = -2,
            allGroupId = 0,
        )
        val result = groupProxyServersForHome(request)
        val memberships: List<ProxyHomeGroupMembership> = proxyHomeGroupMemberships(request, result)
        val groupInputs = request.groups.associateBy(ProxyServerGroupInput::id)
        val desktopGroups = memberships.map { membership ->
            val input = groupInputs[membership.id]
            DesktopProxyGroup(
                id = desktopGroupId(membership.id),
                kind = when {
                    membership.id == 0 -> DesktopProxyGroupKind.All
                    membership.id == -2 -> DesktopProxyGroupKind.AutoBalancer
                    membership.id == SharedDesktopManualGroupId -> DesktopProxyGroupKind.Manual
                    subscriptionById[membership.id]?.url.isNullOrBlank() -> DesktopProxyGroupKind.Manual
                    else -> DesktopProxyGroupKind.Subscription
                },
                title = membership.title,
                serverIds = membership.serverIds,
                enabled = input?.enabled ?: true,
            )
        }
        return DesktopProxyGroupCatalog(desktopGroups, request, result)
    }
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
    fromServerId: Int,
    toServerId: Int,
): List<DesktopStoredProxyServer> = moveProxyHomeItem(
    items = servers,
    fromId = fromServerId,
    toId = toServerId,
    idOf = DesktopStoredProxyServer::id,
)
