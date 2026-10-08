// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.deleteProxyServerRecords
import app.skipi.app.proxy.moveSubscriptionGroup
import features.proxy.server.model.Custom

/** Result of an editor or removal operation over the two catalogues it may affect. */
data class SubscriptionCatalogOperationResult(
    val catalog: SubscriptionCatalog,
    val proxyCatalog: ProxyServerCatalog,
    val savedSubscription: SubscriptionRecord? = null,
    val shouldStopProxy: Boolean = false,
)

/** Pure subscription edits. Hosts persist each returned catalogue through their own adapter. */
object SubscriptionCatalogOperations {
    /** Selects a positive ID from the latest catalogue high-water mark and occupied IDs. */
    fun allocateNextId(catalog: SubscriptionCatalog): Int {
        require(catalog.nextSubscriptionId > 0) { "Next subscription ID must be positive" }
        require(catalog.subscriptions.all { it.id > 0 }) { "Subscription IDs must be positive" }

        val occupiedIds = catalog.subscriptions.mapTo(HashSet(catalog.subscriptions.size)) { it.id }
        val maxId = occupiedIds.maxOrNull() ?: 0
        val afterMaxId = positiveIncrementSaturated(maxId)
        val candidate = maxOf(catalog.nextSubscriptionId, afterMaxId)
        check(candidate < Int.MAX_VALUE && candidate !in occupiedIds) { "No subscription IDs remain" }
        return candidate
    }

    /** Replaces an existing row in place or appends a raw refresh/install snapshot. */
    fun upsert(
        catalog: SubscriptionCatalog,
        subscription: SubscriptionRecord,
    ): SubscriptionCatalog {
        require(subscription.id > 0) { "Subscription IDs must be positive" }
        val existingIndex = catalog.subscriptions.indexOfFirst { it.id == subscription.id }
        val subscriptions = if (existingIndex >= 0) {
            catalog.subscriptions.toMutableList().also { it[existingIndex] = subscription }
        } else {
            catalog.subscriptions + subscription
        }
        return catalog.copy(
            subscriptions = subscriptions,
            nextSubscriptionId = maxOf(catalog.nextSubscriptionId, positiveIncrementSaturated(subscription.id)),
        )
    }

    /** Creates from an editor draft using the latest catalogue ID, never its displayed draft ID. */
    fun createEditor(
        catalog: SubscriptionCatalog,
        proxyCatalog: ProxyServerCatalog,
        draft: SubscriptionRecord,
    ): SubscriptionCatalogOperationResult {
        val id = allocateNextId(catalog)
        val created = draft.copy(
            id = id,
            builtIn = false,
            metadata = null,
            lastUpdatedAtMillis = null,
        )
        val updated = upsert(catalog, created).copy(
            nextSubscriptionId = maxOf(catalog.nextSubscriptionId, positiveIncrementSaturated(id)),
        )
        return SubscriptionCatalogOperationResult(
            catalog = updated,
            proxyCatalog = proxyCatalog,
            savedSubscription = created,
        )
    }

    /** Saves editor-owned fields onto the latest row; fetched metadata and enabled state stay current. */
    fun saveEditor(
        catalog: SubscriptionCatalog,
        proxyCatalog: ProxyServerCatalog,
        subscription: SubscriptionRecord,
    ): SubscriptionCatalogOperationResult {
        val current = catalog.subscriptions.firstOrNull { it.id == subscription.id }
            ?: return SubscriptionCatalogOperationResult(catalog, proxyCatalog)

        val saved = subscription.copy(
            id = current.id,
            title = if (current.builtIn) current.title else subscription.title,
            enabled = current.enabled,
            builtIn = current.builtIn,
            metadata = current.metadata,
            lastUpdatedAtMillis = current.lastUpdatedAtMillis,
        )
        val nextCatalog = upsert(catalog, saved)
        val nextProxyCatalog = updateLinkedCustomOverrides(proxyCatalog, saved)
        return SubscriptionCatalogOperationResult(
            catalog = nextCatalog,
            proxyCatalog = nextProxyCatalog,
            savedSubscription = saved,
        )
    }

    /** Toggles only editable subscriptions; a host's built-in group is immutable here. */
    fun setEnabled(
        catalog: SubscriptionCatalog,
        subscriptionId: Int,
        enabled: Boolean,
    ): SubscriptionCatalog {
        val current = catalog.subscriptions.firstOrNull { it.id == subscriptionId } ?: return catalog
        if (current.builtIn || current.enabled == enabled) return catalog
        return catalog.copy(subscriptions = catalog.subscriptions.map { row ->
            if (row.id == subscriptionId) row.copy(enabled = enabled) else row
        })
    }

    /** Reorders editable rows while preserving built-in slots and an optional host-fixed row. */
    fun move(
        catalog: SubscriptionCatalog,
        groupId: Int,
        offset: Int,
        fixedGroupId: Int? = null,
    ): SubscriptionCatalog = catalog.copy(
        subscriptions = moveSubscriptionGroup(
            groups = catalog.subscriptions,
            groupId = groupId,
            offset = offset,
            idOf = SubscriptionRecord::id,
            isBuiltIn = SubscriptionRecord::builtIn,
            fixedGroupId = fixedGroupId,
        ),
    )

    /** Removes an editable subscription and all linked server/composite references. */
    fun remove(
        catalog: SubscriptionCatalog,
        proxyCatalog: ProxyServerCatalog,
        subscriptionId: Int,
    ): SubscriptionCatalogOperationResult {
        val current = catalog.subscriptions.firstOrNull { it.id == subscriptionId }
            ?: return SubscriptionCatalogOperationResult(catalog, proxyCatalog)
        require(!current.builtIn) { "Built-in subscriptions cannot be removed" }

        val deletedServerIds = proxyCatalog.servers.asSequence()
            .filter { it.sourceSubscriptionId == subscriptionId }
            .map(ProxyServerRecord::id)
            .toSet()
        val nextProxyCatalog = deleteProxyServerRecords(proxyCatalog, deletedServerIds)
        return SubscriptionCatalogOperationResult(
            catalog = catalog.copy(subscriptions = catalog.subscriptions.filterNot { it.id == subscriptionId }),
            proxyCatalog = nextProxyCatalog,
            shouldStopProxy = proxyCatalog.selectedServerId in deletedServerIds,
        )
    }

    internal fun updateLinkedCustomOverrides(
        proxyCatalog: ProxyServerCatalog,
        subscription: SubscriptionRecord,
    ): ProxyServerCatalog {
        val updatedServers = proxyCatalog.servers.map { row ->
            val custom = row.server as? Custom
            if (row.sourceSubscriptionId == subscription.id && custom != null &&
                custom.overrideInboundAndDns != subscription.autoOverrideRules
            ) {
                row.copy(server = custom.copy(overrideInboundAndDns = subscription.autoOverrideRules))
            } else {
                row
            }
        }
        return if (updatedServers == proxyCatalog.servers) proxyCatalog
        else proxyCatalog.copy(servers = updatedServers)
    }
}

private fun positiveIncrementSaturated(value: Int): Int = when {
    value >= Int.MAX_VALUE -> Int.MAX_VALUE
    value < 0 -> 1
    else -> value + 1
}
