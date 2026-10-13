// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import features.subscription.SubscriptionMetadata

/** Prepared subscription row and full catalogue to persist before its first refresh. */
data class SubscriptionInstallPreparation(
    val catalog: SubscriptionCatalog,
    val subscription: SubscriptionRecord,
    val existingTitle: String?,
)

/** Pure install preparation; hosts keep URI policy, persistence, fetching, and UI in their adapters. */
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
}
