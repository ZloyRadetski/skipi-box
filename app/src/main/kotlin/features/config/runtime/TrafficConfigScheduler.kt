// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import features.config.TrafficConfigState

/** Android adapts persisted config state; shared code owns eligibility rules. */
internal fun TrafficConfigScheduler.reconcileTrafficConfigStates(configs: List<TrafficConfigState>) {
    reconcile(
        configs.map { config ->
            TrafficConfigRefreshTarget(
                id = config.id,
                sourceUrl = config.sourceUrl,
                autoUpdate = config.autoUpdate,
                updateLocked = config.updateLocked,
                updateInterval = config.updateInterval,
                resourceAutoUpdate = config.resourceSettings.autoUpdate,
                resourceUpdateInterval = config.resourceSettings.updateInterval,
            )
        },
    )
}
