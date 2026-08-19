// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import android.net.Uri
import android.util.Base64
import features.subscription.runtime.SubscriptionFetchResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Metadata attached to a subscription response by common proxy subscription servers. */
internal data class SubscriptionMetadata(
    val profileTitle: String? = null,
    val announce: String? = null,
    val userInfoReceived: Boolean = false,
    val trafficUploadBytes: Long = -1L,
    val trafficDownloadBytes: Long = -1L,
    val trafficTotalBytes: Long = -1L,
    val trafficExpireAtSeconds: Long = -1L,
    /** Server-supplied interval in hours, used unless the user changes it manually. */
    val profileUpdateIntervalHours: String? = null,
)

internal fun SubscriptionFetchResponse.subscriptionMetadata(): SubscriptionMetadata {
    val normalizedHeaders = headers.mapKeys { (name, _) -> name.lowercase() }
    val userInfo = normalizedHeaders[SubscriptionUserInfoHeader]
        ?: normalizedHeaders[SubscriptionUserInfoHeaderWithHash]
    val values = userInfo?.split(';')
        ?.associate { entry ->
            val (key, value) = entry.trim().split('=', limit = 2).let { parts ->
                parts.firstOrNull().orEmpty().trim().lowercase() to parts.getOrElse(1) { "" }.trim()
            }
            key to value
        }
        .orEmpty()
    return SubscriptionMetadata(
        profileTitle = normalizedHeaders[ProfileTitleHeader]?.decodeSubscriptionHeaderValue(),
        announce = normalizedHeaders[AnnounceHeader]?.decodeSubscriptionHeaderValue(),
        userInfoReceived = userInfo != null,
        trafficUploadBytes = values["upload"].toSubscriptionLong(),
        trafficDownloadBytes = values["download"].toSubscriptionLong(),
        trafficTotalBytes = values["total"].toSubscriptionLong(),
        trafficExpireAtSeconds = values["expire"].toSubscriptionLong(),
        profileUpdateIntervalHours = normalizedHeaders[ProfileUpdateIntervalHeader]
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.toString(),
    )
}

private fun String?.toSubscriptionLong(): Long {
    return this?.toLongOrNull()?.takeIf { it >= 0L } ?: -1L
}

private fun String.decodeSubscriptionHeaderValue(): String {
    val raw = trim()
    if (raw.isEmpty()) return ""
    val unquoted = raw.removeSurrounding("\"")
    val explicitlyEncoded = unquoted.startsWith("base64:", ignoreCase = true) ||
        unquoted.startsWith("base64,", ignoreCase = true)
    // Plain-text profile names commonly contain spaces.  Removing whitespace first made
    // values such as "TORVALDS VPN" look like Base64 and displayed them incorrectly.
    if (!explicitlyEncoded && unquoted.any(Char::isWhitespace)) return raw
    val encoded = unquoted
        .removeBase64Prefix()
        .let(Uri::decode)
        .filterNot(Char::isWhitespace)
    if (!encoded.isBase64HeaderValue()) return raw
    val padded = encoded.padEnd((encoded.length + 3) / 4 * 4, '=')
    return listOf(Base64.DEFAULT, Base64.URL_SAFE or Base64.NO_WRAP)
        .firstNotNullOfOrNull { flags ->
            runCatching {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.decode(padded, flags)))
                    .toString()
            }.getOrNull()?.takeIf(String::isReadableSubscriptionHeaderText)
        }
        ?: raw
}

private fun String.removeBase64Prefix(): String {
    return when {
        startsWith("base64:", ignoreCase = true) -> substringAfter(':')
        startsWith("base64,", ignoreCase = true) -> substringAfter(',')
        else -> this
    }
}

private fun String.isBase64HeaderValue(): Boolean {
    if (length % 4 == 1) return false
    return all { character ->
        character.isLetterOrDigit() || character in "+/=_-"
    }
}

private fun String.isReadableSubscriptionHeaderText(): Boolean {
    return all { character ->
        !character.isISOControl() || character in "\n\r\t"
    }
}

private const val ProfileTitleHeader = "profile-title"
private const val AnnounceHeader = "announce"
private const val ProfileUpdateIntervalHeader = "profile-update-interval"
private const val SubscriptionUserInfoHeader = "subscription-userinfo"
private const val SubscriptionUserInfoHeaderWithHash = "#subscription-userinfo"
