// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.ui.graphics.Color
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.ThemeMode

/**
 * A color the user picked counts only while "Custom colors" is on, the accent excepted (it works on its own).
 * Every other picked color goes through here, so turning the switch off brings back the theme's own colors
 * everywhere, not just in the palette.
 */
fun customColorIfEnabled(color: Long?, enabled: Boolean): Long? = color.takeIf { enabled }

/** Applies Android's persisted appearance color preferences to an already resolved host palette. */
fun applyAppearanceSettings(
    base: AppColors,
    appearance: AppearanceSettings,
): AppColors {
    // A picked accent works on its own, with "Custom colors" on or off; that switch gates only the other colors.
    // The exception is "dynamic colors" on a theme without a palette: the system accent is what that asks for.
    val customAccent = appearance.customAccentColor?.let(::Color)
    val accent = when {
        appearance.themeMode != ThemeMode.Named && appearance.dynamicColors -> base.accent
        else -> customAccent ?: base.accent
    }

    if (!appearance.customColorsEnabled) return base.copy(accent = accent)

    return base.copy(
        accent = accent,
        background = appearance.customBackgroundColor?.let(::Color) ?: base.background,
        surface = appearance.customSurfaceColor?.let(::Color) ?: base.surface,
        surfaceVariant = appearance.customSurfaceVariantColor?.let(::Color) ?: base.surfaceVariant,
        onBackground = appearance.customTextColor?.let(::Color) ?: base.onBackground,
        onSurface = appearance.customTextColor?.let(::Color) ?: base.onSurface,
        onAccent = appearance.customTextColor?.let(::Color) ?: base.onAccent,
        onSurfaceVariant = appearance.customTextSecondaryColor?.let(::Color) ?: base.onSurfaceVariant,
    )
}
