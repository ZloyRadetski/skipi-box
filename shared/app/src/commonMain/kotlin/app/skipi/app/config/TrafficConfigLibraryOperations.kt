// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.ConfigProfile
import features.config.ConfigProfileLibraries
import features.config.ConfigProfileLibrary

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
        return library.copy(selectedConfigId = selectedId, configs = profiles)
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
