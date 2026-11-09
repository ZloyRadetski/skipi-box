// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.ui.graphics.toArgb
import app.skipi.ui.theme.AppColors
import features.config.CustomThemeShare
import features.config.toCustomThemes
import features.config.toStorage

/**
 * Applies a saved theme through the colour overrides every host already draws: custom colours on, the six
 * colours set, and the light or dark base the theme was made on. Status, ping and protocol colours stay as
 * the user set them.
 */
fun AppearanceSettingsState.withCustomTheme(theme: CustomThemeShare): AppearanceSettingsState = copy(
    enableCustomColors = true,
    colorMode = theme.baseColorMode(),
    customAccentColor = theme.accent,
    customBackgroundColor = theme.background,
    customSurfaceColor = theme.surface,
    customSurfaceVariantColor = theme.surfaceVariant,
    customTextColor = theme.text,
    customTextSecondaryColor = theme.textSecondary,
)

/** Whether this theme is what the overrides show right now, whatever it is called. */
fun AppearanceSettingsState.isShowing(theme: CustomThemeShare): Boolean = enableCustomColors &&
    colorMode == theme.baseColorMode() &&
    customAccentColor == theme.accent &&
    customBackgroundColor == theme.background &&
    customSurfaceColor == theme.surface &&
    customSurfaceVariantColor == theme.surfaceVariant &&
    customTextColor == theme.text &&
    customTextSecondaryColor == theme.textSecondary

fun AppearanceSettingsState.savedThemes(): List<CustomThemeShare> = customThemes.toCustomThemes()

fun AppearanceSettingsState.withCustomThemes(themes: List<CustomThemeShare>): AppearanceSettingsState =
    copy(customThemes = themes.toStorage())

/** The colours the screen shows right now as a theme: what the user sees is what gets saved. */
fun AppColors.toCustomTheme(name: String): CustomThemeShare = CustomThemeShare(
    name = name,
    dark = isDark,
    accent = accent.toArgbLong(),
    background = background.toArgbLong(),
    surface = surface.toArgbLong(),
    surfaceVariant = surfaceVariant.toArgbLong(),
    text = onSurface.toArgbLong(),
    textSecondary = onSurfaceVariant.toArgbLong(),
)

private fun CustomThemeShare.baseColorMode(): Int =
    if (dark) AppearanceSettingValues.ColorModeDark else AppearanceSettingValues.ColorModeLight

private fun androidx.compose.ui.graphics.Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
