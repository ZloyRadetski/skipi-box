// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

@Serializable
data class DesktopStoredConfig(
    val id: Int,
    val name: String,
    val content: String,
    val sourceUrl: String = "",
    val updateLocked: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
)

@Serializable data class DesktopConfigLibrary(val selectedConfigId: Int? = null, val configs: List<DesktopStoredConfig> = emptyList())

object DesktopConfigLibraries {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    fun defaultPath(): Path = Path.of(System.getenv("APPDATA")?.takeIf(String::isNotBlank) ?: System.getProperty("user.home"), "SKIPI", "configs.json")
    fun loadDefault(): Result<DesktopConfigLibrary> = load(defaultPath())
    fun saveDefault(value: DesktopConfigLibrary): Result<Unit> = save(defaultPath(), value)
    fun load(path: Path): Result<DesktopConfigLibrary> = runCatching {
        if (!Files.exists(path)) {
            DesktopConfigLibrary()
        } else {
            json.decodeFromString<DesktopConfigLibrary>(Files.readString(path, StandardCharsets.UTF_8)).normalized()
        }
    }
    fun save(path: Path, value: DesktopConfigLibrary): Result<Unit> = runCatching {
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(value), StandardCharsets.UTF_8)
        try {
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, REPLACE_EXISTING)
        }
    }

    fun put(
        library: DesktopConfigLibrary,
        name: String,
        content: String,
        sourceUrl: String? = null,
        updateLocked: Boolean? = null,
        lastUpdatedAtMillis: Long? = null,
    ): DesktopConfigLibrary {
        require(content.isNotBlank()) { "Config cannot be empty" }
        val normalizedName = name.trim().ifBlank { "Config ${(library.configs.maxOfOrNull { it.id } ?: 0) + 1}" }
        val normalizedUrl = sourceUrl?.trim()?.takeIf(String::isNotBlank)
        val existing = normalizedUrl
            ?.let { url -> library.configs.firstOrNull { it.sourceUrl.equals(url, ignoreCase = true) } }
            ?: library.configs.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }
        val id = existing?.id ?: (library.configs.maxOfOrNull { it.id } ?: 0) + 1
        val stored = DesktopStoredConfig(
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
        )
    }
    fun select(library: DesktopConfigLibrary, id: Int): DesktopConfigLibrary {
        require(library.configs.any { it.id == id }) { "Unknown config ID: $id" }
        return library.copy(selectedConfigId = id)
    }

    fun update(
        library: DesktopConfigLibrary,
        id: Int,
        name: String,
        content: String,
        sourceUrl: String,
        updateLocked: Boolean,
        lastUpdatedAtMillis: Long,
    ): DesktopConfigLibrary {
        require(content.isNotBlank()) { "Config cannot be empty" }
        require(library.configs.any { it.id == id }) { "Unknown config ID: $id" }
        val updated = DesktopStoredConfig(
            id = id,
            name = name.trim().ifBlank { "Config $id" },
            content = content,
            sourceUrl = sourceUrl.trim(),
            updateLocked = updateLocked,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
        )
        return library.copy(
            selectedConfigId = library.selectedConfigId,
            configs = library.configs.map { stored -> if (stored.id == id) updated else stored },
        )
    }

    fun remove(library: DesktopConfigLibrary, id: Int): DesktopConfigLibrary {
        val remaining = library.configs.filterNot { it.id == id }
        return library.copy(
            selectedConfigId = library.selectedConfigId
                ?.takeIf { selectedId -> selectedId != id && remaining.any { config -> config.id == selectedId } }
                ?: remaining.firstOrNull()?.id,
            configs = remaining,
        )
    }

    private fun DesktopConfigLibrary.normalized(): DesktopConfigLibrary {
        val selected = selectedConfigId?.takeIf { selectedId -> configs.any { config -> config.id == selectedId } }
            ?: configs.firstOrNull()?.id
        return copy(selectedConfigId = selected)
    }
}
