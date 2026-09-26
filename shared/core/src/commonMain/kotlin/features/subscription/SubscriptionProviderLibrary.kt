// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlinx.serialization.Serializable

@Serializable
data class StoredSubscriptionMetadata(
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
data class StoredSubscription(
    val id: Int,
    val url: String,
    val userAgent: String = "",
    val name: String = "",
    val metadata: StoredSubscriptionMetadata = StoredSubscriptionMetadata(),
    val enabled: Boolean = true,
    val updateInterval: String = "",
    val ageSecretKey: String = "",
    val updateViaProxy: Boolean = false,
    val autoOverrideRules: Boolean = true,
    val notifyOnExpiry: Boolean = true,
    val customExpiryReminders: List<SubscriptionExpiryReminder>? = null,
)

@Serializable
data class SubscriptionProviderLibrary(val subscriptions: List<StoredSubscription> = emptyList())

data class SubscriptionProviderEdit(
    val name: String,
    val url: String,
    val userAgent: String,
    val enabled: Boolean,
    val updateInterval: String,
    val ageSecretKey: String,
    val updateViaProxy: Boolean,
    val autoOverrideRules: Boolean,
    val notifyOnExpiry: Boolean,
    val customExpiryReminders: List<SubscriptionExpiryReminder>?,
)

fun StoredSubscription.toProviderEdit(): SubscriptionProviderEdit = SubscriptionProviderEdit(
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

/** Pure transformations for stored providers. Scheduling and reconciliation policy use shared subscription APIs. */
object SubscriptionProviderLibraries {
    fun addOrReplace(
        library: SubscriptionProviderLibrary,
        url: String,
        userAgent: String = "",
        name: String = "",
        metadata: SubscriptionMetadata? = null,
        nowMillis: Long,
    ): SubscriptionProviderLibrary {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isValidManualSubscriptionUrl()) { "Invalid subscription URL" }
        val existing = library.subscriptions.firstOrNull { it.url == normalizedUrl }
        val normalizedName = name.trim().takeIf(String::isNotBlank)
            ?: metadata?.profileTitle?.trim()?.takeIf(String::isNotBlank)
            ?: existing?.name.orEmpty()
        val stored = StoredSubscription(
            id = existing?.id ?: (library.subscriptions.maxOfOrNull { it.id } ?: 0) + 1,
            url = normalizedUrl,
            userAgent = userAgent.trim().ifBlank { existing?.userAgent.orEmpty() },
            name = normalizedName,
            metadata = metadata?.toStoredSubscriptionMetadata(existing?.metadata ?: StoredSubscriptionMetadata(), nowMillis)
                ?: existing?.metadata
                ?: StoredSubscriptionMetadata(),
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

    /** Updates only editor-owned properties and retains ID, fetched metadata, and imported cache membership. */
    fun updateProvider(
        library: SubscriptionProviderLibrary,
        subscriptionId: Int,
        edit: SubscriptionProviderEdit,
    ): SubscriptionProviderLibrary {
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
        require(library.subscriptions.none { it.id != subscriptionId && it.url == normalizedUrl }) {
            "A subscription with this URL already exists"
        }

        val normalizedUserAgent = edit.userAgent.trim()
        require(normalizedUserAgent.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "Invalid subscription user agent"
        }
        require(normalizedUserAgent.length <= MaxSubscriptionUserAgentLength) {
            "Subscription user agent is too long"
        }

        val normalizedInterval = normalizeSubscriptionUpdateInterval(edit.updateInterval)
        val normalizedAgeSecretKey = edit.ageSecretKey.trim()
        require(normalizedAgeSecretKey.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "Invalid subscription age secret key"
        }
        val normalizedReminders = validateSubscriptionExpiryReminders(edit.customExpiryReminders)
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
            customExpiryReminders = normalizedReminders,
        )
        return library.copy(subscriptions = library.subscriptions.map { stored ->
            if (stored.id == subscriptionId) updated else stored
        })
    }

    fun remove(library: SubscriptionProviderLibrary, subscriptionId: Int): SubscriptionProviderLibrary =
        library.copy(subscriptions = library.subscriptions.filterNot { it.id == subscriptionId })
}

private fun normalizeSubscriptionUpdateInterval(value: String): String {
    val normalized = value.trim()
    require(
        normalized.isBlank() ||
            (normalized.all { it.isDigit() || it == '.' } && normalized.count { it == '.' } <= 1 &&
                isValidSubscriptionIntervalInput(normalized)),
    ) { "Invalid subscription update interval" }
    return normalized
}

private fun SubscriptionMetadata.toStoredSubscriptionMetadata(
    previous: StoredSubscriptionMetadata,
    nowMillis: Long,
): StoredSubscriptionMetadata = StoredSubscriptionMetadata(
    description = profileDescription ?: previous.description,
    announce = announce ?: previous.announce,
    supportUrl = supportUrl ?: previous.supportUrl,
    supportEmail = supportEmail ?: previous.supportEmail,
    profileWebPageUrl = profileWebPageUrl ?: previous.profileWebPageUrl,
    announceUrl = announceUrl ?: previous.announceUrl,
    trafficUploadBytes = if (userInfoReceived) trafficUploadBytes else previous.trafficUploadBytes,
    trafficDownloadBytes = if (userInfoReceived) trafficDownloadBytes else previous.trafficDownloadBytes,
    trafficTotalBytes = if (userInfoReceived) trafficTotalBytes else previous.trafficTotalBytes,
    trafficExpireAtSeconds = if (userInfoReceived) trafficExpireAtSeconds else previous.trafficExpireAtSeconds,
    profileUpdateIntervalHours = profileUpdateIntervalHours ?: previous.profileUpdateIntervalHours,
    embeddedConfigPayload = embeddedConfig?.payload ?: previous.embeddedConfigPayload,
    embeddedConfigActivate = embeddedConfig?.activate ?: previous.embeddedConfigActivate,
    embeddedConfigIsUrl = embeddedConfig?.isUrl ?: previous.embeddedConfigIsUrl,
    lastUpdatedAtMillis = nowMillis,
)

private const val MaxSubscriptionUserAgentLength = 512
