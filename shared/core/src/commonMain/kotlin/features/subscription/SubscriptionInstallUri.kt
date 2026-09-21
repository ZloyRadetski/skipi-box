// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import io.ktor.http.Url
import utils.decodeUrlComponentPreservingPlus

/**
 * A side-effect-free subscription-install link shared by Android and desktop.
 * Platform code chooses the User-Agent and performs the actual install.
 */
data class SubscriptionInstallUri(
    val name: String,
    val url: String,
    val source: SubscriptionInstallSource,
)

/** Client format that supplied a [SubscriptionInstallUri]. */
enum class SubscriptionInstallSource {
    RawHttp,
    V2rayNg,
    Clash,
    ClashMeta,
    FlClashX,
}

/** Which HTTP schemes a platform accepts for a subscription URL. */
enum class SubscriptionInstallUrlPolicy {
    HttpsOnly,
    HttpOrHttps,
}

/**
 * Platform policy for direct URLs and URLs embedded in a client install link.
 * Android accepts only HTTPS links received from outside the app; desktop also
 * permits a manually pasted HTTP URL for compatibility with its existing UI.
 */
data class SubscriptionInstallUriParsingPolicy(
    val directUrlPolicy: SubscriptionInstallUrlPolicy,
    val embeddedUrlPolicy: SubscriptionInstallUrlPolicy,
)

/**
 * Parses a direct subscription URL or a supported client install link without
 * performing networking or persistence.
 */
fun parseSubscriptionInstallUriOrNull(
    value: String,
    policy: SubscriptionInstallUriParsingPolicy,
): SubscriptionInstallUri? {
    val rawValue = value.trim()
    if (rawValue.isBlank() || rawValue.any(Char::isWhitespace)) return null

    val url = runCatching { Url(rawValue) }.getOrNull() ?: return null
    return url.toDirectSubscriptionInstallUriOrNull(rawValue, policy.directUrlPolicy)
        ?: url.toClientSubscriptionInstallUriOrNull(policy.embeddedUrlPolicy)
}

/** True only for a recognized custom client install URI, not a direct URL. */
fun String.isSubscriptionInstallUri(): Boolean {
    val rawValue = trim()
    if (rawValue.isBlank() || rawValue.any(Char::isWhitespace)) return false
    val url = runCatching { Url(rawValue) }.getOrNull() ?: return false
    return url.toSubscriptionInstallSourceOrNull() != null &&
        url.host.lowercase() in SubscriptionInstallHosts
}

private fun Url.toDirectSubscriptionInstallUriOrNull(
    rawValue: String,
    policy: SubscriptionInstallUrlPolicy,
): SubscriptionInstallUri? {
    if (!rawValue.isAllowedSubscriptionUrl(policy)) return null
    val name = fragment.decodeInstallNameOrNull() ?: DefaultV2rayNgSubscriptionName
    return SubscriptionInstallUri(
        name = name,
        url = rawValue,
        source = SubscriptionInstallSource.RawHttp,
    )
}

private fun Url.toClientSubscriptionInstallUriOrNull(
    embeddedUrlPolicy: SubscriptionInstallUrlPolicy,
): SubscriptionInstallUri? {
    val source = toSubscriptionInstallSourceOrNull() ?: return null
    if (host.lowercase() !in SubscriptionInstallHosts) return null

    val subscriptionUrl = parameters["url"]?.trim().orEmpty()
    if (!subscriptionUrl.isAllowedSubscriptionUrl(embeddedUrlPolicy)) return null

    val name = listOfNotNull(
        parameters["name"],
        fragment,
        subscriptionUrl.toSubscriptionUrlFragmentOrNull(),
        source.defaultName,
    ).firstNotNullOfOrNull(String::decodeInstallNameOrNull) ?: return null

    return SubscriptionInstallUri(
        name = name,
        url = subscriptionUrl,
        source = source,
    )
}

private fun Url.toSubscriptionInstallSourceOrNull(): SubscriptionInstallSource? = when {
    protocol.name.equals("v2rayng", ignoreCase = true) -> SubscriptionInstallSource.V2rayNg
    protocol.name.equals("clash", ignoreCase = true) -> SubscriptionInstallSource.Clash
    protocol.name.equals("clashmeta", ignoreCase = true) -> SubscriptionInstallSource.ClashMeta
    protocol.name.equals("flclashx", ignoreCase = true) -> SubscriptionInstallSource.FlClashX
    else -> null
}

private fun String.isAllowedSubscriptionUrl(policy: SubscriptionInstallUrlPolicy): Boolean = when (policy) {
    SubscriptionInstallUrlPolicy.HttpsOnly -> isValidSubscriptionInstallUrl()
    SubscriptionInstallUrlPolicy.HttpOrHttps -> isValidManualSubscriptionUrl()
}

private fun String.toSubscriptionUrlFragmentOrNull(): String? {
    return runCatching { Url(this).fragment }.getOrNull()
}

private fun String?.decodeInstallNameOrNull(): String? = this
    ?.decodeUrlComponentPreservingPlus()
    ?.trim()
    ?.takeIf(String::isNotBlank)

private val SubscriptionInstallSource.defaultName: String?
    get() = when (this) {
        SubscriptionInstallSource.RawHttp -> null
        SubscriptionInstallSource.V2rayNg -> DefaultV2rayNgSubscriptionName
        SubscriptionInstallSource.Clash,
        SubscriptionInstallSource.ClashMeta,
        SubscriptionInstallSource.FlClashX -> DefaultClashSubscriptionName
    }

private val SubscriptionInstallHosts = setOf("install-config", "install-sub")
private const val DefaultV2rayNgSubscriptionName = "import sub"
private const val DefaultClashSubscriptionName = "clashsub"
