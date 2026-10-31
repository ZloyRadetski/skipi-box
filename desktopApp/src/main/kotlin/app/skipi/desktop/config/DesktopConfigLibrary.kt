// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigLibraryOperations
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

/** The selected profile, if the selection points at an existing one. */
val DesktopConfigLibrary.activeProfile: DesktopStoredConfig?
    get() = selectedConfigId?.let { selectedId -> configs.firstOrNull { profile -> profile.id == selectedId } }

/** Whether the tunnel would run a different profile (or the same one with edited content) than in [previous]. */
fun DesktopConfigLibrary.activeProfileChangedFrom(previous: DesktopConfigLibrary): Boolean {
    val before = previous.activeProfile
    val after = activeProfile
    return before?.id != after?.id || before?.content != after?.content
}

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
            TrafficConfigLibraryOperations.normalize(
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
    ): DesktopConfigLibrary = TrafficConfigLibraryOperations.put(
        library, name, content, sourceUrl, updateLocked, lastUpdatedAtMillis,
    )

    fun create(
        library: DesktopConfigLibrary,
        profile: ConfigProfile,
        resourceUserAgentFallback: String = versionedDesktopProfileResourceUserAgent(app.ProjectInfo.VERSION_NAME),
    ): DesktopConfigLibrary = TrafficConfigLibraryOperations.create(
        library, profile, resourceUserAgentFallback,
    )

    fun importDocument(
        library: DesktopConfigLibrary,
        content: String,
        activate: Boolean,
        fallbackName: String = "Config",
        sourceUrl: String = "",
        resourceUserAgentFallback: String = versionedDesktopProfileResourceUserAgent(app.ProjectInfo.VERSION_NAME),
    ): DesktopConfigLibrary = TrafficConfigLibraryOperations.importDocument(
        library = library,
        content = content,
        activate = activate,
        fallbackName = fallbackName,
        sourceUrl = sourceUrl,
        resourceUserAgentFallback = resourceUserAgentFallback,
    )

    fun duplicate(
        library: DesktopConfigLibrary,
        sourceProfileId: Int,
        name: String,
        resourceUserAgentFallback: String = versionedDesktopProfileResourceUserAgent(app.ProjectInfo.VERSION_NAME),
    ): DesktopConfigLibrary = TrafficConfigLibraryOperations.duplicate(
        library, sourceProfileId, name, resourceUserAgentFallback,
    )

    fun duplicate(
        library: DesktopConfigLibrary,
        sourceProfile: DesktopStoredConfig,
        name: String,
        resourceUserAgentFallback: String = versionedDesktopProfileResourceUserAgent(app.ProjectInfo.VERSION_NAME),
    ): DesktopConfigLibrary = TrafficConfigLibraryOperations.duplicate(
        library, sourceProfile, name, resourceUserAgentFallback,
    )

    fun select(library: DesktopConfigLibrary, id: Int?): DesktopConfigLibrary =
        TrafficConfigLibraryOperations.select(library, id)

    fun update(
        library: DesktopConfigLibrary,
        id: Int,
        name: String,
        content: String,
        sourceUrl: String,
        updateLocked: Boolean,
        lastUpdatedAtMillis: Long,
    ): DesktopConfigLibrary = TrafficConfigLibraryOperations.update(
        library, id, name, content, sourceUrl, updateLocked, lastUpdatedAtMillis,
    )

    fun remove(library: DesktopConfigLibrary, id: Int): DesktopConfigLibrary =
        TrafficConfigLibraryOperations.remove(library, id)
}
