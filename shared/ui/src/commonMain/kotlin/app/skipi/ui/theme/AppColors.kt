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
    /** Semantic container and state roles. Defaults keep older host bridges source-compatible. */
    val primaryContainer: Color = surfaceVariant,
    val onPrimaryContainer: Color = onSurface,
    val secondary: Color = surfaceVariant,
    val onSecondary: Color = onSurface,
    val secondaryContainer: Color = surfaceVariant,
    val onSecondaryContainer: Color = onSurface,
    val error: Color = if (isDark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A),
    val onError: Color = Color.White,
    val errorContainer: Color = error.copy(alpha = if (isDark) 0.20f else 0.12f),
    val onErrorContainer: Color = error,
    val outline: Color = onSurfaceVariant.copy(alpha = 0.55f),
    val surfaceContainer: Color = surfaceVariant,
    val surfaceContainerHigh: Color = surfaceVariant,
    val disabledContainer: Color = onSurface.copy(alpha = 0.12f),
    val onDisabled: Color = onSurface.copy(alpha = 0.38f),
    val success: Color = if (isDark) Color(0xFF6BD58A) else Color(0xFF128A3C),
    val onSuccess: Color = Color.White,
    val warning: Color = if (isDark) Color(0xFFFFC857) else Color(0xFFD18A00),
    val onWarning: Color = Color.Black,
    val info: Color = accent,
    val onInfo: Color = onAccent,
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

    val colorScheme: SkipiColorScheme
        @Composable
        get() = LocalSkipiColorScheme.current

    val typography: SkipiTypography
        @Composable
        get() = LocalSkipiTypography.current

    val shapes: SkipiShapes
        @Composable
        get() = LocalSkipiShapes.current

    val spacing: SkipiSpacing
        @Composable
        get() = LocalSkipiSpacing.current

    val elevation: SkipiElevation
        @Composable
        get() = LocalSkipiElevation.current

    val motion: SkipiMotion
        @Composable
        get() = LocalSkipiMotion.current

    val windowClass: SkipiWindowClass
        @Composable
        get() = LocalSkipiWindowClass.current

    val visualProfile: SkipiVisualProfile
        @Composable
        get() = LocalSkipiVisualProfile.current

    val homeMetrics: SkipiHomeMetrics
        @Composable
        get() = LocalSkipiHomeMetrics.current
}
