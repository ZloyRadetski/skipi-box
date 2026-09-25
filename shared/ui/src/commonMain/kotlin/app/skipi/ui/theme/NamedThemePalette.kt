// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class NamedThemePalette(
    val background: Color,
    val onBackground: Color,
    val accent: Color,
    val onAccent: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
)

fun namedThemePaletteFor(themeName: String): NamedThemePalette? = when (themeName.lowercase()) {
    "aurora" -> NamedThemePalette(
        background = Color(0xFF081427),
        onBackground = Color(0xFFE7F1FF),
        accent = Color(0xFF3B82F6),
        onAccent = Color.White,
        surface = Color(0xFF102443),
        onSurface = Color(0xFFE7F1FF),
        surfaceVariant = Color(0xFF18345E),
        onSurfaceVariant = Color(0xFFB2C8EA),
    )
    "sakura" -> NamedThemePalette(
        background = Color(0xFFFFF7FB),
        onBackground = Color(0xFF351321),
        accent = Color(0xFFB93872),
        onAccent = Color.White,
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF351321),
        surfaceVariant = Color(0xFFF7E4EE),
        onSurfaceVariant = Color(0xFF806171),
    )
    "forest" -> NamedThemePalette(
        background = Color(0xFF061B15),
        onBackground = Color(0xFFE6FFF4),
        accent = Color(0xFF16835A),
        onAccent = Color.White,
        surface = Color(0xFF0C2A20),
        onSurface = Color(0xFFE6FFF4),
        surfaceVariant = Color(0xFF164235),
        onSurfaceVariant = Color(0xFFA9D7C0),
    )
    "sunset" -> NamedThemePalette(
        background = Color(0xFF211015),
        onBackground = Color(0xFFFFF0ED),
        accent = Color(0xFFC85048),
        onAccent = Color.White,
        surface = Color(0xFF32161D),
        onSurface = Color(0xFFFFF0ED),
        surfaceVariant = Color(0xFF4A2029),
        onSurfaceVariant = Color(0xFFE9B8B1),
    )
    else -> null
}
