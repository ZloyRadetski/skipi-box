// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.isValidManualSubscriptionUrl
import utils.decodeUrlComponentPreservingPlus
import java.net.URI

/**
 * A subscription-install link received from another client or pasted directly
 * by the user.  Parsing is deliberately side-effect free: networking,
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

/** Source client encoded by an install link, used to retain its expected User-Agent. */
enum class DesktopSubscriptionInstallSource {
    RawHttp,
    V2rayNg,
    Clash,
    ClashMeta,
    FlClashX,
}

/** Convenience form for import integrations. */
fun String.toDesktopSubscriptionInstallUriOrNull(): DesktopSubscriptionInstallUri? =
    DesktopSubscriptionInstallUri.parseOrNull(this)

/**
 * Desktop-compatible counterpart of Android's SubscriptionInstallConfig parser.
 *
 * A manually pasted direct subscription keeps desktop's accepted HTTP/HTTPS
 * policy.  Client install links themselves may use only the known custom
 * schemes and hosts, while their embedded subscription URL must be HTTP(S).
 */
object DesktopSubscriptionInstallUriParser {
    fun parseOrNull(value: String): DesktopSubscriptionInstallUri? {
        val rawValue = value.trim()
        if (rawValue.isBlank() || rawValue.any(Char::isWhitespace)) return null

        val uri = runCatching { URI(rawValue) }.getOrNull() ?: return null
        return uri.toRawSubscriptionOrNull(rawValue)
            ?: uri.toClientInstallSubscriptionOrNull()
    }
}

private fun URI.toRawSubscriptionOrNull(rawValue: String): DesktopSubscriptionInstallUri? {
    if (!rawValue.isValidManualSubscriptionUrl()) return null
    val name = rawFragment.decodeInstallNameOrNull() ?: DefaultV2rayNgSubscriptionName
    return DesktopSubscriptionInstallUri(
        name = name,
        url = rawValue,
        userAgent = DefaultDesktopSubscriptionUserAgent,
        source = DesktopSubscriptionInstallSource.RawHttp,
    )
}

private fun URI.toClientInstallSubscriptionOrNull(): DesktopSubscriptionInstallUri? {
    val source = scheme.toDesktopSubscriptionInstallSourceOrNull() ?: return null
    if (host?.lowercase() !in DesktopSubscriptionInstallHosts) return null

    val parameters = rawQuery.parseInstallQueryParameters()
    val subscriptionUrl = parameters["url"].orEmpty().trim()
    if (!subscriptionUrl.isValidManualSubscriptionUrl()) return null

    val name = sequenceOf(
        parameters["name"],
        rawFragment,
        runCatching { URI(subscriptionUrl).rawFragment }.getOrNull(),
        source.defaultName,
    ).mapNotNull(String?::decodeInstallNameOrNull).firstOrNull() ?: return null

    return DesktopSubscriptionInstallUri(
        name = name,
        url = subscriptionUrl,
        userAgent = source.userAgent,
        source = source,
    )
}

private fun String?.parseInstallQueryParameters(): Map<String, String> {
    if (isNullOrBlank()) return emptyMap()
    return split('&').mapNotNull { entry ->
        val separatorIndex = entry.indexOf('=')
        val rawKey = if (separatorIndex >= 0) entry.substring(0, separatorIndex) else entry
        val rawValue = if (separatorIndex >= 0) entry.substring(separatorIndex + 1) else ""
        val key = rawKey.decodeUrlComponentPreservingPlus()
        if (key.isBlank()) null else key to rawValue.decodeUrlComponentPreservingPlus()
    }.toMap()
}

private fun String?.decodeInstallNameOrNull(): String? = this
    ?.decodeUrlComponentPreservingPlus()
    ?.trim()
    ?.takeIf(String::isNotBlank)

private fun String?.toDesktopSubscriptionInstallSourceOrNull(): DesktopSubscriptionInstallSource? = when {
    equals("v2rayng", ignoreCase = true) -> DesktopSubscriptionInstallSource.V2rayNg
    equals("clash", ignoreCase = true) -> DesktopSubscriptionInstallSource.Clash
    equals("clashmeta", ignoreCase = true) -> DesktopSubscriptionInstallSource.ClashMeta
    equals("flclashx", ignoreCase = true) -> DesktopSubscriptionInstallSource.FlClashX
    else -> null
}

private val DesktopSubscriptionInstallSource.userAgent: String
    get() = when (this) {
        DesktopSubscriptionInstallSource.RawHttp,
        DesktopSubscriptionInstallSource.V2rayNg -> DefaultDesktopSubscriptionUserAgent

        DesktopSubscriptionInstallSource.Clash,
        DesktopSubscriptionInstallSource.ClashMeta -> DesktopClashMetaSubscriptionUserAgent

        DesktopSubscriptionInstallSource.FlClashX -> DesktopFlClashXSubscriptionUserAgent
    }

private val DesktopSubscriptionInstallSource.defaultName: String?
    get() = when (this) {
        DesktopSubscriptionInstallSource.RawHttp -> null
        DesktopSubscriptionInstallSource.V2rayNg -> DefaultV2rayNgSubscriptionName
        DesktopSubscriptionInstallSource.Clash,
        DesktopSubscriptionInstallSource.ClashMeta,
        DesktopSubscriptionInstallSource.FlClashX -> DefaultClashSubscriptionName
    }

private val DesktopSubscriptionInstallHosts = setOf("install-config", "install-sub")
private const val DefaultV2rayNgSubscriptionName = "import sub"
private const val DefaultClashSubscriptionName = "clashsub"
private const val DesktopClashMetaSubscriptionUserAgent = "clash.meta"
private const val DesktopFlClashXSubscriptionUserAgent = "FlClash X/v0.4.2 Platform/android"
