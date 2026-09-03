// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode

/**
 * Stable keys for the group selector shared by Desktop's home and any future
 * desktop proxy-list screen.  They intentionally do not reuse a subscription
 * URL: a URL can be edited while a numeric subscription record ID is stable.
 */
object DesktopProxyGroupIds {
    const val All = "all"
    const val Manual = "manual"
    const val AutoBalancers = "auto-balancers"

    fun subscription(subscriptionId: Int): String {
        require(subscriptionId > 0) { "Subscription ID must be positive" }
        return "subscription:$subscriptionId"
    }
}

enum class DesktopProxyGroupKind {
    All,
    Manual,
    Subscription,
    AutoBalancer,
}

/** A UI-independent tab/group description. [serverIds] retain library order. */
data class DesktopProxyGroup(
    val id: String,
    val kind: DesktopProxyGroupKind,
    val title: String,
    val serverIds: List<Int>,
    val enabled: Boolean = true,
) {
    val serverCount: Int get() = serverIds.size
}

/**
 * Display labels stay outside Compose so the same grouping can be consumed by
 * a future desktop settings/localisation layer without rebuilding its logic.
 */
data class DesktopProxyGroupLabels(
    val allProxies: String = "Все прокси",
    val manualServers: String = "Ручные серверы",
    val autoBalancers: String = "Автобалансировщики",
)

/**
 * An optional override is useful for previews and transient UI state.  Without
 * it, the persisted Android-compatible [DesktopStoredSubscription.enabled]
 * flag is the source of truth.
 */
data class DesktopProxyGroupOptions(
    val subscriptionEnabledById: Map<Int, Boolean> = emptyMap(),
    val activeTrafficConfigId: Int? = null,
    val enableAllProxyGroup: Boolean = true,
    val labels: DesktopProxyGroupLabels = DesktopProxyGroupLabels(),
)

data class DesktopProxyGroupSelection(
    val group: DesktopProxyGroup?,
    val serverIds: List<Int>,
)

/**
 * Immutable result of grouping the current desktop libraries.  It owns the
 * non-UI selection and search policy so every desktop presentation uses the
 * same Android-like group semantics.
 */
class DesktopProxyGroupCatalog internal constructor(
    val groups: List<DesktopProxyGroup>,
    private val searchTextByServerId: Map<Int, String>,
) {
    val defaultGroupId: String?
        get() = groups.firstOrNull { group -> group.kind != DesktopProxyGroupKind.All }?.id

    fun group(groupId: String?): DesktopProxyGroup? = groups.firstOrNull { it.id == groupId }

    /**
     * Selects a valid requested group, falling back to Android's first real
     * group rather than silently selecting the synthetic "All" tab.
     */
    fun select(groupId: String? = null): DesktopProxyGroupSelection {
        val selected = group(groupId) ?: group(defaultGroupId)
        return DesktopProxyGroupSelection(
            group = selected,
            serverIds = selected?.serverIds.orEmpty(),
        )
    }

    /**
     * Filters only inside the selected group. Search covers the same display
     * fields available to Desktop's server cards: name, address and protocol.
     * Undecodable legacy records remain present for an empty query, but cannot
     * falsely match a non-empty query.
     */
    fun filter(groupId: String? = null, query: String = ""): DesktopProxyGroupSelection {
        val selection = select(groupId)
        val keyword = query.trim()
        if (keyword.isEmpty()) return selection
        return selection.copy(
            serverIds = selection.serverIds.filter { serverId ->
                searchTextByServerId[serverId]?.contains(keyword, ignoreCase = true) == true
            },
        )
    }
}

/**
 * Desktop equivalent of Android's `proxyServerListGroups`, derived solely
 * from the portable server and subscription libraries.
 *
 * Direct proxy records belong to either the manual or subscription group.
 * `StrategyGroup` records are surfaced as virtual auto-balancers, even when a
 * legacy desktop record carries a subscription ID. This preserves Android's
 * separate AutoBalancer group and prevents strategy records from being mixed
 * into a provider's ordinary server list.
 */
