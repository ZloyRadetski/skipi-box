// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.subscription.SubscriptionInstallOperations
import app.skipi.app.subscription.SubscriptionInstallPreparation

/** Binds Desktop's already parsed URI metadata to the shared, side-effect-free install preparation. */
internal fun DesktopSubscriptionInstallUri.prepareIn(
    catalog: SubscriptionCatalog,
): SubscriptionInstallPreparation = SubscriptionInstallOperations.prepare(
    catalog = catalog,
    title = name,
    url = url,
    userAgent = userAgent,
)

/** Applies the common install decision to the latest repository catalog and persists its result. */
internal suspend fun DesktopSubscriptionInstallUri.prepareAndPersistIn(
    repository: DesktopSubscriptionRepository,
): SubscriptionRecord {
    var installedSubscription: SubscriptionRecord? = null
    repository.updateCatalog { latest ->
        val preparation = prepareIn(latest)
        installedSubscription = preparation.subscription
        preparation.catalog
    }
    return checkNotNull(installedSubscription) { "Desktop subscription install did not produce a record" }
}
