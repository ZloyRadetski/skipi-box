// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import app.ProjectInfo

const val DefaultSubscriptionGroupId = 1
val DefaultSubscriptionUserAgent = "SKIPI/${ProjectInfo.VERSION_NAME}/Android"
const val ClashMetaSubscriptionUserAgent = "clash.meta"
const val FlClashXSubscriptionUserAgent = "FlClash X/v0.4.2 Platform/android"

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
internal enum class SubscriptionUserAgentSelection {
    V2rayNg,
    ClashMeta,
    FlClashX,
    Custom,
}

internal val SubscriptionUserAgentSelections = listOf(
    SubscriptionUserAgentSelection.V2rayNg,
    SubscriptionUserAgentSelection.ClashMeta,
    SubscriptionUserAgentSelection.FlClashX,
    SubscriptionUserAgentSelection.Custom,
)

internal fun SubscriptionUserAgentSelection.userAgentOrNull(): String? = when (this) {
    SubscriptionUserAgentSelection.V2rayNg -> DefaultSubscriptionUserAgent
    SubscriptionUserAgentSelection.ClashMeta -> ClashMetaSubscriptionUserAgent
    SubscriptionUserAgentSelection.FlClashX -> FlClashXSubscriptionUserAgent
    SubscriptionUserAgentSelection.Custom -> null
}

internal fun SubscriptionUserAgentSelection.resolveUserAgent(customUserAgent: String): String {
    return userAgentOrNull() ?: customUserAgent.trim().ifBlank { DefaultSubscriptionUserAgent }
}

internal fun subscriptionUserAgentSelectionFor(userAgent: String): SubscriptionUserAgentSelection {
    val trimmedUserAgent = userAgent.trim()
    return SubscriptionUserAgentSelections.firstOrNull { selection ->
        selection.userAgentOrNull() == trimmedUserAgent
    } ?: SubscriptionUserAgentSelection.Custom
}
