// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

import android.app.Activity
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.R
import app.modes.BackgroundStyleClassic
import app.modes.ColorModeAmoled
import app.modes.ColorModeDark
import app.modes.ColorModeLight
import app.modes.ColorModeSystem
import app.modes.ColorModeThemeDark
import app.modes.ColorModeThemeLight
import app.modes.ColorModeThemeSystem
import app.modes.FontFamilyModeDefault
import app.modes.FontSizeModeDefault
import app.modes.FontWeightModeDefault
import app.modes.normalizeColorMode
import app.modes.resolveFontSizeScale
import ui.text.resolveFontFamily
import ui.text.resolveFontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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

fun resolveSystemAccentColor(context: Context): Color {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val colorInt = context.getColor(android.R.color.system_accent1_500)
            if (colorInt != 0) {
                return Color(colorInt)
            }
        } catch (_: Throwable) {}
    }
    return Color(0xFF0070F3) // Чистый современный системный синий
}

@Composable
fun AppTheme(
    colorMode: Int = ColorModeSystem,
    fontFamilyMode: Int = FontFamilyModeDefault,
    fontSizeMode: Int = FontSizeModeDefault,
    fontWeightMode: Int = FontWeightModeDefault,
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
    val context = LocalContext.current
    SynchronizeSplashTheme(colorMode)
    val isAmoled = normalizeColorMode(colorMode) == ColorModeAmoled
    val resolvedDark = when (normalizeColorMode(colorMode)) {
        ColorModeLight -> false
        ColorModeDark, ColorModeAmoled -> true
        else -> systemDark
    }
    val systemAccent = remember(context) { resolveSystemAccentColor(context) }
    val effectiveKeyColor = keyColor ?: (if (enableCustomColors && customAccentColor != null) customAccentColor else if (enableMaterialYou) systemAccent else Color(0xFF0070F3))

    val controller = remember(colorMode, enableMaterialYou, effectiveKeyColor, resolvedDark) {
        if (enableMaterialYou) {
            when (colorMode) {
                ColorModeLight, ColorModeThemeLight -> ThemeController(
                    ColorSchemeMode.MonetLight,
                    keyColor = effectiveKeyColor,
                    colorSpec = AndroidDynamicColorSpec,
                    paletteStyle = AndroidDynamicPaletteStyle,
                )
                ColorModeDark, ColorModeThemeDark, ColorModeAmoled -> ThemeController(
                    ColorSchemeMode.MonetDark,
                    keyColor = effectiveKeyColor,
                    colorSpec = AndroidDynamicColorSpec,
                    paletteStyle = AndroidDynamicPaletteStyle,
                )
                else -> ThemeController(
                    if (resolvedDark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                    keyColor = effectiveKeyColor,
                    colorSpec = AndroidDynamicColorSpec,
                    paletteStyle = AndroidDynamicPaletteStyle,
                )
            }
        } else {
            when (colorMode) {
                ColorModeLight, ColorModeThemeLight -> ThemeController(
                    ColorSchemeMode.Light,
                )
                ColorModeDark, ColorModeThemeDark, ColorModeAmoled -> ThemeController(
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

        val baseBackground = when {
            isAmoled -> Color.Black
            resolvedDark -> Color(0xFF16171A)
            else -> Color(0xFFF5F6F8)
        }
        val baseOnBackground = if (resolvedDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E)
        val baseSurface = when {
            isAmoled -> Color(0xFF0A0A0A)
            resolvedDark -> Color(0xFF202227)
            else -> Color(0xFFFFFFFF)
        }
        val baseOnSurface = if (resolvedDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E)
        val baseSurfaceVariant = when {
            isAmoled -> Color(0xFF141414)
            resolvedDark -> Color(0xFF282A31)
            else -> Color(0xFFE8EAEE)
        }
        val baseOnSurfaceVariant = if (resolvedDark) Color(0xFF9EA3AE) else Color(0xFF6B7280)

        // AMOLED provides true black defaults. An explicit user palette still wins,
        // otherwise the custom background controls would appear to have no effect.
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

    val resolvedFontFamily = remember(fontFamilyMode) { resolveFontFamily(fontFamilyMode) }
    val resolvedFontWeight = remember(fontWeightMode) { resolveFontWeight(fontWeightMode) }
    val baseMiuixTextStyles = MiuixTheme.textStyles
    val miuixTextStyles = remember(baseMiuixTextStyles, resolvedFontFamily, resolvedFontWeight) {
        if (resolvedFontFamily == null && resolvedFontWeight == null) {
            baseMiuixTextStyles
        } else {
            baseMiuixTextStyles.copy(
                main = baseMiuixTextStyles.main.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                headline1 = baseMiuixTextStyles.headline1.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                headline2 = baseMiuixTextStyles.headline2.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                title1 = baseMiuixTextStyles.title1.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                title2 = baseMiuixTextStyles.title2.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                title3 = baseMiuixTextStyles.title3.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                title4 = baseMiuixTextStyles.title4.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                body1 = baseMiuixTextStyles.body1.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                body2 = baseMiuixTextStyles.body2.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                footnote1 = baseMiuixTextStyles.footnote1.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                footnote2 = baseMiuixTextStyles.footnote2.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
                button = baseMiuixTextStyles.button.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight),
            )
        }
    }

    val currentDensity = LocalDensity.current
    val fontScaleFactor = remember(fontSizeMode) { resolveFontSizeScale(fontSizeMode) }
    val scaledDensity = remember(currentDensity, fontScaleFactor) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScaleFactor,
        )
    }

    CompositionLocalProvider(
        LocalColorMode provides colorMode,
        LocalResolvedDarkTheme provides resolvedDark,
        LocalAppColors provides appColors,
        LocalBackgroundStyle provides backgroundStyle,
        LocalDensity provides scaledDensity,
    ) {
        MiuixTheme(
            colors = miuixColors,
            textStyles = miuixTextStyles,
        ) {
            SystemBarAppearance(
                statusBarDark = resolvedDark,
                navigationBarDark = resolvedDark,
            )
            content()
        }
    }
}

private fun TextStyle.applyFontAndWeight(
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    targetWeight: FontWeight?,
): TextStyle {
    val newFontFamily = fontFamily ?: this.fontFamily
    val currentWeight = this.fontWeight
    val newWeight = when {
        targetWeight == null -> currentWeight
        currentWeight != null && currentWeight >= FontWeight.SemiBold -> {
            when (targetWeight) {
                FontWeight.Light -> FontWeight.Normal
                FontWeight.Normal -> FontWeight.SemiBold
                FontWeight.Medium -> FontWeight.Bold
                FontWeight.SemiBold -> FontWeight.Bold
                FontWeight.Bold -> FontWeight.ExtraBold
                else -> targetWeight
            }
        }
        else -> targetWeight
    }
    return copy(
        fontFamily = newFontFamily,
        fontWeight = newWeight,
    )
}

@Composable
fun isInDarkTheme(): Boolean = LocalResolvedDarkTheme.current

@Composable
private fun SynchronizeSplashTheme(colorMode: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    if (view.isInEditMode) return
    LaunchedEffect(view, colorMode) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        val themeId = when (normalizeColorMode(colorMode)) {
            ColorModeLight -> R.style.AppTheme_Starting_Light
            ColorModeDark -> R.style.AppTheme_Starting_Dark
            ColorModeAmoled -> R.style.AppTheme_Starting_Amoled
            else -> Resources.ID_NULL
        }
        activity.splashScreen.setSplashScreenTheme(themeId)
    }
}

@Composable
private fun SystemBarAppearance(
    statusBarDark: Boolean,
    navigationBarDark: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = !statusBarDark
            isAppearanceLightNavigationBars = !navigationBarDark
        }
    }
}

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

private val AndroidDynamicColorSpec = ThemeColorSpec.Spec2025
private val AndroidDynamicPaletteStyle = ThemePaletteStyle.TonalSpot
