// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.repository.ProxyServerRepository
import app.skipi.app.repository.SubscriptionRepository

/** Result of saving a subscription editor draft. */
data class SubscriptionEditorSaveResult(
    val savedSubscription: SubscriptionRecord?,
    val shouldStartRefresh: Boolean,
)

/**
 * Coordinates subscription editor actions through the shared catalogue rules.
 * Each host repository persists its own catalogue; cross-catalogue updates are
 * sequential and do not claim a global storage transaction.
 */
class SubscriptionEditorController(
    private val subscriptions: SubscriptionRepository,
    private val proxyServers: ProxyServerRepository,
) {
    /** Saves a new group from the latest counter or edits an existing latest row. */
    suspend fun save(
        draft: SubscriptionRecord,
        isNew: Boolean,
    ): SubscriptionEditorSaveResult {
        var savedSubscription: SubscriptionRecord? = null
        subscriptions.updateCatalog { current ->
            val result = if (isNew) {
                SubscriptionCatalogOperations.createEditor(
                    catalog = current,
                    proxyCatalog = ProxyServerCatalog(),
                    draft = draft,
                )
            } else {
                SubscriptionCatalogOperations.saveEditor(
                    catalog = current,
                    proxyCatalog = ProxyServerCatalog(),
                    subscription = draft,
                )
            }
            savedSubscription = result.savedSubscription
            result.catalog
        }

        val saved = savedSubscription
        if (saved != null) {
            proxyServers.updateCatalog { latest ->
                SubscriptionCatalogOperations.updateLinkedCustomOverrides(latest, saved)
            }
        }

        return SubscriptionEditorSaveResult(
            savedSubscription = saved,
            shouldStartRefresh = isNew && saved != null && saved.enabled && saved.url.isNotBlank(),
        )
    }

    /** Changes enabled state while preserving built-in restrictions. */
    suspend fun setEnabled(subscriptionId: Int, enabled: Boolean) {
        subscriptions.updateCatalog { current ->
            SubscriptionCatalogOperations.setEnabled(
                catalog = current,
                subscriptionId = subscriptionId,
                enabled = enabled,
            )
        }
    }

    /** Reorders ordinary groups while retaining built-in and host-fixed slots. */
    suspend fun move(groupId: Int, offset: Int, fixedGroupId: Int? = null) {
        subscriptions.updateCatalog { current ->
            SubscriptionCatalogOperations.move(
                catalog = current,
                groupId = groupId,
                offset = offset,
                fixedGroupId = fixedGroupId,
            )
        }
    }

    /** Delegates host-specific linked-row cleanup and persistence to the repository. */
    suspend fun remove(subscriptionId: Int) {
        subscriptions.remove(subscriptionId)
    }
}
