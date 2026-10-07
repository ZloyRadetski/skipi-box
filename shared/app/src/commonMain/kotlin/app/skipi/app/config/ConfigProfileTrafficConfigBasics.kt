// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.ConfigProfile
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig

/**
 * Applies Desktop profile-basics edits through the shared typed Android profile model.
 * The optional seed supplies only typed fallbacks for settings missing from [content].
 */
fun ConfigProfile.withTrafficConfigBasics(
    name: String,
    sourceUrl: String,
    updateLocked: Boolean,
    seed: TrafficConfigState? = null,
): ConfigProfile {
    val materialized = (seed ?: TrafficConfigState(
        id = id,
        name = this.name,
        rawConfig = content,
        sourceUrl = this.sourceUrl,
        updateLocked = this.updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    )).copy(
        id = id,
        name = this.name,
        rawConfig = content,
        sourceUrl = this.sourceUrl,
        updateLocked = this.updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    ).withSkipiSettingsReadFromRawConfig()

    val updated = materialized.withProfileBasics(
        name = name,
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        autoUpdate = materialized.autoUpdate,
        updateInterval = materialized.updateInterval,
        resourceAutoUpdate = materialized.resourceSettings.autoUpdate,
        resourceUpdateInterval = materialized.resourceSettings.updateInterval,
    )

    return copy(
        name = updated.name,
        content = updated.rawConfig,
        sourceUrl = updated.sourceUrl,
        updateLocked = updated.updateLocked,
    )
}
