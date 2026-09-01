// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

/**
 * Portable country-flag handling shared by Android and desktop profile adapters.
 *
 * Subscription authors often put a flag in a server remark but omit it from a
 * `[Proxy Group]` member. Matching the two names therefore must not depend on
 * UI-specific emoji handling.
 */
fun String.extractLeadingCountryFlagOrNull(): String? {
    val trimmed = trimStart()
    if (trimmed.isEmpty()) return null

    val first = trimmed.codePointAtPortable(0)
    if (first.value in RegionalIndicatorMin..RegionalIndicatorMax && first.nextIndex < trimmed.length) {
        val second = trimmed.codePointAtPortable(first.nextIndex)
        if (second.value in RegionalIndicatorMin..RegionalIndicatorMax) {
            return trimmed.substring(0, second.nextIndex)
        }
    }

    if (first.value == BlackFlag) {
        var offset = first.nextIndex
        while (offset < trimmed.length) {
            val point = trimmed.codePointAtPortable(offset)
            offset = point.nextIndex
            if (point.value == CancelTag) return trimmed.substring(0, offset)
            if (point.value !in TagMin..CancelTag) break
        }
    }

    return CommonFlags.firstOrNull(trimmed::startsWith)
}

/** Removes a leading country flag and adjacent whitespace without losing an all-flag title. */
fun String.stripLeadingCountryFlag(): String {
    val flag = extractLeadingCountryFlagOrNull() ?: return this
    val trimmed = trimStart()
    val remainder = trimmed.removePrefix(flag).trimStart()
    return remainder.ifBlank { this }
}

private data class PortableCodePoint(val value: Int, val nextIndex: Int)

private fun String.codePointAtPortable(index: Int): PortableCodePoint {
    val first = this[index].code
    if (first in HighSurrogateMin..HighSurrogateMax && index + 1 < length) {
        val second = this[index + 1].code
        if (second in LowSurrogateMin..LowSurrogateMax) {
            return PortableCodePoint(
                value = 0x1_0000 + (first - HighSurrogateMin) * 0x400 + second - LowSurrogateMin,
                nextIndex = index + 2,
            )
        }
    }
    return PortableCodePoint(value = first, nextIndex = index + 1)
}

private const val RegionalIndicatorMin = 0x1F1E6
private const val RegionalIndicatorMax = 0x1F1FF
private const val BlackFlag = 0x1F3F4
private const val TagMin = 0xE0000
private const val CancelTag = 0xE007F
private const val HighSurrogateMin = 0xD800
private const val HighSurrogateMax = 0xDBFF
private const val LowSurrogateMin = 0xDC00
private const val LowSurrogateMax = 0xDFFF
private val CommonFlags = listOf("🏳️", "🏴", "🏁", "🚩", "🎌")
