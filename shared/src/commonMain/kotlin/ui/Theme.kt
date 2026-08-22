// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.modes.BackgroundStyleClassic
import app.modes.ColorModeDark
import app.modes.ColorModeLight
import app.modes.ColorModeSystem
import app.modes.ColorModeThemeDark
import app.modes.ColorModeThemeLight
import app.modes.normalizeColorMode
import androidx.compose.runtime.Immutable
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

val LocalBackgroundStyle = compositionLocalOf { BackgroundStyleClassic }

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

val LocalAppColors = compositionLocalOf {
    AppColors(
        background = Color(0xFFF5F6F8),
        onBackground = Color(0xFF1B1C1E),
        accent = Color(0xFFEDE7DC),
        onAccent = Color(0xFF2C2621),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1B1C1E),
        surfaceVariant = Color(0xFFE8EAEE),
        onSurfaceVariant = Color(0xFF6B7280),
        isDark = false,
    )
}

object AppTheme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current
}

val LocalColorMode = compositionLocalOf<Int> { ColorModeSystem }
private val LocalResolvedDarkTheme = compositionLocalOf { false }

/** Platform accent color (Material You on Android, fixed brand blue elsewhere). */
@Composable
expect fun rememberSystemAccentColor(): Color

/** Platform chrome effects: splash sync + system bar appearance on Android, no-op elsewhere. */
@Composable
expect fun PlatformThemeEffects(
    statusBarDark: Boolean,
    navigationBarDark: Boolean,
    colorMode: Int,
)

