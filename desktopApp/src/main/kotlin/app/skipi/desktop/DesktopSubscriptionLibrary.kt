// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.isValidManualSubscriptionUrl
import features.subscription.SubscriptionMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

@Serializable
data class DesktopStoredSubscriptionMetadata(
    val description: String = "",
    val announce: String = "",
    val supportUrl: String = "",
    val supportEmail: String = "",
    val profileWebPageUrl: String = "",
    val announceUrl: String = "",
    val trafficUploadBytes: Long = -1L,
    val trafficDownloadBytes: Long = -1L,
    val trafficTotalBytes: Long = -1L,
    val trafficExpireAtSeconds: Long = -1L,
    val profileUpdateIntervalHours: String = "",
    val embeddedConfigPayload: String = "",
    val embeddedConfigActivate: Boolean = false,
    val embeddedConfigIsUrl: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
)

@Serializable
data class DesktopStoredSubscription(
    val id: Int,
    val url: String,
    val userAgent: String = DefaultDesktopSubscriptionUserAgent,
    val name: String = "",
    val metadata: DesktopStoredSubscriptionMetadata = DesktopStoredSubscriptionMetadata(),
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

    fun addOrReplace(
        library: DesktopSubscriptionLibrary,
        url: String,
        userAgent: String,
        name: String = "",
        metadata: SubscriptionMetadata? = null,
    ): DesktopSubscriptionLibrary {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isValidManualSubscriptionUrl()) { "Invalid subscription URL" }
        val existing = library.subscriptions.firstOrNull { it.url == normalizedUrl }
        val normalizedName = name.trim().takeIf(String::isNotBlank)
            ?: metadata?.profileTitle?.trim()?.takeIf(String::isNotBlank)
            ?: existing?.name.orEmpty()
        val stored = DesktopStoredSubscription(
            existing?.id ?: (library.subscriptions.maxOfOrNull { it.id } ?: 0) + 1,
            normalizedUrl,
            userAgent.trim().ifBlank { existing?.userAgent ?: DefaultDesktopSubscriptionUserAgent },
            normalizedName,
            metadata?.toStoredSubscriptionMetadata(existing?.metadata ?: DesktopStoredSubscriptionMetadata())
                ?: existing?.metadata
                ?: DesktopStoredSubscriptionMetadata(),
        )
        return library.copy(subscriptions = library.subscriptions.filterNot { it.id == stored.id } + stored)
    }

    fun remove(library: DesktopSubscriptionLibrary, subscriptionId: Int): DesktopSubscriptionLibrary =
        library.copy(subscriptions = library.subscriptions.filterNot { it.id == subscriptionId })
}

private fun SubscriptionMetadata.toStoredSubscriptionMetadata(
    previous: DesktopStoredSubscriptionMetadata,
): DesktopStoredSubscriptionMetadata =
    DesktopStoredSubscriptionMetadata(
        description = this.profileDescription ?: previous.description,
        announce = this.announce ?: previous.announce,
        supportUrl = this.supportUrl ?: previous.supportUrl,
        supportEmail = this.supportEmail ?: previous.supportEmail,
        profileWebPageUrl = this.profileWebPageUrl ?: previous.profileWebPageUrl,
        announceUrl = this.announceUrl ?: previous.announceUrl,
        trafficUploadBytes = if (this.userInfoReceived) this.trafficUploadBytes else previous.trafficUploadBytes,
        trafficDownloadBytes = if (this.userInfoReceived) this.trafficDownloadBytes else previous.trafficDownloadBytes,
        trafficTotalBytes = if (this.userInfoReceived) this.trafficTotalBytes else previous.trafficTotalBytes,
        trafficExpireAtSeconds = if (this.userInfoReceived) this.trafficExpireAtSeconds else previous.trafficExpireAtSeconds,
        profileUpdateIntervalHours = this.profileUpdateIntervalHours ?: previous.profileUpdateIntervalHours,
        embeddedConfigPayload = this.embeddedConfig?.payload ?: previous.embeddedConfigPayload,
        embeddedConfigActivate = this.embeddedConfig?.activate ?: previous.embeddedConfigActivate,
        embeddedConfigIsUrl = this.embeddedConfig?.isUrl ?: previous.embeddedConfigIsUrl,
        lastUpdatedAtMillis = System.currentTimeMillis(),
    )
