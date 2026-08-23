// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.modes

const val ColorModeSystem = 0
const val ColorModeLight = 1
const val ColorModeDark = 2
const val ColorModeThemeSystem = 3
const val ColorModeThemeLight = 4
const val ColorModeThemeDark = 5

fun normalizeColorMode(value: Int): Int = when (value) {
    ColorModeThemeSystem -> ColorModeSystem
    ColorModeThemeLight -> ColorModeLight
    ColorModeThemeDark -> ColorModeDark
    ColorModeSystem, ColorModeLight, ColorModeDark -> value
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
