// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles

@Immutable
data class AppColors(
    val background: Color,
    val onBackground: Color,
    val accent: Color,
    val onAccent: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val isDark: Boolean,
)

val DefaultAppAccentColor = Color(0xFF4B6078)

val LocalAppColors = compositionLocalOf {
    AppColors(
        background = Color(0xFFF5F6F8),
        onBackground = Color(0xFF1B1C1E),
        accent = DefaultAppAccentColor,
        onAccent = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1B1C1E),
        surfaceVariant = Color(0xFFE8EAEE),
        onSurfaceVariant = Color(0xFF6B7280),
        isDark = false,
    )
}

val LocalPopupTextStyles = compositionLocalOf<TextStyles?> { null }
val LocalPopupColors = compositionLocalOf<Colors?> { null }

object SkipiTheme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current
}
