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

fun normalizeFontFamilyMode(value: Int): Int = when (value) {
    in FontFamilyModeDefault..FontFamilyModeClimateCrisis -> value
    else -> FontFamilyModeDefault
}

const val FontSizeModeSmall = 0
const val FontSizeModeDefault = 1
const val FontSizeModeMedium = 2
const val FontSizeModeLarge = 3
const val FontSizeModeExtraLarge = 4

fun normalizeFontSizeMode(value: Int): Int = when (value) {
    in FontSizeModeSmall..FontSizeModeExtraLarge -> value
    else -> FontSizeModeDefault
}

fun resolveFontSizeScale(fontSizeMode: Int): Float = when (normalizeFontSizeMode(fontSizeMode)) {
    FontSizeModeSmall -> 0.85f
    FontSizeModeDefault -> 1.0f
    FontSizeModeMedium -> 1.15f
    FontSizeModeLarge -> 1.30f
    FontSizeModeExtraLarge -> 1.45f
    else -> 1.0f
}

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

