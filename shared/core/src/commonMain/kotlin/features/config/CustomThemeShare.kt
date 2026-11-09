// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import utils.decodeUrlSafeBase64OptionalPaddingOrNull
import utils.encodeUrlSafeBase64NoPadding
import kotlin.math.pow

/**
 * A colour theme in the form it is saved, shared and sent: the six colours of Appearance's colour
 * overrides, whether it sits on the dark or the light base, and a short name.
 *
 * Colours are ARGB longs like the rest of the appearance settings, but a theme is always opaque.
 */
data class CustomThemeShare(
    val name: String,
    val dark: Boolean,
    val accent: Long,
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val text: Long,
    val textSecondary: Long,
)

const val CustomThemeNameMaxLength = 24
const val CustomThemeLinkPrefix = "skipi://theme/"

private const val CodeVersion = 1
private const val DarkFlag = 0x01
private const val ColorCount = 6
private const val HeaderSize = 2
private const val ColorBytes = ColorCount * 3
private const val FixedSize = HeaderSize + ColorBytes + 1
private const val MaxCodeLength = 160

/** Version, flags, six RGB colours, name length, then the name in UTF-8: about 40-60 characters. */
fun CustomThemeShare.toCode(): String {
    val nameBytes = name.cleanCustomThemeName().encodeToByteArray()
    val bytes = ByteArray(FixedSize + nameBytes.size)
    bytes[0] = CodeVersion.toByte()
    bytes[1] = (if (dark) DarkFlag else 0).toByte()
    listOf(accent, background, surface, surfaceVariant, text, textSecondary).forEachIndexed { index, color ->
        val at = HeaderSize + index * 3
        bytes[at] = (color shr 16).toByte()
        bytes[at + 1] = (color shr 8).toByte()
        bytes[at + 2] = color.toByte()
    }
    bytes[HeaderSize + ColorBytes] = nameBytes.size.toByte()
    nameBytes.copyInto(bytes, FixedSize)
    return bytes.encodeUrlSafeBase64NoPadding()
}

fun CustomThemeShare.toLink(): String = CustomThemeLinkPrefix + toCode()

/** Reads a bare code or a `skipi://theme/` link; anything else, or a damaged code, is null. */
fun parseCustomThemeCodeOrNull(input: String): CustomThemeShare? {
    val code = input.trim().let { text ->
        if (text.startsWith(CustomThemeLinkPrefix, ignoreCase = true)) {
            text.substring(CustomThemeLinkPrefix.length).substringBefore('?').substringBefore('#')
        } else {
            text
        }
    }
    if (code.isEmpty() || code.length > MaxCodeLength) return null
    val bytes = code.decodeUrlSafeBase64OptionalPaddingOrNull() ?: return null
    if (bytes.size < FixedSize || bytes[0].toInt() != CodeVersion) return null
    val nameLength = bytes[HeaderSize + ColorBytes].toInt() and 0xFF
    if (bytes.size != FixedSize + nameLength) return null
    val name = runCatching {
        bytes.decodeToString(FixedSize, FixedSize + nameLength, throwOnInvalidSequence = true)
    }.getOrNull() ?: return null
    fun color(index: Int): Long {
        val at = HeaderSize + index * 3
        return 0xFF000000L or
            ((bytes[at].toLong() and 0xFF) shl 16) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            (bytes[at + 2].toLong() and 0xFF)
    }
    return CustomThemeShare(
        name = name.cleanCustomThemeName(),
        dark = bytes[1].toInt() and DarkFlag != 0,
        accent = color(0),
        background = color(1),
        surface = color(2),
        surfaceVariant = color(3),
        text = color(4),
        textSecondary = color(5),
    )
}

/** A theme's name as shown and stored: no control characters, trimmed, at most 24 characters, no split pair. */
fun String.cleanCustomThemeName(): String {
    val cleaned = filterNot { it.isISOControl() }.trim()
    if (cleaned.length <= CustomThemeNameMaxLength) return cleaned
    var end = CustomThemeNameMaxLength
    if (cleaned[end - 1].isHighSurrogate()) end--
    return cleaned.substring(0, end).trimEnd()
}

/** WCAG contrast ratio of two colours, 1.0 (identical) to 21.0 (black on white). */
fun contrastRatio(first: Long, second: Long): Double {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

enum class CustomThemePair(val minimumRatio: Double) {
    TextOnBackground(4.5),
    TextOnSurface(4.5),
    SecondaryTextOnSurface(3.0),
    TextOnAccent(3.0),
}

/** The colour pairs of this theme that are hard to read; empty when the theme is fine. */
fun CustomThemeShare.readabilityProblems(): List<CustomThemePair> = CustomThemePair.entries.filter { pair ->
    val ratio = when (pair) {
        CustomThemePair.TextOnBackground -> contrastRatio(text, background)
        CustomThemePair.TextOnSurface -> contrastRatio(text, surface)
        CustomThemePair.SecondaryTextOnSurface -> contrastRatio(textSecondary, surface)
        CustomThemePair.TextOnAccent -> contrastRatio(text, accent)
    }
    ratio < pair.minimumRatio
}

private fun relativeLuminance(color: Long): Double {
    fun channel(shift: Int): Double {
        val value = ((color shr shift) and 0xFF) / 255.0
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
