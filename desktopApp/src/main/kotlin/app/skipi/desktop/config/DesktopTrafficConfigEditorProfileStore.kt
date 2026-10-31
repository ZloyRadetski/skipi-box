// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigEditorProfileStore
import app.skipi.app.config.TrafficConfigLibraryOperations
import app.skipi.app.config.updateTrafficConfigProfile
import app.skipi.app.config.toTrafficConfigState
import features.config.TrafficConfigState

/** Maps the common editor's latest-row updates onto Desktop's persisted config library. */
internal class DesktopTrafficConfigEditorProfileStore(
    private val resourceUserAgentFallback: String,
    private val onTransformConfigLibrary: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibrary,
) : TrafficConfigEditorProfileStore {
    constructor(
        repository: DesktopTrafficConfigRepository,
        resourceUserAgentFallback: String,
    ) : this(resourceUserAgentFallback, repository::transformLatest)

    override fun update(
        profileId: Int,
        transform: (TrafficConfigState) -> TrafficConfigState,
    ): Boolean {
        var found = false
        onTransformConfigLibrary { latest ->
            val current = latest.configs.firstOrNull { it.id == profileId }
                ?: return@onTransformConfigLibrary latest
            found = true
            val updatedProfiles = updateTrafficConfigProfile(
                profiles = listOf(current.toTrafficConfigState(resourceUserAgentFallback)),
                profileId = profileId,
                transform = transform,
            )
            val updated = updatedProfiles.firstOrNull { it.id == profileId } ?: return@onTransformConfigLibrary latest
            TrafficConfigLibraryOperations.update(
                library = latest,
                id = profileId,
                name = updated.name,
                content = updated.rawConfig,
                sourceUrl = updated.sourceUrl,
                updateLocked = updated.updateLocked,
                lastUpdatedAtMillis = current.lastUpdatedAtMillis,
            )
        }
        return found
    }
}
