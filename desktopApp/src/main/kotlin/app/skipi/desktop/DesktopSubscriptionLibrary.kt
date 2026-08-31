// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.isValidManualSubscriptionUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

@Serializable
data class DesktopStoredSubscription(
    val id: Int,
    val url: String,
    val userAgent: String = DefaultDesktopSubscriptionUserAgent,
    val name: String = "",
)

@Serializable
data class DesktopSubscriptionLibrary(val subscriptions: List<DesktopStoredSubscription> = emptyList())

/** User-owned subscription catalog, independent of the imported server cache. */
object DesktopSubscriptionLibraries {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun defaultPath(): Path = Path.of(
        System.getenv("APPDATA")?.takeIf(String::isNotBlank) ?: System.getProperty("user.home"),
        "SKIPI", "subscriptions.json",
    )

    fun loadDefault(): Result<DesktopSubscriptionLibrary> = load(defaultPath())
    fun saveDefault(library: DesktopSubscriptionLibrary): Result<Unit> = save(defaultPath(), library)

    fun load(path: Path): Result<DesktopSubscriptionLibrary> = runCatching {
        if (!Files.exists(path)) DesktopSubscriptionLibrary()
        else Files.readString(path, StandardCharsets.UTF_8).takeIf(String::isNotBlank)
            ?.let(json::decodeFromString) ?: DesktopSubscriptionLibrary()
    }

    fun save(path: Path, library: DesktopSubscriptionLibrary): Result<Unit> = runCatching {
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(library), StandardCharsets.UTF_8)
        Files.move(temporary, path, REPLACE_EXISTING)
    }

    fun addOrReplace(library: DesktopSubscriptionLibrary, url: String, userAgent: String, name: String = ""): DesktopSubscriptionLibrary {
        require(url.isValidManualSubscriptionUrl()) { "Invalid subscription URL" }
        val existing = library.subscriptions.firstOrNull { it.url == url }
        val stored = DesktopStoredSubscription(existing?.id ?: (library.subscriptions.maxOfOrNull { it.id } ?: 0) + 1, url.trim(), userAgent, name)
        return library.copy(subscriptions = library.subscriptions.filterNot { it.id == stored.id } + stored)
    }

    fun remove(library: DesktopSubscriptionLibrary, subscriptionId: Int): DesktopSubscriptionLibrary =
        library.copy(subscriptions = library.subscriptions.filterNot { it.id == subscriptionId })
}
