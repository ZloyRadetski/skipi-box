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
import ui.text.resolveFontFamily
import ui.text.resolveFontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isSpecified
import androidx.compose.runtime.Immutable
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    val activeAccentColor = when {
        enableCustomColors && customAccentColor != null -> customAccentColor
        namedThemePalette != null -> namedThemePalette.accent
        !enableMaterialYou && customAccentColor != null -> customAccentColor
        !enableMaterialYou -> DefaultAppAccentColor
        else -> baseMiuixColors.primary
    }

    val appColors = remember(
        resolvedDark,
        colorMode,
        enableMaterialYou,
        namedThemePalette,
        activeAccentColor,
        enableCustomColors,
        customBackgroundColor,
        customSurfaceColor,
        customSurfaceVariantColor,
        customTextColor,
        customTextSecondaryColor,
        backgroundStyle,
    ) {
        val baseBackground = when {
            namedThemePalette != null -> namedThemePalette.background
            isAmoled -> Color.Black
            resolvedDark -> Color(0xFF16171A)
            else -> Color(0xFFF5F6F8)
        }
        val baseOnBackground = namedThemePalette?.onBackground ?: if (resolvedDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E)
        val baseSurface = when {
            namedThemePalette != null -> namedThemePalette.surface
            isAmoled -> Color(0xFF0A0A0A)
            resolvedDark -> Color(0xFF202227)
            else -> Color(0xFFFFFFFF)
        }
        val baseOnSurface = namedThemePalette?.onSurface ?: if (resolvedDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E)
        val baseSurfaceVariant = when {
            namedThemePalette != null -> namedThemePalette.surfaceVariant
            isAmoled -> Color(0xFF141414)
            resolvedDark -> Color(0xFF282A31)
            else -> Color(0xFFE8EAEE)
        }
        val baseOnSurfaceVariant = namedThemePalette?.onSurfaceVariant ?: if (resolvedDark) Color(0xFF9EA3AE) else Color(0xFF6B7280)
        val baseOnAccent = namedThemePalette?.onAccent ?: if (resolvedDark) Color(0xFFEDEDEF) else Color(0xFF1B1C1E)

        // AMOLED provides true black defaults. An explicit user palette still wins,
        // otherwise the custom background controls would appear to have no effect.
        val finalBackground = if (enableCustomColors && customBackgroundColor != null) customBackgroundColor else baseBackground
        val finalSurface = if (enableCustomColors && customSurfaceColor != null) customSurfaceColor else baseSurface
        val finalSurfaceVariant = if (enableCustomColors && customSurfaceVariantColor != null) customSurfaceVariantColor else baseSurfaceVariant
        val finalAccent = activeAccentColor
        val finalOnBackground = if (enableCustomColors && customTextColor != null) customTextColor else baseOnBackground
        val finalOnSurface = if (enableCustomColors && customTextColor != null) customTextColor else baseOnSurface
        val finalOnAccent = if (enableCustomColors && customTextColor != null) customTextColor else baseOnAccent
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

    val miuixColors = remember(baseMiuixColors, appColors, namedThemePalette, enableCustomColors, enableMaterialYou, activeAccentColor, backgroundStyle) {
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
            primary = if (overridePrimary) activeAccentColor else baseMiuixColors.primary,
            primaryVariant = if (overridePrimary) activeAccentColor else baseMiuixColors.primaryVariant,
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
    val resolvedFontWeight = remember(fontWeightMode) { resolveFontWeight(fontWeightMode) }
    val fontWeightShift = resolvedFontWeight?.weight?.minus(FontWeight.Normal.weight) ?: 0
    val baseMiuixTextStyles = MiuixTheme.textStyles
    val miuixTextStyles = remember(baseMiuixTextStyles, resolvedFontFamily, resolvedFontWeight, appColors.onSurface) {
        baseMiuixTextStyles.copy(
            main = baseMiuixTextStyles.main.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            headline1 = baseMiuixTextStyles.headline1.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            headline2 = baseMiuixTextStyles.headline2.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            title1 = baseMiuixTextStyles.title1.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            title2 = baseMiuixTextStyles.title2.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            title3 = baseMiuixTextStyles.title3.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            title4 = baseMiuixTextStyles.title4.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            body1 = baseMiuixTextStyles.body1.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            body2 = baseMiuixTextStyles.body2.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            footnote1 = baseMiuixTextStyles.footnote1.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            footnote2 = baseMiuixTextStyles.footnote2.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
            button = baseMiuixTextStyles.button.applyFontAndWeight(resolvedFontFamily, resolvedFontWeight).copy(color = appColors.onSurface),
        )
    }
    val popupMiuixTextStyles = remember(miuixTextStyles, fontScaleFactor) {
        if (fontScaleFactor == 1.0f) {
            miuixTextStyles
        } else {
            miuixTextStyles.copy(
                main = miuixTextStyles.main.scaleFontSize(fontScaleFactor),
                headline1 = miuixTextStyles.headline1.scaleFontSize(fontScaleFactor),
                headline2 = miuixTextStyles.headline2.scaleFontSize(fontScaleFactor),
                title1 = miuixTextStyles.title1.scaleFontSize(fontScaleFactor),
                title2 = miuixTextStyles.title2.scaleFontSize(fontScaleFactor),
                title3 = miuixTextStyles.title3.scaleFontSize(fontScaleFactor),
                title4 = miuixTextStyles.title4.scaleFontSize(fontScaleFactor),
                body1 = miuixTextStyles.body1.scaleFontSize(fontScaleFactor),
                body2 = miuixTextStyles.body2.scaleFontSize(fontScaleFactor),
                footnote1 = miuixTextStyles.footnote1.scaleFontSize(fontScaleFactor),
                footnote2 = miuixTextStyles.footnote2.scaleFontSize(fontScaleFactor),
                button = miuixTextStyles.button.scaleFontSize(fontScaleFactor),
            )
        }
    }

    val currentDensity = LocalDensity.current
    val scaledDensity = remember(currentDensity.density, fontScaleFactor) {
        Density(
            density = currentDensity.density,
            fontScale = fontScaleFactor,
        )
    }

    CompositionLocalProvider(
        LocalColorMode provides colorMode,
        LocalResolvedDarkTheme provides resolvedDark,
        LocalBackgroundStyle provides backgroundStyle,
        LocalDensity provides scaledDensity,
    ) {
        ProvideSkipiTheme(
            colors = appColors,
            miuixColors = miuixColors,
            miuixTextStyles = miuixTextStyles,
            popupMiuixTextStyles = popupMiuixTextStyles,
            fontWeightShift = fontWeightShift,
        ) {
            SystemBarAppearance(
                statusBarDark = resolvedDark,
                navigationBarDark = resolvedDark,
            )
            content()
        }
    }
}

private fun TextStyle.scaleFontSize(scaleFactor: Float): TextStyle {
    val newFontSize = if (scaleFactor != 1.0f && fontSize.isSpecified) {
        fontSize * scaleFactor
    } else {
        fontSize
    }
    val newLineHeight = if (scaleFactor != 1.0f && lineHeight.isSpecified) {
        lineHeight * scaleFactor
    } else {
        lineHeight
    }
    return copy(
        fontSize = newFontSize,
        lineHeight = newLineHeight,
    )
}

private fun TextStyle.applyFontAndWeight(
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    targetWeight: FontWeight?,
): TextStyle {
    val newFontFamily = fontFamily ?: this.fontFamily
    val currentWeight = this.fontWeight ?: FontWeight.Normal
    val newWeight = if (targetWeight == null) {
        this.fontWeight
    } else {
        val shift = targetWeight.weight - FontWeight.Normal.weight
        val effectiveWeight = (currentWeight.weight + shift).coerceIn(100, 900)
        FontWeight(effectiveWeight)
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

@Immutable
internal data class NamedThemePalette(
    val background: Color,
    val onBackground: Color,
    val accent: Color,
    val onAccent: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
)

internal fun namedThemePaletteFor(colorMode: Int): NamedThemePalette? = when (normalizeColorMode(colorMode)) {
    ColorModeAurora -> NamedThemePalette(
        background = Color(0xFF081427),
        onBackground = Color(0xFFE7F1FF),
        accent = Color(0xFF3B82F6),
        onAccent = Color.White,
        surface = Color(0xFF102443),
        onSurface = Color(0xFFE7F1FF),
        surfaceVariant = Color(0xFF18345E),
        onSurfaceVariant = Color(0xFFB2C8EA),
    )
    ColorModeSakura -> NamedThemePalette(
        background = Color(0xFFFFF7FB),
        onBackground = Color(0xFF351321),
        accent = Color(0xFFB93872),
        onAccent = Color.White,
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF351321),
        surfaceVariant = Color(0xFFF7E4EE),
        onSurfaceVariant = Color(0xFF806171),
    )
    ColorModeForest -> NamedThemePalette(
        background = Color(0xFF061B15),
        onBackground = Color(0xFFE6FFF4),
        accent = Color(0xFF16835A),
        onAccent = Color.White,
        surface = Color(0xFF0C2A20),
        onSurface = Color(0xFFE6FFF4),
        surfaceVariant = Color(0xFF164235),
        onSurfaceVariant = Color(0xFFA9D7C0),
    )
    ColorModeSunset -> NamedThemePalette(
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
