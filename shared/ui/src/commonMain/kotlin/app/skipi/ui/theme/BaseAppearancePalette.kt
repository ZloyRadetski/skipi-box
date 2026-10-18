// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.ui.graphics.Color

/** Resolves the Android base palette before persisted user overrides are applied. */
fun resolveBaseAppearanceColors(
    isDark: Boolean,
    amoledBlack: Boolean = false,
    namedPalette: NamedThemePalette? = null,
    dynamicAccent: Color? = null,
): AppColors {
    val background = when {
        namedPalette != null -> namedPalette.background
        amoledBlack -> Color.Black
        isDark -> Color(0xFF16171A)
        else -> Color(0xFFF5F6F8)
    }
    val onBackground = namedPalette?.onBackground
        ?: if (isDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E)
    val surface = when {
        namedPalette != null -> namedPalette.surface
        amoledBlack -> Color(0xFF0A0A0A)
        isDark -> Color(0xFF202227)
        else -> Color.White
    }
    val onSurface = namedPalette?.onSurface
        ?: if (isDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E)
    val surfaceVariant = when {
        namedPalette != null -> namedPalette.surfaceVariant
        amoledBlack -> Color(0xFF141414)
        isDark -> Color(0xFF282A31)
        else -> Color(0xFFE8EAEE)
    }
    val onSurfaceVariant = namedPalette?.onSurfaceVariant
        ?: if (isDark) Color(0xFF9EA3AE) else Color(0xFF6B7280)

    return AppColors(
        background = background,
        onBackground = onBackground,
        accent = namedPalette?.accent ?: dynamicAccent ?: DefaultAppAccentColor,
        onAccent = namedPalette?.onAccent ?: if (isDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E),
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        isDark = isDark,
    )
}