@Composable
fun AppTheme(
    colorMode: Int = ColorModeSystem,
    enableMaterialYou: Boolean = true,
    keyColor: Color? = null,
    enableCustomColors: Boolean = false,
    customAccentColor: Color? = null,
    customBackgroundColor: Color? = null,
    customSurfaceColor: Color? = null,
    customSurfaceVariantColor: Color? = null,
    customTextColor: Color? = null,
    customTextSecondaryColor: Color? = null,
    backgroundStyle: Int = BackgroundStyleClassic,
    systemDark: Boolean,
    content: @Composable () -> Unit,
) {
    val resolvedDark = when (normalizeColorMode(colorMode)) {
        ColorModeLight -> false
        ColorModeDark -> true
        else -> systemDark
    }
    val systemAccent = rememberSystemAccentColor()
    val effectiveKeyColor = keyColor ?: (if (enableCustomColors && customAccentColor != null) customAccentColor else if (enableMaterialYou) systemAccent else Color(0xFF0070F3))

    val controller = remember(colorMode, enableMaterialYou, effectiveKeyColor, resolvedDark) {
        if (enableMaterialYou) {
            when (colorMode) {
                ColorModeLight, ColorModeThemeLight -> ThemeController(
                    ColorSchemeMode.MonetLight,
                    keyColor = effectiveKeyColor,
                    colorSpec = DynamicColorSpec,
                    paletteStyle = DynamicPaletteStyle,
                )
                ColorModeDark, ColorModeThemeDark -> ThemeController(
                    ColorSchemeMode.MonetDark,
                    keyColor = effectiveKeyColor,
                    colorSpec = DynamicColorSpec,
                    paletteStyle = DynamicPaletteStyle,
                )
                else -> ThemeController(
                    if (resolvedDark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                    keyColor = effectiveKeyColor,
                    colorSpec = DynamicColorSpec,
                    paletteStyle = DynamicPaletteStyle,
                )
            }
        } else {
            when (colorMode) {
                ColorModeLight, ColorModeThemeLight -> ThemeController(
                    ColorSchemeMode.Light,
                )
                ColorModeDark, ColorModeThemeDark -> ThemeController(
                    ColorSchemeMode.Dark,
                )
                else -> ThemeController(
                    if (resolvedDark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                )
            }
        }
    }

    val appColors = remember(
        resolvedDark,
        colorMode,
        enableMaterialYou,
        effectiveKeyColor,
        enableCustomColors,
        customAccentColor,
        customBackgroundColor,
        customSurfaceColor,
        customSurfaceVariantColor,
        customTextColor,
        customTextSecondaryColor,
        backgroundStyle,
    ) {
        val resolvedAccent = if (enableMaterialYou) {
            if (resolvedDark) {
                Color(
                    red = (0.08f + effectiveKeyColor.red * 0.18f).coerceIn(0f, 1f),
                    green = (0.08f + effectiveKeyColor.green * 0.18f).coerceIn(0f, 1f),
                    blue = (0.08f + effectiveKeyColor.blue * 0.18f).coerceIn(0f, 1f),
                    alpha = 1.0f,
                )
            } else {
                Color(
                    red = (0.90f * 0.85f + effectiveKeyColor.red * 0.15f).coerceIn(0f, 1f),
                    green = (0.90f * 0.85f + effectiveKeyColor.green * 0.15f).coerceIn(0f, 1f),
                    blue = (0.90f * 0.85f + effectiveKeyColor.blue * 0.15f).coerceIn(0f, 1f),
                    alpha = 1.0f,
                )
            }
        } else {
            if (resolvedDark) Color(0xFF282A31) else Color(0xFFE8EAEE)
        }

        val baseBackground = if (resolvedDark) Color(0xFF16171A) else Color(0xFFF5F6F8)
        val baseOnBackground = if (resolvedDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E)
        val baseSurface = if (resolvedDark) Color(0xFF202227) else Color(0xFFFFFFFF)
        val baseOnSurface = if (resolvedDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E)
        val baseSurfaceVariant = if (resolvedDark) Color(0xFF282A31) else Color(0xFFE8EAEE)
        val baseOnSurfaceVariant = if (resolvedDark) Color(0xFF9EA3AE) else Color(0xFF6B7280)

        val finalBackground = if (enableCustomColors && customBackgroundColor != null) customBackgroundColor else baseBackground
        val finalSurface = if (enableCustomColors && customSurfaceColor != null) customSurfaceColor else baseSurface
        val finalSurfaceVariant = if (enableCustomColors && customSurfaceVariantColor != null) customSurfaceVariantColor else baseSurfaceVariant
        val finalAccent = if (enableCustomColors && customAccentColor != null) customAccentColor else resolvedAccent
        val finalOnBackground = if (enableCustomColors && customTextColor != null) customTextColor else baseOnBackground
        val finalOnSurface = if (enableCustomColors && customTextColor != null) customTextColor else baseOnSurface
        val finalOnAccent = if (enableCustomColors && customTextColor != null) customTextColor else (if (resolvedDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E))
        val finalOnSurfaceVariant = if (enableCustomColors && customTextSecondaryColor != null) customTextSecondaryColor else baseOnSurfaceVariant

        AppColors(
            background = finalBackground,
            onBackground = finalOnBackground,
            accent = finalAccent,
            onAccent = finalOnAccent,
            surface = finalSurface,
            onSurface = finalOnSurface,
            surfaceVariant = finalSurfaceVariant,
            onSurfaceVariant = finalOnSurfaceVariant,
            isDark = resolvedDark,
        )
    }

    val baseMiuixColors = controller.currentColors()
    val miuixColors = remember(baseMiuixColors, appColors, enableCustomColors, customAccentColor, backgroundStyle) {
        baseMiuixColors.copy(
            surface = appColors.surface,
            surfaceContainer = appColors.surface,
            surfaceContainerHigh = appColors.surface,
            surfaceContainerHighest = appColors.surface,
            background = appColors.surface,
            surfaceVariant = appColors.surfaceVariant,
            onSurface = appColors.onSurface,
            onBackground = appColors.onBackground,
            onSurfaceContainer = appColors.onSurface,
            onSurfaceVariantSummary = appColors.onSurfaceVariant,
            onBackgroundVariant = appColors.onSurfaceVariant,
            primary = if (enableCustomColors && customAccentColor != null) appColors.accent else baseMiuixColors.primary,
            primaryVariant = if (enableCustomColors && customAccentColor != null) appColors.accent else baseMiuixColors.primaryVariant,
        )
    }

    CompositionLocalProvider(
        LocalColorMode provides colorMode,
        LocalResolvedDarkTheme provides resolvedDark,
        LocalAppColors provides appColors,
        LocalBackgroundStyle provides backgroundStyle,
    ) {
        MiuixTheme(colors = miuixColors) {
            PlatformThemeEffects(
                statusBarDark = resolvedDark,
                navigationBarDark = systemDark,
                colorMode = colorMode,
            )
            content()
        }
    }
}

@Composable
fun isInDarkTheme(): Boolean = LocalResolvedDarkTheme.current

val KeyColors: List<Color> = listOf(
    Color(0xFF3482FF),
    Color(0xFF36D167),
    Color(0xFF7C4DFF),
    Color(0xFFFFB21D),
    Color(0xFFFF5722),
    Color(0xFFE91E63),
    Color(0xFF00BCD4),
)

fun keyColorFor(index: Int, customSeed: Long? = null): Color? {
    if (index == KeyColors.size + 1 && customSeed != null) {
        return Color(customSeed)
    }
    return if (index <= 0) null else KeyColors.getOrNull(index - 1)
}

fun keyColorFor(index: Int): Color? = keyColorFor(index, null)

private val DynamicColorSpec = ThemeColorSpec.Spec2025
private val DynamicPaletteStyle = ThemePaletteStyle.TonalSpot
