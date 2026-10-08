// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.subscription

import app.skipi.app.model.SubscriptionRecord
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.SubscriptionExpiryReminder

/** UI projection of an app subscription group; contains no persistence or platform types. */
data class SubscriptionGroupUiState(
    val id: Int,
    val name: String,
    val url: String,
    val userAgent: String,
    val updateInterval: String,
    val hwid: String = "",
    val ageSecretKey: String = "",
    val updateViaProxy: Boolean = false,
    val autoOverrideRules: Boolean = true,
    val enabled: Boolean,
    val builtIn: Boolean = false,
    val lastUpdatedAtMillis: Long = 0L,
    val profileTitle: String = "",
    val announce: String = "",
    val supportUrl: String = "",
    val supportEmail: String = "",
    val profileWebPageUrl: String = "",
    val announceUrl: String = "",
    val trafficUploadBytes: Long = -1L,
    val trafficDownloadBytes: Long = -1L,
    val trafficTotalBytes: Long = -1L,
    val trafficExpireAtSeconds: Long = -1L,
    val notifyOnExpiry: Boolean = true,
    val customExpiryReminders: List<SubscriptionExpiryReminder>? = null,
)

/**
 * Projects a persisted subscription into the editor/card model.
 *
 * Android has a display-only name for its built-in default group (ID 1). The
 * `builtIn` check is essential because Desktop may have an ordinary group with
 * the same numeric ID.
 */
fun SubscriptionRecord.toSubscriptionGroupUiState(defaultGroupName: String): SubscriptionGroupUiState {
    val fetchedMetadata = metadata
    return SubscriptionGroupUiState(
        id = id,
        name = if (builtIn && id == DefaultSubscriptionGroupId) defaultGroupName else title,
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        hwid = hwid,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        enabled = enabled,
        builtIn = builtIn,
        lastUpdatedAtMillis = lastUpdatedAtMillis ?: 0L,
        profileTitle = fetchedMetadata?.profileTitle.orEmpty(),
        announce = fetchedMetadata?.announce.orEmpty(),
        supportUrl = fetchedMetadata?.supportUrl.orEmpty(),
        supportEmail = fetchedMetadata?.supportEmail.orEmpty(),
        profileWebPageUrl = fetchedMetadata?.profileWebPageUrl.orEmpty(),
        announceUrl = fetchedMetadata?.announceUrl.orEmpty(),
        trafficUploadBytes = fetchedMetadata?.takeIf { it.userInfoReceived }?.trafficUploadBytes ?: -1L,
        trafficDownloadBytes = fetchedMetadata?.takeIf { it.userInfoReceived }?.trafficDownloadBytes ?: -1L,
        trafficTotalBytes = fetchedMetadata?.takeIf { it.userInfoReceived }?.trafficTotalBytes ?: -1L,
        trafficExpireAtSeconds = fetchedMetadata?.takeIf { it.userInfoReceived }?.trafficExpireAtSeconds ?: -1L,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
    )
}

/**
 * Converts editor fields back to a draft, carrying untouched response metadata
 * and update time from the source row. The repository controller remains the
 * final authority and merges those values with the latest stored row on save.
 */
fun SubscriptionGroupUiState.toSubscriptionRecord(
    existing: SubscriptionRecord? = null,
): SubscriptionRecord = SubscriptionRecord(
    id = existing?.id ?: id,
    title = if (existing?.builtIn == true) existing.title else name,
    url = url,
    userAgent = userAgent,
    updateInterval = updateInterval,
    hwid = hwid,
    ageSecretKey = ageSecretKey,
    updateViaProxy = updateViaProxy,
    autoOverrideRules = autoOverrideRules,
    enabled = enabled,
    builtIn = existing?.builtIn ?: builtIn,
    metadata = existing?.metadata,
    lastUpdatedAtMillis = existing?.lastUpdatedAtMillis,
    notifyOnExpiry = notifyOnExpiry,
    customExpiryReminders = customExpiryReminders,
)

data class SubscriptionProviderPresentation(
    val title: String,
    val serverCount: Int,
    val trafficSummary: String? = null,
    val trafficProgress: Float? = null,
    val expirySummary: String? = null,
    val lastUpdatedLabel: String? = null,
    val announcement: String = "",
    val announcementUrl: String = "",
    val supportUrl: String = "",
    val websiteUrl: String = "",
)
