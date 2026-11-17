// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

/** The color mode a backup and the shared settings name this theme by. */
internal fun DesktopThemeMode.toColorMode(): Int = when (this) {
    DesktopThemeMode.Dark -> app.modes.ColorModeDark
    DesktopThemeMode.Amoled -> app.modes.ColorModeAmoled
    DesktopThemeMode.Light -> app.modes.ColorModeLight
    DesktopThemeMode.Aurora -> app.modes.ColorModeAurora
    DesktopThemeMode.Sakura -> app.modes.ColorModeSakura
    DesktopThemeMode.Forest -> app.modes.ColorModeForest
    DesktopThemeMode.Sunset -> app.modes.ColorModeSunset
}

/** The Desktop theme of a color mode; null for the system theme, which Desktop does not have. */
internal fun desktopThemeModeOfColorMode(colorMode: Int): DesktopThemeMode? = when (app.modes.normalizeColorMode(colorMode)) {
    app.modes.ColorModeLight -> DesktopThemeMode.Light
    app.modes.ColorModeDark -> DesktopThemeMode.Dark
    app.modes.ColorModeAmoled -> DesktopThemeMode.Amoled
    app.modes.ColorModeAurora -> DesktopThemeMode.Aurora
    app.modes.ColorModeSakura -> DesktopThemeMode.Sakura
    app.modes.ColorModeForest -> DesktopThemeMode.Forest
    app.modes.ColorModeSunset -> DesktopThemeMode.Sunset
    else -> null
}
