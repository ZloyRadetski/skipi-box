// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.repository.SubscriptionRepository
import features.subscription.SubscriptionMetadata

/** Prepared subscription row and full catalogue to persist before its first refresh. */
data class SubscriptionInstallPreparation(
    val catalog: SubscriptionCatalog,
    val subscription: SubscriptionRecord,
    val existingTitle: String?,
)

/** The saved install decision and the result from its one first refresh. */
data class SubscriptionInstallAndRefreshResult<RefreshResult>(
    val preparation: SubscriptionInstallPreparation,
    val refreshResult: RefreshResult,
)

/** Shared install preparation and persist-before-refresh coordination; hosts own URI and refresh policy. */
object SubscriptionInstallOperations {
    /** Reuses the first exact-URL row unchanged, or appends an Android-compatible new row. */
    fun prepare(
        catalog: SubscriptionCatalog,
        title: String,
        url: String,
        userAgent: String,
    ): SubscriptionInstallPreparation {
        val existing = catalog.subscriptions.firstOrNull { subscription -> subscription.url == url }
        if (existing != null) {
            return SubscriptionInstallPreparation(
                catalog = catalog,
                subscription = existing,
                existingTitle = existing.title,
            )
        }

        val subscription = SubscriptionRecord(
            id = SubscriptionCatalogOperations.allocateNextId(catalog),
            title = title,
            url = url,
            userAgent = userAgent,
            updateInterval = "",
            hwid = "",
            ageSecretKey = "",
            updateViaProxy = false,
            autoOverrideRules = true,
            enabled = true,
            builtIn = false,
            metadata = SubscriptionMetadata(),
            lastUpdatedAtMillis = null,
            notifyOnExpiry = true,
            customExpiryReminders = null,
        )
        val nextCatalog = SubscriptionCatalogOperations.upsert(catalog, subscription)
        return SubscriptionInstallPreparation(
            catalog = nextCatalog,
            subscription = subscription,
            existingTitle = null,
        )
    }

    /**
     * Persists the install decision, then runs exactly one host-owned refresh on its saved row.
     * A refresh result or exception never rolls back the already-persisted subscription.
     */
    suspend fun <RefreshResult> installAndRefresh(
        repository: SubscriptionRepository,
        title: String,
        url: String,
        userAgent: String,
        refresh: suspend (SubscriptionRecord) -> RefreshResult,
    ): SubscriptionInstallAndRefreshResult<RefreshResult> {
        var preparation: SubscriptionInstallPreparation? = null
        repository.updateCatalog { catalog ->
            prepare(
                catalog = catalog,
                title = title,
                url = url,
                userAgent = userAgent,
            ).also { prepared -> preparation = prepared }.catalog
        }
        val savedPreparation = checkNotNull(preparation) {
            "Subscription repository did not prepare the installed row"
        }
        return SubscriptionInstallAndRefreshResult(
            preparation = savedPreparation,
            refreshResult = refresh(savedPreparation.subscription),
        )
    }
}
