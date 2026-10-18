// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.ui.graphics.Color
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.ThemeMode

/** Applies Android's persisted appearance color preferences to an already resolved host palette. */
fun applyAppearanceSettings(
    base: AppColors,
    appearance: AppearanceSettings,
): AppColors {
    val customAccent = appearance.customAccentColor?.let(::Color)
    val accent = when {
        appearance.customColorsEnabled && customAccent != null -> customAccent
        appearance.themeMode == ThemeMode.Named -> base.accent
        !appearance.dynamicColors && customAccent != null -> customAccent
        !appearance.dynamicColors -> DefaultAppAccentColor
        else -> base.accent
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
