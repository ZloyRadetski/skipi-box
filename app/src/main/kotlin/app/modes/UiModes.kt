// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.modes

const val ColorModeSystem = 0
const val ColorModeLight = 1
const val ColorModeDark = 2
const val ColorModeThemeSystem = 3
const val ColorModeThemeLight = 4
const val ColorModeThemeDark = 5
const val ColorModeAmoled = 6

fun normalizeColorMode(value: Int): Int = when (value) {
    ColorModeThemeSystem -> ColorModeSystem
    ColorModeThemeLight -> ColorModeLight
    ColorModeThemeDark -> ColorModeDark
    ColorModeSystem, ColorModeLight, ColorModeDark, ColorModeAmoled -> value
    else -> ColorModeSystem
}

const val LanguageModeSystem = 0
const val LanguageModeEnglish = 1
const val LanguageModeChinese = 2
const val LanguageModeRussian = 3
const val LanguageModePersian = 4

fun normalizeLanguageMode(value: Int): Int = when (value) {
    in LanguageModeSystem..LanguageModePersian -> value
    else -> LanguageModeSystem
}

const val AppIconDefault = 0
const val AppIconDark = 1
const val AppIconLight = 2
const val AppIconMonet = 3
const val AppIconCyber = 4
const val AppIconSunset = 5
const val AppIconNordic = 6
const val AppIconEmerald = 7
const val AppIconStealth = 8

fun normalizeAppIcon(value: Int): Int = when (value) {
    in AppIconDefault..AppIconStealth -> value
    else -> AppIconDefault
}

const val BottomBarSizeSmall = 0
const val BottomBarSizeMedium = 1
const val BottomBarSizeLarge = 2

fun normalizeBottomBarSize(value: Int): Int = when (value) {
    in BottomBarSizeSmall..BottomBarSizeLarge -> value
    else -> BottomBarSizeLarge
}

const val FontFamilyModeDefault = 0
const val FontFamilyModeInter = 1
const val FontFamilyModeGolosText = 2
const val FontFamilyModeManrope = 3
const val FontFamilyModeJetBrainsMono = 4
const val FontFamilyModeClimateCrisis = 5
const val FontFamilyModeUnbounded = 6
const val FontFamilyModeOnest = 7

fun normalizeFontFamilyMode(value: Int): Int = when (value) {
    in FontFamilyModeDefault..FontFamilyModeOnest -> value
    else -> FontFamilyModeDefault
}

/** Font scale values are persisted as percentages in five-percent increments. */
const val FontSizeModeTiny = 55
const val FontSizeModeExtraSmall = 65
const val FontSizeModeVerySmall = 75
const val FontSizeModeSmall = 85
const val FontSizeModeDefault = 100
const val FontSizeModeMedium = 115
const val FontSizeModeLarge = 130
const val FontSizeModeExtraLarge = 145
const val FontSizeModeStepPercent = 5

/**
 * Keeps values from releases that stored one of eight font-size option indices
 * (0 through 7), while accepting the current five-percent slider values.
 */
fun normalizeFontSizeMode(value: Int): Int = when {
    value in LegacyFontSizeModeTiny..LegacyFontSizeModeExtraLarge -> legacyFontSizeModePercent(value)
    value in FontSizeModeTiny..FontSizeModeExtraLarge &&
        (value - FontSizeModeTiny) % FontSizeModeStepPercent == 0 -> value

    else -> FontSizeModeDefault
}

fun resolveFontSizeScale(fontSizeMode: Int): Float = normalizeFontSizeMode(fontSizeMode) / 100f

private fun legacyFontSizeModePercent(value: Int): Int = when (value) {
    LegacyFontSizeModeTiny -> FontSizeModeTiny
    LegacyFontSizeModeExtraSmall -> FontSizeModeExtraSmall
    LegacyFontSizeModeVerySmall -> FontSizeModeVerySmall
    LegacyFontSizeModeSmall -> FontSizeModeSmall
    LegacyFontSizeModeDefault -> FontSizeModeDefault
    LegacyFontSizeModeMedium -> FontSizeModeMedium
    LegacyFontSizeModeLarge -> FontSizeModeLarge
    LegacyFontSizeModeExtraLarge -> FontSizeModeExtraLarge
    else -> FontSizeModeDefault
}

private const val LegacyFontSizeModeTiny = 0
private const val LegacyFontSizeModeExtraSmall = 1
private const val LegacyFontSizeModeVerySmall = 2
private const val LegacyFontSizeModeSmall = 3
private const val LegacyFontSizeModeDefault = 4
private const val LegacyFontSizeModeMedium = 5
private const val LegacyFontSizeModeLarge = 6
private const val LegacyFontSizeModeExtraLarge = 7

const val FontWeightModeDefault = 0
const val FontWeightModeLight = 1
const val FontWeightModeNormal = 2
const val FontWeightModeMedium = 3
const val FontWeightModeSemiBold = 4
const val FontWeightModeBold = 5

fun normalizeFontWeightMode(value: Int): Int = when (value) {
    in FontWeightModeDefault..FontWeightModeBold -> value
    else -> FontWeightModeDefault
}

fun isFontWeightSupported(fontFamilyMode: Int): Boolean =
    normalizeFontFamilyMode(fontFamilyMode) != FontFamilyModeClimateCrisis

