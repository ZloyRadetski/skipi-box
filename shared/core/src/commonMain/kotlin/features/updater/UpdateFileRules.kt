// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

/** The HTTP constants every platform's update transport shares. */
object UpdateHttp {
    const val UserAgent = "SKIPI-App"
    const val ApiAccept = "application/vnd.github.v3+json"
    const val ApiConnectTimeoutMs = 15_000
    const val ApiReadTimeoutMs = 20_000
    const val DownloadConnectTimeoutMs = 30_000
    const val DownloadReadTimeoutMs = 60_000
    const val MaxRedirects = 5

    /** The User-Agent of release checks, which also tell GitHub which build asks. */
    fun userAgent(versionName: String): String = "$UserAgent/$versionName"
}

private val Sha256Hex = Regex("[0-9a-f]{64}")

/** The lower-case 64-digit hex of a published digest (a `sha256:` prefix is allowed), or null when it is malformed. */
fun normalizeSha256(raw: String?): String? = raw
    ?.trim()
    ?.removePrefix("sha256:")
    ?.trim()
    ?.lowercase()
    ?.takeIf { Sha256Hex.matches(it) }

private const val HexDigits = "0123456789abcdef"

fun bytesToHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
    for (byte in bytes) {
        val value = byte.toInt() and 0xff
        append(HexDigits[value shr 4])
        append(HexDigits[value and 0x0f])
    }
}

/** A release file name is one plain file name: it can never point outside the download folder. */
fun isSafeAssetName(name: String): Boolean =
    name.isNotBlank() &&
        name != "." && name != ".." &&
        name.none { it == '/' || it == '\\' || it == ':' || it.code < 0x20 }