object DesktopProxyGroups {
    fun create(
        serverLibrary: DesktopServerLibrary,
        subscriptionLibrary: DesktopSubscriptionLibrary,
        options: DesktopProxyGroupOptions = DesktopProxyGroupOptions(),
    ): DesktopProxyGroupCatalog {
        val records = serverLibrary.servers
            .distinctBy(DesktopStoredProxyServer::id)
            .map { stored -> DesktopProxyGroupRecord(stored = stored, server = stored.decode().getOrNull()) }
        val subscriptionsById = subscriptionLibrary.subscriptions.associateBy(DesktopStoredSubscription::id)
        val knownSubscriptionIds = subscriptionsById.keys

        fun subscriptionEnabled(subscriptionId: Int): Boolean =
            options.subscriptionEnabledById[subscriptionId]
                ?: subscriptionsById[subscriptionId]?.enabled
                ?: true

        val visibleStrategyRecords = records.filter { record ->
            val strategy = record.server as? StrategyGroup
            strategy != null && strategy.isVisibleDesktopAutoBalancer(options.activeTrafficConfigId)
        }
        val directRecords = records.filter { record -> record.server !is StrategyGroup }
        val manualRecords = directRecords.filter { record -> record.stored.subscriptionId == null }
        val subscriptionRecordsById = directRecords
            .filter { record -> record.stored.subscriptionId in knownSubscriptionIds }
            .groupBy { record -> checkNotNull(record.stored.subscriptionId) }

        val baseGroups = buildList {
            if (visibleStrategyRecords.isNotEmpty()) {
                add(
                    DesktopProxyGroup(
                        id = DesktopProxyGroupIds.AutoBalancers,
                        kind = DesktopProxyGroupKind.AutoBalancer,
                        title = options.labels.autoBalancers,
                        serverIds = visibleStrategyRecords.map(DesktopProxyGroupRecord::id),
                    ),
                )
            }
            subscriptionLibrary.subscriptions.forEach { subscription ->
                if (subscriptionEnabled(subscription.id)) {
                    add(
                        DesktopProxyGroup(
                            id = DesktopProxyGroupIds.subscription(subscription.id),
                            kind = DesktopProxyGroupKind.Subscription,
                            title = subscription.name.trim().ifBlank { subscription.url },
                            serverIds = subscriptionRecordsById[subscription.id].orEmpty().map(DesktopProxyGroupRecord::id),
                            enabled = true,
                        ),
                    )
                }
            }
            if (manualRecords.isNotEmpty()) {
                add(
                    DesktopProxyGroup(
                        id = DesktopProxyGroupIds.Manual,
                        kind = DesktopProxyGroupKind.Manual,
                        title = options.labels.manualServers,
                        serverIds = manualRecords.map(DesktopProxyGroupRecord::id),
                    ),
                )
            }
        }

        // Android exposes the synthetic all-proxies tab only when there are
        // several real groups. Config-owned strategy groups remain reachable
        // in AutoBalancer, but must not leak into the global tab.
        val allGroup = if (options.enableAllProxyGroup && baseGroups.size > 1) {
            DesktopProxyGroup(
                id = DesktopProxyGroupIds.All,
                kind = DesktopProxyGroupKind.All,
                title = options.labels.allProxies,
                serverIds = records
                    .filter { record ->
                        when (val server = record.server) {
                            is StrategyGroup -> {
                                server.isVisibleDesktopAutoBalancer(options.activeTrafficConfigId) &&
                                    server.sourceTrafficConfigId == null
                            }

                            else -> {
                                val subscriptionId = record.stored.subscriptionId
                                subscriptionId == null ||
                                    (subscriptionId in knownSubscriptionIds && subscriptionEnabled(subscriptionId))
                            }
                        }
                    }
                    .map(DesktopProxyGroupRecord::id),
            )
        } else {
            null
        }

        val searchableText = records.associate { record ->
            record.id to record.server.searchableDesktopProxyGroupText()
        }
        return DesktopProxyGroupCatalog(
            groups = listOfNotNull(allGroup) + baseGroups,
            searchTextByServerId = searchableText,
        )
    }
}

private data class DesktopProxyGroupRecord(
    val stored: DesktopStoredProxyServer,
    val server: ProxyServer<*>?,
) {
    val id: Int get() = stored.id
}

private fun StrategyGroup.isVisibleDesktopAutoBalancer(activeTrafficConfigId: Int?): Boolean {
    if (!showInAutoBalancerList) return false
    if (sourceTrafficConfigId == null) return true
    return when (displayMode) {
        StrategyGroupDisplayMode.ALWAYS -> true
        StrategyGroupDisplayMode.NEVER -> false
        StrategyGroupDisplayMode.ACTIVE_CONFIG -> sourceTrafficConfigId == activeTrafficConfigId
        else -> sourceTrafficConfigId == activeTrafficConfigId
    }
}

private fun ProxyServer<*>?.searchableDesktopProxyGroupText(): String {
    val info = this?.getInfo() ?: return ""
    return listOf(info.remarks, info.address, info.protocol).joinToString("\n")
}
