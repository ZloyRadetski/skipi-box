// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.isValidManualSubscriptionUrl
import features.subscription.SubscriptionMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
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

/** Serializable desktop counterpart of Android's expiry-reminder unit. */
@Serializable
enum class DesktopSubscriptionExpiryReminderUnit {
    Minutes,
    Hours,
    Days,
    Weeks,
    AtExpiration,
}

/**
 * Stored provider-specific expiry reminder. A `null` reminder list means the
 * desktop provider has no custom schedule, just like Android's group state.
 */
@Serializable
data class DesktopSubscriptionExpiryReminder(
    val value: Int = 1,
    val unit: DesktopSubscriptionExpiryReminderUnit = DesktopSubscriptionExpiryReminderUnit.Days,
)

@Serializable
data class DesktopStoredSubscription(
    val id: Int,
    val url: String,
    val userAgent: String = DefaultDesktopSubscriptionUserAgent,
    val name: String = "",
    val metadata: DesktopStoredSubscriptionMetadata = DesktopStoredSubscriptionMetadata(),
    /** Matches Android's group switch; disabled providers stay stored for a reversible edit. */
    val enabled: Boolean = true,
    /** Android-compatible automatic refresh interval, in hours. An empty value disables scheduling. */
    val updateInterval: String = "",
    /** Persisted Android-compatible provider option; fetching support is owned outside this catalog. */
    val ageSecretKey: String = "",
    val updateViaProxy: Boolean = false,
    val autoOverrideRules: Boolean = true,
    val notifyOnExpiry: Boolean = true,
    val customExpiryReminders: List<DesktopSubscriptionExpiryReminder>? = null,
)

@Serializable
data class DesktopSubscriptionLibrary(val subscriptions: List<DesktopStoredSubscription> = emptyList())

/**
 * The editable provider fields. Keeping this separate from [DesktopStoredSubscription]
 * prevents an editor from accidentally replacing the stable id or fetched metadata.
 */
data class DesktopSubscriptionProviderEdit(
    val name: String,
    val url: String,
    val userAgent: String,
    val enabled: Boolean,
    val updateInterval: String,
    val ageSecretKey: String,
    val updateViaProxy: Boolean,
    val autoOverrideRules: Boolean,
    val notifyOnExpiry: Boolean,
    val customExpiryReminders: List<DesktopSubscriptionExpiryReminder>?,
)

