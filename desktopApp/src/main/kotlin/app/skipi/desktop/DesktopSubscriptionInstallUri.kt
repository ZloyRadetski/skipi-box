// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.SkipiDeepLink
import features.config.parseSkipiDeepLinkOrNull
import features.subscription.SubscriptionInstallSource
import features.subscription.SubscriptionInstallUriParsingPolicy
import features.subscription.SubscriptionInstallUrlPolicy
import features.subscription.parseSubscriptionInstallUriOrNull

/**
 * A subscription-install link received from another client or pasted directly
 * by the user. Parsing is deliberately side-effect free: networking,
 * persistence and UI confirmation remain with the caller.
 */
data class DesktopSubscriptionInstallUri(
    val name: String,
    val url: String,
    val userAgent: String,
    val source: DesktopSubscriptionInstallSource,
) {
    companion object {
        fun parseOrNull(value: String): DesktopSubscriptionInstallUri? =
            DesktopSubscriptionInstallUriParser.parseOrNull(value)
    }
}

/** Shared client format names retained under the desktop API name. */
typealias DesktopSubscriptionInstallSource = SubscriptionInstallSource

/** Convenience form for import integrations. */
fun String.toDesktopSubscriptionInstallUriOrNull(): DesktopSubscriptionInstallUri? =
    DesktopSubscriptionInstallUri.parseOrNull(this)

/** Parses existing clipboard install formats plus SKIPI subscription links supported by Android. */
fun String.toDesktopSubscriptionInstallClipboardUriOrNull(): DesktopSubscriptionInstallUri? {
    DesktopSubscriptionInstallUri.parseOrNull(this)?.let { return it }
    val subscriptionLink = parseSkipiDeepLinkOrNull(this) as? SkipiDeepLink.Subscription ?: return null
    return DesktopSubscriptionInstallUri(
        name = "SKIPI subscription",
        url = subscriptionLink.url,
        userAgent = DefaultDesktopSubscriptionUserAgent,
        source = SubscriptionInstallSource.RawHttp,
    )
}

/** Thin desktop adapter over the common install-link parser. */
object DesktopSubscriptionInstallUriParser {
    fun parseOrNull(value: String): DesktopSubscriptionInstallUri? {
        val install = parseSubscriptionInstallUriOrNull(
            value = value,
            policy = DesktopSubscriptionInstallUriParsingPolicy,
        ) ?: return null
        return DesktopSubscriptionInstallUri(
            name = install.name,
            url = install.url,
            userAgent = install.source.desktopUserAgent,
            source = install.source,
        )
    }
}

private val DesktopSubscriptionInstallUriParsingPolicy = SubscriptionInstallUriParsingPolicy(
    directUrlPolicy = SubscriptionInstallUrlPolicy.HttpOrHttps,
    embeddedUrlPolicy = SubscriptionInstallUrlPolicy.HttpOrHttps,
)

private val SubscriptionInstallSource.desktopUserAgent: String
    get() = when (this) {
        SubscriptionInstallSource.RawHttp -> ""
        SubscriptionInstallSource.V2rayNg -> DefaultDesktopSubscriptionUserAgent

        SubscriptionInstallSource.Clash,
        SubscriptionInstallSource.ClashMeta -> DesktopClashMetaSubscriptionUserAgent

        SubscriptionInstallSource.FlClashX -> DesktopFlClashXSubscriptionUserAgent
    }

private const val DesktopClashMetaSubscriptionUserAgent = "clash.meta"
private const val DesktopFlClashXSubscriptionUserAgent = "FlClash X/v0.4.2 Platform/android"
