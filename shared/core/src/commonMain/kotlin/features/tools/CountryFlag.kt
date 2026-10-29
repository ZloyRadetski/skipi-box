// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools

private const val RegionalIndicatorA = 0x1F1E6

/** Converts a two-letter country code into its flag emoji, e.g. "DE" becomes the German flag; null if invalid. */
fun countryFlagEmoji(countryCode: String?): String? {
    val code = countryCode?.trim()?.uppercase() ?: return null
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return null
    return buildString {
        appendSurrogatePair(RegionalIndicatorA + (code[0] - 'A'))
        appendSurrogatePair(RegionalIndicatorA + (code[1] - 'A'))
    }
}

private fun StringBuilder.appendSurrogatePair(codePoint: Int) {
    val offset = codePoint - 0x10000
    append((0xD800 + (offset shr 10)).toChar())
    append((0xDC00 + (offset and 0x3FF)).toChar())
}
