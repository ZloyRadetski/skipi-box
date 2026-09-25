// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.presentation

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode

/** Stable ids shared by proxy list presentations on every platform. */
object ProxyGroupIds {
    const val All = "all"
    const val Manual = "manual"
    const val AutoBalancers = "auto-balancers"

    fun subscription(subscriptionId: Int): String {
        require(subscriptionId > 0) { "Subscription ID must be positive" }
        return "subscription:$subscriptionId"
    }
}

enum class ProxyGroupKind { All, Manual, Subscription, AutoBalancer }

data class ProxyGroup(
    val id: String,
    val kind: ProxyGroupKind,
    val title: String,
    val serverIds: List<Int>,
    val enabled: Boolean = true,
) {
    val serverCount: Int get() = serverIds.size
}

data class ProxyGroupLabels(
    val allProxies: String = "Все прокси",
    val manualServers: String = "Ручные серверы",
    val autoBalancers: String = "Автобалансировщики",
)

data class ProxyGroupOptions(
    val subscriptionEnabledById: Map<Int, Boolean> = emptyMap(),
    val activeTrafficConfigId: Int? = null,
    val enableAllProxyGroup: Boolean = true,
    val labels: ProxyGroupLabels = ProxyGroupLabels(),
)

data class ProxyGroupSelection(val group: ProxyGroup?, val serverIds: List<Int>)

data class ProxyGroupServer(
    val id: Int,
    val subscriptionId: Int? = null,
    val server: ProxyServer<*>? = null,
)

data class ProxyGroupSubscription(
    val id: Int,
    val url: String,
    val name: String = "",
    val enabled: Boolean = true,
)

class ProxyGroupCatalog internal constructor(
    val groups: List<ProxyGroup>,
    private val searchTextByServerId: Map<Int, String>,
) {
    val defaultGroupId: String?
        get() = groups.firstOrNull { it.kind != ProxyGroupKind.All && it.enabled }?.id
            ?: groups.firstOrNull { it.kind != ProxyGroupKind.All }?.id

    fun group(groupId: String?): ProxyGroup? = groups.firstOrNull { it.id == groupId }

    fun select(groupId: String? = null): ProxyGroupSelection {
        val selected = group(groupId) ?: group(defaultGroupId)
        return ProxyGroupSelection(selected, selected?.serverIds.orEmpty())
    }

    fun filter(groupId: String? = null, query: String = ""): ProxyGroupSelection {
        val selection = select(groupId)
        val keyword = query.trim()
        if (keyword.isEmpty()) return selection
        return selection.copy(serverIds = selection.serverIds.filter { id ->
            searchTextByServerId[id]?.contains(keyword, ignoreCase = true) == true
        })
    }
}

/** Pure grouping and search policy. No persistence, clock, or platform API is used. */
fun createProxyGroupCatalog(
    servers: List<ProxyGroupServer>,
    subscriptions: List<ProxyGroupSubscription>,
    options: ProxyGroupOptions = ProxyGroupOptions(),
): ProxyGroupCatalog {
    val records = servers.distinctBy(ProxyGroupServer::id)
    val subscriptionsById = subscriptions.associateBy(ProxyGroupSubscription::id)
    val knownSubscriptionIds = subscriptionsById.keys

    fun subscriptionEnabled(id: Int): Boolean =
        options.subscriptionEnabledById[id] ?: subscriptionsById[id]?.enabled ?: true

    val visibleStrategies = records.filter { record ->
        (record.server as? StrategyGroup)?.isVisibleInProxyGroup(options.activeTrafficConfigId) == true
    }
    val direct = records.filter { it.server !is StrategyGroup }
    val manual = direct.filter { it.subscriptionId == null }
    val bySubscription = direct
        .filter { it.subscriptionId in knownSubscriptionIds }
        .groupBy { requireNotNull(it.subscriptionId) }

    val baseGroups = buildList {
        if (visibleStrategies.isNotEmpty()) add(
            ProxyGroup(
                id = ProxyGroupIds.AutoBalancers,
                kind = ProxyGroupKind.AutoBalancer,
                title = options.labels.autoBalancers,
                serverIds = visibleStrategies.map(ProxyGroupServer::id),
            ),
        )
        subscriptions.forEach { subscription ->
            add(
                ProxyGroup(
                    id = ProxyGroupIds.subscription(subscription.id),
                    kind = ProxyGroupKind.Subscription,
                    title = subscription.name.trim().ifBlank { subscription.url },
                    serverIds = bySubscription[subscription.id].orEmpty().map(ProxyGroupServer::id),
                    enabled = subscriptionEnabled(subscription.id),
                ),
            )
        }
        if (manual.isNotEmpty()) add(
            ProxyGroup(
                id = ProxyGroupIds.Manual,
                kind = ProxyGroupKind.Manual,
                title = options.labels.manualServers,
                serverIds = manual.map(ProxyGroupServer::id),
            ),
        )
    }

    val allGroup = if (options.enableAllProxyGroup && baseGroups.size > 1) {
        ProxyGroup(
            id = ProxyGroupIds.All,
            kind = ProxyGroupKind.All,
            title = options.labels.allProxies,
            serverIds = records.filter { record ->
                when (val server = record.server) {
                    is StrategyGroup -> server.isVisibleInProxyGroup(options.activeTrafficConfigId) &&
                        server.sourceTrafficConfigId == null
                    else -> record.subscriptionId == null ||
                        (record.subscriptionId in knownSubscriptionIds &&
                            subscriptionEnabled(requireNotNull(record.subscriptionId)))
                }
            }.map(ProxyGroupServer::id),
        )
    } else null

    val searchable = records.associate { it.id to it.server.searchableProxyGroupText() }
    return ProxyGroupCatalog(listOfNotNull(allGroup) + baseGroups, searchable)
}

private fun StrategyGroup.isVisibleInProxyGroup(activeTrafficConfigId: Int?): Boolean {
    if (!showInAutoBalancerList) return false
    if (sourceTrafficConfigId == null) return true
    return when (displayMode) {
        StrategyGroupDisplayMode.ALWAYS -> true
        StrategyGroupDisplayMode.NEVER -> false
        StrategyGroupDisplayMode.ACTIVE_CONFIG -> sourceTrafficConfigId == activeTrafficConfigId
        else -> sourceTrafficConfigId == activeTrafficConfigId
    }
}

private fun ProxyServer<*>?.searchableProxyGroupText(): String {
    val info = this?.getInfo() ?: return ""
    return listOf(info.remarks, info.address, info.protocol).joinToString("\n")
}
