// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig

/** Result of a collection operation that allocates a profile ID. */
data class TrafficConfigCollectionUpdate(
    val profiles: List<TrafficConfigState>,
    val nextId: Int,
    val affectedProfileId: Int,
)

/** Creates a profile with caller-provided platform defaults and a localized name. */
fun createTrafficConfigProfile(
    profiles: List<TrafficConfigState>,
    nextId: Int,
    name: String,
    rawDocument: String,
    resourceSettings: TrafficConfigResourceSettings,
    sourceUrl: String = "",
    lastUpdatedAtMillis: Long = 0L,
    readSkipiSettingsFromRawDocument: Boolean = false,
): TrafficConfigCollectionUpdate {
    val initialProfile = TrafficConfigState(
        id = nextId,
        name = name,
        rawConfig = rawDocument,
        sourceUrl = sourceUrl,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        resourceSettings = resourceSettings,
    )
    val parsedProfile = if (readSkipiSettingsFromRawDocument) {
        initialProfile.withSkipiSettingsReadFromRawConfig()
    } else {
        initialProfile
    }
    val profile = parsedProfile.copy(
        sourceUrl = sourceUrl.ifBlank { parsedProfile.sourceUrl },
    ).withSkipiSettingsInRawConfig()
    return TrafficConfigCollectionUpdate(
        profiles = profiles + profile,
        nextId = nextId + 1,
        affectedProfileId = profile.id,
    )
}

/** Duplicates a profile, preserving its settings while assigning a fresh ID and name. */
fun duplicateTrafficConfigProfile(
    profiles: List<TrafficConfigState>,
    nextId: Int,
    source: TrafficConfigState,
    name: String,
): TrafficConfigCollectionUpdate {
    val duplicate = source.copy(
        id = nextId,
        name = name,
        lastUpdatedAtMillis = 0L,
    ).withSkipiSettingsInRawConfig()
    return TrafficConfigCollectionUpdate(
        profiles = profiles + duplicate,
        nextId = nextId + 1,
        affectedProfileId = duplicate.id,
    )
}

/**
 * Deletes a profile only when another remains. If the deleted profile was active,
 * selects the first remaining profile; unrelated active IDs are preserved.
 */
fun deleteTrafficConfigProfile(
    profiles: List<TrafficConfigState>,
    activeProfileId: Int,
    profileId: Int,
): TrafficConfigDeletionUpdate {
    if (profiles.size <= 1) return TrafficConfigDeletionUpdate(profiles, activeProfileId)
    val remaining = profiles.filterNot { it.id == profileId }
    if (remaining.size == profiles.size) return TrafficConfigDeletionUpdate(profiles, activeProfileId)
    val activeId = activeProfileId.takeIf { it != profileId } ?: remaining.first().id
    return TrafficConfigDeletionUpdate(remaining, activeId)
}

data class TrafficConfigDeletionUpdate(
    val profiles: List<TrafficConfigState>,
    val activeProfileId: Int,
)

/** Applies a portable profile edit and keeps its SKIPI settings synchronized with the document. */
fun updateTrafficConfigProfile(
    profiles: List<TrafficConfigState>,
    profileId: Int,
    transform: (TrafficConfigState) -> TrafficConfigState,
): List<TrafficConfigState> {
    var changed = false
    val updatedProfiles = profiles.map { profile ->
        if (profile.id != profileId) {
            profile
        } else {
            val transformed = transform(profile)
            if (transformed == profile) {
                profile
            } else {
                changed = true
                val withDocumentSettings = if (transformed.rawConfig != profile.rawConfig) {
                    transformed.withSkipiSettingsReadFromRawConfig()
                } else {
                    transformed
                }
                withDocumentSettings.withSkipiSettingsInRawConfig()
            }
        }
    }
    return if (changed) updatedProfiles else profiles
}

/** Selects an existing profile while preserving the current ID for an unknown request. */
fun selectTrafficConfigProfile(
    profiles: List<TrafficConfigState>,
    activeProfileId: Int,
    requestedProfileId: Int,
): Int = requestedProfileId.takeIf { id -> profiles.any { it.id == id } } ?: activeProfileId
