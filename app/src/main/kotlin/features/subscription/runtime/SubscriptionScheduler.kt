// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import app.SubscriptionGroupState

/** Android adapts persisted state; shared code owns scheduling decisions. */
internal fun SubscriptionScheduler.reconcile(groups: List<SubscriptionGroupState>) {
    reconcile(
        groups.map { group ->
            SubscriptionRefreshTarget(
                id = group.id,
                url = group.url,
                enabled = group.enabled,
                updateInterval = group.updateInterval,
                lastUpdatedAtMillis = group.lastUpdatedAtMillis,
            )
        },
    )
}
