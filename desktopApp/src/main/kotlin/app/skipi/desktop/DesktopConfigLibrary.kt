// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

@Serializable data class DesktopStoredConfig(val id: Int, val name: String, val content: String)
@Serializable data class DesktopConfigLibrary(val selectedConfigId: Int? = null, val configs: List<DesktopStoredConfig> = emptyList())

object DesktopConfigLibraries {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    fun defaultPath(): Path = Path.of(System.getenv("APPDATA")?.takeIf(String::isNotBlank) ?: System.getProperty("user.home"), "SKIPI", "configs.json")
    fun loadDefault(): Result<DesktopConfigLibrary> = load(defaultPath())
    fun saveDefault(value: DesktopConfigLibrary): Result<Unit> = save(defaultPath(), value)
    fun load(path: Path): Result<DesktopConfigLibrary> = runCatching {
        if (!Files.exists(path)) DesktopConfigLibrary() else json.decodeFromString(Files.readString(path, StandardCharsets.UTF_8))
    }
    fun save(path: Path, value: DesktopConfigLibrary): Result<Unit> = runCatching {
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(value), StandardCharsets.UTF_8)
        Files.move(temporary, path, REPLACE_EXISTING)
    }
    fun put(library: DesktopConfigLibrary, name: String, content: String): DesktopConfigLibrary {
        require(content.isNotBlank()) { "Config cannot be empty" }
        val existing = library.configs.firstOrNull { it.name == name }
        val id = existing?.id ?: (library.configs.maxOfOrNull { it.id } ?: 0) + 1
        return library.copy(selectedConfigId = id, configs = library.configs.filterNot { it.id == id } + DesktopStoredConfig(id, name.ifBlank { "Config $id" }, content))
    }
    fun select(library: DesktopConfigLibrary, id: Int): DesktopConfigLibrary {
        require(library.configs.any { it.id == id }) { "Unknown config ID: $id" }
        return library.copy(selectedConfigId = id)
    }
    fun remove(library: DesktopConfigLibrary, id: Int): DesktopConfigLibrary = library.copy(
        selectedConfigId = library.selectedConfigId.takeIf { it != id },
        configs = library.configs.filterNot { it.id == id },
    )
}
