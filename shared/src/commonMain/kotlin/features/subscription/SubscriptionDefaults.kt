// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import app.SharedProjectInfo

const val DefaultSubscriptionGroupId = 1
val DefaultSubscriptionUserAgent = "SKIPI/${SharedProjectInfo.VERSION_NAME}/Android"
const val ClashMetaSubscriptionUserAgent = "clash.meta"
const val FlClashXSubscriptionUserAgent = "FlClash X/v0.4.2 Platform/android"

fun isSkipiUserAgent(userAgent: String): Boolean {
    val trimmed = userAgent.trim()
    return trimmed.isBlank() || (trimmed.startsWith("SKIPI/") && trimmed.endsWith("/Android"))
}

fun normalizeSkipiUserAgent(userAgent: String): String {
    return if (isSkipiUserAgent(userAgent)) DefaultSubscriptionUserAgent else userAgent
}

fun normalizeSkipiUserAgents(userAgents: List<String>): List<String> {
    val normalized = userAgents.map { normalizeSkipiUserAgent(it) }.distinct()
    return if (DefaultSubscriptionUserAgent !in normalized) {
        listOf(DefaultSubscriptionUserAgent) + normalized
    } else {
        normalized
    }
}

/** Presets shown in Settings and in each subscription editor. Users may add more. */
val DefaultSubscriptionUserAgents = listOf(
    DefaultSubscriptionUserAgent,
    ClashMetaSubscriptionUserAgent,
    FlClashXSubscriptionUserAgent,
    "Mihomo/1.19.0",
    "ClashForAndroid/2.5.12",
    "FlClash/0.8.73",
    "Shadowrocket/2.2.48",
    "Surfboard/2.23.0",
    "NekoBoxForAndroid/1.3.9",
)
enum class SubscriptionUserAgentSelection {
    V2rayNg,
    ClashMeta,
    FlClashX,
    Custom,
}

val SubscriptionUserAgentSelections = listOf(
    SubscriptionUserAgentSelection.V2rayNg,
    SubscriptionUserAgentSelection.ClashMeta,
    SubscriptionUserAgentSelection.FlClashX,
    SubscriptionUserAgentSelection.Custom,
)

fun SubscriptionUserAgentSelection.userAgentOrNull(): String? = when (this) {
    SubscriptionUserAgentSelection.V2rayNg -> DefaultSubscriptionUserAgent
    SubscriptionUserAgentSelection.ClashMeta -> ClashMetaSubscriptionUserAgent
    SubscriptionUserAgentSelection.FlClashX -> FlClashXSubscriptionUserAgent
    SubscriptionUserAgentSelection.Custom -> null
}

fun SubscriptionUserAgentSelection.resolveUserAgent(customUserAgent: String): String {
    return userAgentOrNull() ?: normalizeSkipiUserAgent(customUserAgent)
}

fun subscriptionUserAgentSelectionFor(userAgent: String): SubscriptionUserAgentSelection {
    if (isSkipiUserAgent(userAgent)) return SubscriptionUserAgentSelection.V2rayNg
    val trimmedUserAgent = userAgent.trim()
    return SubscriptionUserAgentSelections.firstOrNull { selection ->
        selection.userAgentOrNull() == trimmedUserAgent
    } ?: SubscriptionUserAgentSelection.Custom
}
