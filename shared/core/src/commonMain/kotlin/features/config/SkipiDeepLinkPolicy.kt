// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

/** What must hold before an externally supplied link may change anything. */
enum class DeepLinkGate {
    Allowed,

    /** Adds servers or configs: any web page can send it, so the user must approve. */
    NeedsConfirmation,

    /** Starts or stops the tunnel: only honoured when external control is enabled. */
    NeedsAutomationEnabled,
}

fun SkipiDeepLink.gate(): DeepLinkGate = when (this) {
    is SkipiDeepLink.Subscription,
    is SkipiDeepLink.ManualServer,
    is SkipiDeepLink.TrafficConfig,
    -> DeepLinkGate.NeedsConfirmation

    SkipiDeepLink.Connect,
    SkipiDeepLink.Disconnect,
    SkipiDeepLink.Toggle,
    -> DeepLinkGate.NeedsAutomationEnabled

    SkipiDeepLink.Open,
    SkipiDeepLink.Close,
    -> DeepLinkGate.Allowed
}

/** The host a confirmation dialog shows, or an empty string for inline content. */
fun SkipiDeepLink.confirmationTarget(): String = when (this) {
    is SkipiDeepLink.Subscription -> url.hostOrEmpty()
    is SkipiDeepLink.ManualServer -> url.hostOrEmpty()
    is SkipiDeepLink.TrafficConfig -> content.takeIf { it.contains("://") && !it.contains('\n') }.orEmpty().hostOrEmpty()
    else -> ""
}

private fun String.hostOrEmpty(): String {
    val afterScheme = substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return ""
    val authority = afterScheme
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
    return if (authority.startsWith("[")) {
        authority.substringBefore(']').removePrefix("[")
    } else {
        authority.substringBefore(':')
    }
}
