// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.subscription

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
