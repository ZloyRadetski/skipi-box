// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ConfigProfileLibraries
import features.config.ConfigProfile
import features.config.ConfigProfileLibrary
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Compatibility aliases preserve Desktop call sites and the existing JSON shape. */
typealias DesktopStoredConfig = ConfigProfile
typealias DesktopConfigLibrary = ConfigProfileLibrary

/** Desktop filesystem/JSON adapter; config transformations live in shared core. */
object DesktopConfigLibraries {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun defaultPath(): Path = Path.of(
        System.getenv("APPDATA")?.takeIf(String::isNotBlank) ?: System.getProperty("user.home"),
        "SKIPI",
        "configs.json",
    )

    fun loadDefault(): Result<DesktopConfigLibrary> = load(defaultPath())
    fun saveDefault(value: DesktopConfigLibrary): Result<Unit> = save(defaultPath(), value)

    fun load(path: Path): Result<DesktopConfigLibrary> = runCatching {
        if (!Files.exists(path)) {
            DesktopConfigLibrary()
        } else {
            ConfigProfileLibraries.normalize(
                json.decodeFromString<ConfigProfileLibrary>(Files.readString(path, StandardCharsets.UTF_8)),
            )
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
    ): DesktopConfigLibrary = ConfigProfileLibraries.put(
        library, name, content, sourceUrl, updateLocked, lastUpdatedAtMillis,
    )

    fun select(library: DesktopConfigLibrary, id: Int): DesktopConfigLibrary =
        ConfigProfileLibraries.select(library, id)

    fun update(
        library: DesktopConfigLibrary,
        id: Int,
        name: String,
        content: String,
        sourceUrl: String,
        updateLocked: Boolean,
        lastUpdatedAtMillis: Long,
    ): DesktopConfigLibrary = ConfigProfileLibraries.update(
        library, id, name, content, sourceUrl, updateLocked, lastUpdatedAtMillis,
    )

    fun remove(library: DesktopConfigLibrary, id: Int): DesktopConfigLibrary =
        ConfigProfileLibraries.remove(library, id)
}