fun DesktopStoredSubscription.toProviderEdit(): DesktopSubscriptionProviderEdit =
    DesktopSubscriptionProviderEdit(
        name = name,
        url = url,
        userAgent = userAgent,
        enabled = enabled,
        updateInterval = updateInterval,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
    )

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
        try {
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, REPLACE_EXISTING)
        }
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
            id = existing?.id ?: (library.subscriptions.maxOfOrNull { it.id } ?: 0) + 1,
            url = normalizedUrl,
            userAgent = userAgent.trim().ifBlank { existing?.userAgent ?: DefaultDesktopSubscriptionUserAgent },
            name = normalizedName,
            metadata = metadata?.toStoredSubscriptionMetadata(existing?.metadata ?: DesktopStoredSubscriptionMetadata())
                ?: existing?.metadata
                ?: DesktopStoredSubscriptionMetadata(),
            enabled = existing?.enabled ?: true,
            updateInterval = existing?.updateInterval.orEmpty(),
            ageSecretKey = existing?.ageSecretKey.orEmpty(),
            updateViaProxy = existing?.updateViaProxy ?: false,
            autoOverrideRules = existing?.autoOverrideRules ?: true,
            notifyOnExpiry = existing?.notifyOnExpiry ?: true,
            customExpiryReminders = existing?.customExpiryReminders,
        )
        return library.copy(subscriptions = library.subscriptions.filterNot { it.id == stored.id } + stored)
    }

    /**
     * Replaces only user-editable provider properties. It intentionally keeps
     * imported server membership, subscription metadata, id, and the refresh
     * timestamp intact so editing a provider cannot erase a successfully
     * imported subscription.
     */
    fun updateProvider(
        library: DesktopSubscriptionLibrary,
        subscriptionId: Int,
        edit: DesktopSubscriptionProviderEdit,
    ): DesktopSubscriptionLibrary {
        val matching = library.subscriptions.filter { it.id == subscriptionId }
        require(matching.size == 1) {
            if (matching.isEmpty()) "Unknown subscription ID: $subscriptionId"
            else "Ambiguous subscription ID: $subscriptionId"
        }
        val current = matching.single()
        val normalizedUrl = edit.url.trim()
        require(normalizedUrl.isValidManualSubscriptionUrl()) { "Invalid subscription URL" }
        require(normalizedUrl.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "Invalid subscription URL"
        }
        require(
            library.subscriptions.none { stored ->
                stored.id != subscriptionId && stored.url == normalizedUrl
            },
        ) { "A subscription with this URL already exists" }

        val normalizedUserAgent = edit.userAgent.trim()
            .ifBlank { DefaultDesktopSubscriptionUserAgent }
        require(normalizedUserAgent.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "Invalid subscription user agent"
        }
        require(normalizedUserAgent.length <= MaxDesktopSubscriptionUserAgentLength) {
            "Subscription user agent is too long"
        }

        val normalizedInterval = normalizeDesktopSubscriptionUpdateInterval(edit.updateInterval)
        val normalizedAgeSecretKey = normalizeDesktopSubscriptionAgeSecretKey(edit.ageSecretKey)
        val normalizedCustomExpiryReminders = normalizeDesktopSubscriptionExpiryReminders(edit.customExpiryReminders)
        val updated = current.copy(
            name = edit.name.trim(),
            url = normalizedUrl,
            userAgent = normalizedUserAgent,
            enabled = edit.enabled,
            updateInterval = normalizedInterval,
            ageSecretKey = normalizedAgeSecretKey,
            updateViaProxy = edit.updateViaProxy,
            autoOverrideRules = edit.autoOverrideRules,
            notifyOnExpiry = edit.notifyOnExpiry,
            customExpiryReminders = normalizedCustomExpiryReminders,
        )
        return library.copy(
            subscriptions = library.subscriptions.map { stored ->
                if (stored.id == subscriptionId) updated else stored
            },
        )
    }

    fun remove(library: DesktopSubscriptionLibrary, subscriptionId: Int): DesktopSubscriptionLibrary =
        library.copy(subscriptions = library.subscriptions.filterNot { it.id == subscriptionId })
}

private fun normalizeDesktopSubscriptionUpdateInterval(value: String): String {
    val normalized = value.trim()
    require(normalized.isValidDesktopSubscriptionUpdateInterval()) {
        "Invalid subscription update interval"
    }
    return normalized
}

private fun normalizeDesktopSubscriptionAgeSecretKey(value: String): String {
    val normalized = value.trim()
    require(normalized.none { it == '\r' || it == '\n' || it == '\u0000' }) {
        "Invalid subscription age secret key"
    }
    return normalized
}

private fun normalizeDesktopSubscriptionExpiryReminders(
    reminders: List<DesktopSubscriptionExpiryReminder>?,
): List<DesktopSubscriptionExpiryReminder>? {
    reminders?.forEach { reminder ->
        when (reminder.unit) {
            DesktopSubscriptionExpiryReminderUnit.AtExpiration -> require(reminder.value == 0) {
                "At-expiration reminder must use zero"
            }

            else -> require(reminder.value > 0) {
                "Expiry reminder value must be positive"
            }
        }
    }
    return reminders?.toList()
}

/** Mirrors the Android editor: blank or zero disables scheduling; a positive interval is at least 0.25 h. */
private fun String.isValidDesktopSubscriptionUpdateInterval(): Boolean {
    if (isBlank()) return true
    if (any { character -> !character.isDigit() && character != '.' } || count { it == '.' } > 1) return false
    val hours = runCatching { BigDecimal(this) }.getOrNull() ?: return false
    if (hours.signum() == 0) return true
    if (hours < MinimumDesktopSubscriptionUpdateHours) return false
    return hours.multiply(DesktopMillisecondsPerHour) <= MaximumDesktopSubscriptionIntervalMillis
}

private val MinimumDesktopSubscriptionUpdateHours = BigDecimal("0.25")
private val DesktopMillisecondsPerHour = BigDecimal(60L * 60L * 1_000L)
private val MaximumDesktopSubscriptionIntervalMillis = BigDecimal.valueOf(Long.MAX_VALUE)
private const val MaxDesktopSubscriptionUserAgentLength = 512

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
