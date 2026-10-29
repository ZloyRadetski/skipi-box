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
import androidx.compose.ui.graphics.toArgb
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.R
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.ThemeMode
import app.modes.BackgroundStyleClassic
import app.modes.ColorModeAmoled
import app.modes.ColorModeAurora
import app.modes.ColorModeForest
import app.modes.ColorModeSakura
import app.modes.ColorModeSystem
import app.modes.ColorModeSunset
import app.modes.FontFamilyModeDefault
import app.modes.FontSizeModeSmall
import app.modes.FontWeightModeDefault
import app.modes.explicitColorModeIsDark
import app.modes.normalizeColorMode
import app.modes.resolveFontSizeScale
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.applyAppearanceSettings
import app.skipi.ui.theme.resolveBaseAppearanceColors
import ui.text.resolveFontFamily
import app.skipi.ui.theme.NamedThemePalette as SharedNamedThemePalette
import app.skipi.ui.theme.namedThemePaletteFor as sharedNamedThemePaletteFor
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

val LocalBackgroundStyle = compositionLocalOf { BackgroundStyleClassic }

/** Compatibility aliases for Android-only callers while the source of truth lives in shared UI. */
typealias AppColors = app.skipi.ui.theme.AppColors

val DefaultAppAccentColor: Color
    get() = app.skipi.ui.theme.DefaultAppAccentColor

val LocalAppColors = app.skipi.ui.theme.LocalAppColors
val LocalPopupTextStyles = app.skipi.ui.theme.LocalPopupTextStyles
val LocalPopupColors = app.skipi.ui.theme.LocalPopupColors

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
    return DefaultAppAccentColor
}

@Composable
fun AppTheme(
    colorMode: Int = ColorModeSystem,
    fontFamilyMode: Int = FontFamilyModeDefault,
    fontSizeMode: Int = FontSizeModeSmall,
    fontWeightMode: Int = FontWeightModeDefault,
    enableMaterialYou: Boolean = false,
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
    val normalizedColorMode = normalizeColorMode(colorMode)
    val namedThemePalette = remember(normalizedColorMode) { namedThemePaletteFor(normalizedColorMode) }
    val isAmoled = normalizedColorMode == ColorModeAmoled
    val resolvedDark = explicitColorModeIsDark(normalizedColorMode) ?: systemDark
    val systemAccent = remember(context) { resolveSystemAccentColor(context) }
    val effectiveKeyColor = namedThemePalette?.accent ?: keyColor ?: (
        if (enableMaterialYou) systemAccent else DefaultAppAccentColor
    )
    val usesTonalPalette = enableMaterialYou || namedThemePalette != null

    val controller = remember(normalizedColorMode, usesTonalPalette, effectiveKeyColor, resolvedDark) {
        if (usesTonalPalette) {
            ThemeController(
                if (resolvedDark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                keyColor = effectiveKeyColor,
                colorSpec = AndroidDynamicColorSpec,
                paletteStyle = AndroidDynamicPaletteStyle,
            )
        } else {
            ThemeController(if (resolvedDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        }
    }
    val baseMiuixColors = controller.currentColors()
    val baseAppColors = remember(resolvedDark, isAmoled, namedThemePalette, enableMaterialYou, baseMiuixColors.primary) {
        resolveBaseAppearanceColors(
            isDark = resolvedDark,
            amoledBlack = isAmoled,
            namedPalette = namedThemePalette,
            dynamicAccent = baseMiuixColors.primary.takeIf { enableMaterialYou },
        )
    }
    val appColors = remember(
        baseAppColors,
        enableMaterialYou,
        namedThemePalette,
        enableCustomColors,
        customAccentColor,
        customBackgroundColor,
        customSurfaceColor,
        customSurfaceVariantColor,
        customTextColor,
        customTextSecondaryColor,
    ) {
        applyAppearanceSettings(
            base = baseAppColors,
            appearance = AppearanceSettings(
                themeMode = if (namedThemePalette != null) ThemeMode.Named else ThemeMode.System,
                dynamicColors = enableMaterialYou,
                customColorsEnabled = enableCustomColors,
                customAccentColor = customAccentColor?.toAppearanceArgb(),
                customBackgroundColor = customBackgroundColor?.toAppearanceArgb(),
                customSurfaceColor = customSurfaceColor?.toAppearanceArgb(),
                customSurfaceVariantColor = customSurfaceVariantColor?.toAppearanceArgb(),
                customTextColor = customTextColor?.toAppearanceArgb(),
                customTextSecondaryColor = customTextSecondaryColor?.toAppearanceArgb(),
            ),
        )
    }

    val miuixColors = remember(baseMiuixColors, appColors, namedThemePalette, enableCustomColors, enableMaterialYou, customAccentColor, backgroundStyle) {
        val overridePrimary = namedThemePalette != null || !enableMaterialYou || (enableCustomColors && customAccentColor != null)
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
            primary = if (overridePrimary) appColors.accent else baseMiuixColors.primary,
            primaryVariant = if (overridePrimary) appColors.accent else baseMiuixColors.primaryVariant,
        )
    }

    val activity = context as? Activity
    val fontScaleFactor = remember(fontSizeMode) { resolveFontSizeScale(fontSizeMode) }

    LaunchedEffect(activity, fontScaleFactor) {
        activity?.let { act ->
            val config = act.resources.configuration
            @Suppress("DEPRECATION")
            if (config.fontScale != fontScaleFactor) {
                config.fontScale = fontScaleFactor
                val metrics = act.resources.displayMetrics
                metrics.scaledDensity = config.fontScale * metrics.density
                act.resources.updateConfiguration(config, metrics)
            }
        }
    }

    val resolvedFontFamily = remember(fontFamilyMode) { resolveFontFamily(fontFamilyMode) }

    CompositionLocalProvider(
        LocalColorMode provides colorMode,
        LocalResolvedDarkTheme provides resolvedDark,
        LocalBackgroundStyle provides backgroundStyle,
    ) {
        ProvideSkipiTheme(
            colors = appColors,
            miuixColors = miuixColors,
            fontFamily = resolvedFontFamily,
            fontSizeMode = fontSizeMode,
            fontWeightMode = fontWeightMode,
        ) {
            SystemBarAppearance(
                statusBarDark = resolvedDark,
                navigationBarDark = resolvedDark,
            )
            content()
        }
    }
}

@Composable
private fun SynchronizeSplashTheme(colorMode: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    if (view.isInEditMode) return
    LaunchedEffect(view, colorMode) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        val normalizedColorMode = normalizeColorMode(colorMode)
        val themeId = when {
            normalizedColorMode == ColorModeAmoled -> R.style.AppTheme_Starting_Amoled
            explicitColorModeIsDark(normalizedColorMode) == false -> R.style.AppTheme_Starting_Light
            explicitColorModeIsDark(normalizedColorMode) == true -> R.style.AppTheme_Starting_Dark
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

internal typealias NamedThemePalette = SharedNamedThemePalette

internal fun namedThemePaletteFor(colorMode: Int): NamedThemePalette? = when (normalizeColorMode(colorMode)) {
    ColorModeAurora -> sharedNamedThemePaletteFor("aurora")
    ColorModeSakura -> sharedNamedThemePaletteFor("sakura")
    ColorModeForest -> sharedNamedThemePaletteFor("forest")
    ColorModeSunset -> sharedNamedThemePaletteFor("sunset")
    else -> null
}

private fun Color.toAppearanceArgb(): Long = toArgb().toLong() and 0xFFFFFFFFL

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
