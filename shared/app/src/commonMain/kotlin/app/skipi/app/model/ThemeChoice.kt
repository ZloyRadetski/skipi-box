// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.model

/** How a color mode is stored in [AppearanceSettings]: a mode, the AMOLED switch and the variant of a named theme. */
data class ThemeChoice(
    val themeMode: ThemeMode,
    val amoledBlack: Boolean,
    val themeVariant: String?,
)

fun themeChoiceOfColorMode(colorMode: Int): ThemeChoice = when (colorMode) {
    app.modes.ColorModeLight -> ThemeChoice(ThemeMode.Light, false, null)
    app.modes.ColorModeDark -> ThemeChoice(ThemeMode.Dark, false, null)
    app.modes.ColorModeAmoled -> ThemeChoice(ThemeMode.Dark, true, null)
    app.modes.ColorModeAurora -> ThemeChoice(ThemeMode.Named, false, "aurora")
    app.modes.ColorModeSakura -> ThemeChoice(ThemeMode.Named, false, "sakura")
    app.modes.ColorModeForest -> ThemeChoice(ThemeMode.Named, false, "forest")
    app.modes.ColorModeSunset -> ThemeChoice(ThemeMode.Named, false, "sunset")
    else -> ThemeChoice(ThemeMode.System, false, null)
}

/** The color mode of stored appearance settings; a named theme with no known variant keeps [fallback]. */
fun colorModeOfAppearance(appearance: AppearanceSettings, fallback: Int): Int = when {
    appearance.amoledBlack -> app.modes.ColorModeAmoled
    appearance.themeMode == ThemeMode.Light -> app.modes.ColorModeLight
    appearance.themeMode == ThemeMode.Dark -> app.modes.ColorModeDark
    appearance.themeMode == ThemeMode.System -> app.modes.ColorModeSystem
    else -> when (appearance.themeVariant) {
        "aurora" -> app.modes.ColorModeAurora
        "sakura" -> app.modes.ColorModeSakura
        "forest" -> app.modes.ColorModeForest
        "sunset" -> app.modes.ColorModeSunset
        else -> fallback
    }
}
