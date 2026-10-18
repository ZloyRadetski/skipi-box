// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.ConfigProfile
import features.config.ConfigProfileLibraries
import features.config.ConfigProfileLibrary
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig

/** Shared application operations for platforms that persist the portable config-library model. */
object TrafficConfigLibraryOperations {
    fun normalize(library: ConfigProfileLibrary): ConfigProfileLibrary =
        ConfigProfileLibraries.normalize(library)

    fun put(
        library: ConfigProfileLibrary,
        name: String,
        content: String,
        sourceUrl: String? = null,
        updateLocked: Boolean? = null,
        lastUpdatedAtMillis: Long? = null,
    ): ConfigProfileLibrary = ConfigProfileLibraries.put(
        library = library,
        name = name,
        content = content,
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    )

    /** Upserts a repository record with its caller-provided ID, appending it as before Desktop did. */
    fun upsert(library: ConfigProfileLibrary, profile: ConfigProfile): ConfigProfileLibrary {
        require(profile.content.isNotBlank()) { "Config cannot be empty" }
        val profiles = library.configs.filterNot { it.id == profile.id } + profile
        val selectedId = library.selectedConfigId
            ?.takeIf { id -> profiles.any { it.id == id } }
            ?: profile.id
        return library.copy(
            selectedConfigId = selectedId,
            configs = profiles,
            nextConfigId = maxOf(library.nextConfigId, profile.id + 1),
        )
    }

    /** Creates one profile with the same typed document writer used by Android. */
    fun create(
        library: ConfigProfileLibrary,
        profile: ConfigProfile,
        resourceUserAgentFallback: String = "",
    ): ConfigProfileLibrary {
        val normalized = ConfigProfileLibraries.normalize(library)
        val parsed = profile.toTrafficConfigState(resourceUserAgentFallback)
        val created = createTrafficConfigProfile(
            profiles = normalized.configs.map { it.toTrafficConfigState(resourceUserAgentFallback) },
            nextId = normalized.nextConfigId,
            name = parsed.name,
            rawDocument = profile.content,
            resourceSettings = parsed.resourceSettings,
            sourceUrl = parsed.sourceUrl,
            lastUpdatedAtMillis = profile.lastUpdatedAtMillis,
            readSkipiSettingsFromRawDocument = true,
        ).profiles.last()
        return upsert(normalized, created.toConfigProfile())
    }

    /** Imports a raw document using the same matching and parsing rules as Android. */
    fun importDocument(
        library: ConfigProfileLibrary,
        content: String,
        activate: Boolean,
        fallbackName: String = "Config",
        sourceUrl: String = "",
        resourceUserAgentFallback: String = "",
    ): ConfigProfileLibrary {
        val normalized = ConfigProfileLibraries.normalize(library).let { normalized ->
            if (library.selectedConfigId == null) normalized.copy(selectedConfigId = null) else normalized
        }
        val result = importTrafficConfigDocument(
            trafficConfigs = normalized.configs.map { it.toTrafficConfigState(resourceUserAgentFallback) },
            nextTrafficConfigId = normalized.nextConfigId,
            activeTrafficConfigId = normalized.selectedConfigId ?: 0,
            content = content,
            activate = activate,
            fallbackName = fallbackName,
            sourceUrl = sourceUrl,
            newProfileResourceSettings = TrafficConfigResourceSettings(userAgent = resourceUserAgentFallback),
        )
        return normalized.copy(
            selectedConfigId = result.activeTrafficConfigId.takeIf { id -> result.trafficConfigs.any { it.id == id } },
            configs = result.trafficConfigs.map(TrafficConfigState::toConfigProfile),
            nextConfigId = result.nextTrafficConfigId,
        )
    }

    /** Duplicates an existing profile with Android's typed settings and document writer. */
    fun duplicate(
        library: ConfigProfileLibrary,
        sourceProfileId: Int,
        name: String,
        resourceUserAgentFallback: String = "",
    ): ConfigProfileLibrary {
        val source = library.configs.firstOrNull { it.id == sourceProfileId }
            ?: throw IllegalArgumentException("Unknown config ID: $sourceProfileId")
        return duplicate(library, source, name, resourceUserAgentFallback)
    }

    /** Duplicates the profile snapshot supplied by the caller, as Android's menu callback does. */
    fun duplicate(
        library: ConfigProfileLibrary,
        sourceProfile: ConfigProfile,
        name: String,
        resourceUserAgentFallback: String = "",
    ): ConfigProfileLibrary {
        val normalized = ConfigProfileLibraries.normalize(library)
        val profiles = normalized.configs.map { it.toTrafficConfigState(resourceUserAgentFallback) }
        val source = sourceProfile.toTrafficConfigState(resourceUserAgentFallback)
        val duplicated = duplicateTrafficConfigProfile(
            profiles = profiles,
            nextId = normalized.nextConfigId,
            source = source,
            name = name,
        ).profiles.last()
        return upsert(normalized, duplicated.toConfigProfile())
    }

    fun update(
        library: ConfigProfileLibrary,
        id: Int,
        name: String,
        content: String,
        sourceUrl: String,
        updateLocked: Boolean,
        lastUpdatedAtMillis: Long,
    ): ConfigProfileLibrary = ConfigProfileLibraries.update(
        library, id, name, content, sourceUrl, updateLocked, lastUpdatedAtMillis,
    )

    fun select(library: ConfigProfileLibrary, id: Int?): ConfigProfileLibrary {
        if (id == null) return library.copy(selectedConfigId = null)
        return ConfigProfileLibraries.select(library, id)
    }

    fun remove(library: ConfigProfileLibrary, id: Int): ConfigProfileLibrary =
        ConfigProfileLibraries.remove(library, id)
}

/** Materializes a stored profile with host-provided typed defaults for absent SKIPI fields. */
fun ConfigProfile.toTrafficConfigState(resourceUserAgentFallback: String = ""): TrafficConfigState {
    val parsed = TrafficConfigState(
        id = id,
        name = name,
        rawConfig = content,
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        resourceSettings = TrafficConfigResourceSettings(userAgent = resourceUserAgentFallback),
    ).withSkipiSettingsReadFromRawConfig()
    // Desktop stores these identity/update fields outside the document. Preserve that current
    // envelope when old serialized [SKIPI] lines disagree; Android duplicates its typed state.
    return parsed.copy(
        id = id,
        name = name,
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    )
}

private fun TrafficConfigState.toConfigProfile(): ConfigProfile = ConfigProfile(
    id = id,
    name = name,
    content = rawConfig,
    sourceUrl = sourceUrl,
    updateLocked = updateLocked,
    lastUpdatedAtMillis = lastUpdatedAtMillis,
)
