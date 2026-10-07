// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.server

import app.skipi.app.proxy.createProxyServerRecord
import app.skipi.app.proxy.editProxyServerRecord
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationActionOutcome
import app.skipi.app.store.SharedApplicationStore
import features.proxy.server.model.ProxyServer

/** Result returned by a proxy editor to a catalog-owning host. A null ID denotes a new draft. */
data class ProxyServerEditResult(
    val serverId: Int?,
    val server: ProxyServer<*>,
    val groupId: Int? = null,
    val returnGroupId: Int? = null,
    val deleted: Boolean = false,
)

sealed interface ProxyServerEditApplyOutcome {
    data class Saved(
        val serverId: Int,
        val wasExistingAtCommit: Boolean,
        val selectedGroupId: Int?,
    ) : ProxyServerEditApplyOutcome

    data object Deleted : ProxyServerEditApplyOutcome
    data class Failed(val reason: String) : ProxyServerEditApplyOutcome
}

/**
 * Applies an editor result against the latest persisted catalog. New drafts allocate their ID
 * inside the repository update, so imports and refreshes that finish while the editor is open
 * cannot claim the same ID.
 */
suspend fun applyProxyServerEditResult(
    result: ProxyServerEditResult,
    store: SharedApplicationStore,
    defaultGroupId: Int,
): ProxyServerEditApplyOutcome {
    if (result.deleted && result.serverId == null) {
        return ProxyServerEditApplyOutcome.Failed("A new proxy server draft cannot be deleted from the catalog")
    }

    if (result.deleted) {
        val actionResult = store.dispatchAndAwaitInStoreScope(
            SharedApplicationAction.RemoveProxyServer(requireNotNull(result.serverId)),
        )
        return when (val outcome = actionResult.outcome) {
            SharedApplicationActionOutcome.Completed -> ProxyServerEditApplyOutcome.Deleted
            is SharedApplicationActionOutcome.Rejected -> ProxyServerEditApplyOutcome.Failed(outcome.reason)
            is SharedApplicationActionOutcome.Failed -> ProxyServerEditApplyOutcome.Failed(outcome.reason)
        }
    }

    var committedServerId: Int? = null
    var wasExistingAtCommit = false
    var selectedGroupId: Int? = null
    val actionResult = store.dispatchAndAwaitInStoreScope(
        SharedApplicationAction.UpdateProxyCatalog { catalog ->
            val requestedId = result.serverId
            if (requestedId == null) {
                val sourceSubscriptionId = result.groupId.takeUnless { it == defaultGroupId }
                val nextCatalog = createProxyServerRecord(
                    catalog = catalog,
                    server = result.server,
                    sourceSubscriptionId = sourceSubscriptionId,
                )
                committedServerId = nextCatalog.servers.first().id
                selectedGroupId = result.groupId?.let { result.returnGroupId ?: it }
                nextCatalog
            } else {
                val currentRecord = catalog.servers.firstOrNull { it.id == requestedId }
                wasExistingAtCommit = currentRecord != null
                val sourceSubscriptionId = result.groupId.takeUnless { it == defaultGroupId }
                selectedGroupId = if (currentRecord != null) {
                    result.returnGroupId ?: currentRecord.sourceSubscriptionId ?: defaultGroupId
                } else {
                    result.groupId?.let { result.returnGroupId ?: it }
                }
                committedServerId = requestedId
                editProxyServerRecord(
                    catalog = catalog,
                    serverId = requestedId,
                    server = result.server,
                    sourceSubscriptionId = sourceSubscriptionId,
                )
            }
        },
    )

    return when (val outcome = actionResult.outcome) {
        SharedApplicationActionOutcome.Completed -> ProxyServerEditApplyOutcome.Saved(
            serverId = requireNotNull(committedServerId),
            wasExistingAtCommit = wasExistingAtCommit,
            selectedGroupId = selectedGroupId,
        )
        is SharedApplicationActionOutcome.Rejected -> ProxyServerEditApplyOutcome.Failed(outcome.reason)
        is SharedApplicationActionOutcome.Failed -> ProxyServerEditApplyOutcome.Failed(outcome.reason)
    }
}
