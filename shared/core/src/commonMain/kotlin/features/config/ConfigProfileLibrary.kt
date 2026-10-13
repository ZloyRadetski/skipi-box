// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlinx.serialization.Serializable

/** Platform-neutral representation persisted by each platform's storage adapter. */
@Serializable
data class ConfigProfile(
    val id: Int,
    val name: String,
    val content: String,
    val sourceUrl: String = "",
    val updateLocked: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
)

@Serializable
data class ConfigProfileLibrary(
    val selectedConfigId: Int? = null,
    val configs: List<ConfigProfile> = emptyList(),
    /** Next profile ID to allocate; omitted by legacy JSON and derived by [normalize]. */
    val nextConfigId: Int = 1,
)

/** Pure config-library transformations. Filesystem access belongs to platform adapters. */
object ConfigProfileLibraries {
    fun normalize(library: ConfigProfileLibrary): ConfigProfileLibrary {
        val selected = library.selectedConfigId?.takeIf { id -> library.configs.any { it.id == id } }
            ?: library.configs.firstOrNull()?.id
        val minimumNextId = (library.configs.maxOfOrNull { it.id } ?: 0) + 1
        return library.copy(
            selectedConfigId = selected,
            nextConfigId = maxOf(library.nextConfigId, minimumNextId),
        )
    }

    fun put(
        library: ConfigProfileLibrary,
        name: String,
        content: String,
        sourceUrl: String? = null,
        updateLocked: Boolean? = null,
        lastUpdatedAtMillis: Long? = null,
    ): ConfigProfileLibrary {
        require(content.isNotBlank()) { "Config cannot be empty" }
        val normalizedName = name.trim().ifBlank { "Config ${(library.configs.maxOfOrNull { it.id } ?: 0) + 1}" }
        val normalizedUrl = sourceUrl?.trim()?.takeIf(String::isNotBlank)
        val existing = normalizedUrl
            ?.let { url -> library.configs.firstOrNull { it.sourceUrl.equals(url, ignoreCase = true) } }
            ?: library.configs.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }
        val minimumNextId = (library.configs.maxOfOrNull { it.id } ?: 0) + 1
        val nextConfigId = maxOf(library.nextConfigId, minimumNextId)
        val id = existing?.id ?: nextConfigId
        val stored = ConfigProfile(
            id = id,
            name = normalizedName,
            content = content,
            sourceUrl = sourceUrl?.trim() ?: existing?.sourceUrl.orEmpty(),
            updateLocked = updateLocked ?: existing?.updateLocked ?: false,
            lastUpdatedAtMillis = lastUpdatedAtMillis ?: existing?.lastUpdatedAtMillis ?: 0L,
        )
        return library.copy(
            selectedConfigId = library.selectedConfigId?.takeIf { selectedId ->
                library.configs.any { config -> config.id == selectedId }
            } ?: id,
            configs = library.configs.filterNot { it.id == id } + stored,
            nextConfigId = if (existing == null) id + 1 else nextConfigId,
        )
    }

    fun select(library: ConfigProfileLibrary, id: Int): ConfigProfileLibrary {
        if (library.configs.none { it.id == id }) return library
        return library.copy(selectedConfigId = id)
    }

    fun update(
        library: ConfigProfileLibrary,
        id: Int,
        name: String,
        content: String,
        sourceUrl: String,
        updateLocked: Boolean,
        lastUpdatedAtMillis: Long,
    ): ConfigProfileLibrary {
        require(content.isNotBlank()) { "Config cannot be empty" }
        require(library.configs.any { it.id == id }) { "Unknown config ID: $id" }
        val updated = ConfigProfile(
            id = id,
            name = name.trim().ifBlank { "Config $id" },
            content = content,
            sourceUrl = sourceUrl.trim(),
            updateLocked = updateLocked,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
        )
        return library.copy(configs = library.configs.map { stored -> if (stored.id == id) updated else stored })
    }

    fun remove(library: ConfigProfileLibrary, id: Int): ConfigProfileLibrary {
        if (library.configs.size <= 1 || library.configs.none { it.id == id }) return library
        val remaining = library.configs.filterNot { it.id == id }
        return library.copy(
            selectedConfigId = library.selectedConfigId
                ?.takeIf { selectedId -> selectedId != id && remaining.any { it.id == selectedId } }
                ?: remaining.firstOrNull()?.id,
            configs = remaining,
        )
    }
}
