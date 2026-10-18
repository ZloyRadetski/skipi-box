// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.platform.Font
import app.modes.FontFamilyModeDefault
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.ThemeMode
import app.skipi.ui.text.fontFamilyAssetName
import app.skipi.ui.theme.AppColors
import app.skipi.ui.theme.NamedThemePalette
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.applyAppearanceSettings
import app.skipi.ui.theme.namedThemePaletteFor
import app.skipi.ui.theme.resolveBaseAppearanceColors

/** A small shared palette switch so AMOLED changes every desktop section, not just Settings. */
internal val LocalDesktopThemeMode = staticCompositionLocalOf { DesktopThemeMode.Dark }

/** The host supplies the persisted shared appearance snapshot at the application root. */
internal val LocalDesktopAppearanceSettings = staticCompositionLocalOf {
    AppearanceSettings(dynamicColors = false)
}

internal val DesktopContentBackground: androidx.compose.ui.graphics.Color
    @Composable get() = SkipiTheme.colors.background

@Composable
internal fun DesktopAppTheme(content: @Composable () -> Unit) {
    val themeMode = LocalDesktopThemeMode.current
    val appearance = LocalDesktopAppearanceSettings.current
    val appColors = remember(themeMode, appearance) {
        desktopAppearanceColors(themeMode, appearance)
    }

    val fontFamilyMode = appearance.fontFamilyMode ?: FontFamilyModeDefault
    val resolvedFontFamily = remember(fontFamilyMode) {
        resolveDesktopFontFamily(fontFamilyMode)?.family
    }

    ProvideSkipiTheme(
        colors = appColors,
        fontFamily = resolvedFontFamily,
        fontSizeMode = appearance.fontSizeMode,
        fontWeightMode = appearance.fontWeightMode,
        visualProfile = SkipiVisualProfile.Desktop,
        content = content,
    )
}

internal fun desktopAppearanceColors(
    themeMode: DesktopThemeMode,
    appearance: AppearanceSettings,
): AppColors {
    val namedPalette = desktopNamedThemePalette(themeMode)
    val base = resolveBaseAppearanceColors(
        isDark = desktopThemeIsDark(themeMode),
        amoledBlack = themeMode == DesktopThemeMode.Amoled,
        namedPalette = namedPalette,
    )
    val resolvedAppearance = appearance.copy(
        themeMode = if (namedPalette != null) ThemeMode.Named else ThemeMode.System,
    )
    return applyAppearanceSettings(base, resolvedAppearance)
}

private fun desktopThemeIsDark(themeMode: DesktopThemeMode): Boolean = when (themeMode) {
    DesktopThemeMode.Light, DesktopThemeMode.Sakura -> false
    else -> true
}

private fun desktopNamedThemePalette(themeMode: DesktopThemeMode): NamedThemePalette? = when (themeMode) {
    DesktopThemeMode.Aurora,
    DesktopThemeMode.Sakura,
    DesktopThemeMode.Forest,
    DesktopThemeMode.Sunset -> namedThemePaletteFor(themeMode.name)
    else -> null
}

internal data class DesktopFontFamilyResolution(
    val resourceName: String,
    val family: FontFamily,
)

@OptIn(ExperimentalTextApi::class)
internal fun resolveDesktopFontFamily(fontFamilyMode: Int): DesktopFontFamilyResolution? {
    val resourceName = fontFamilyAssetName(fontFamilyMode) ?: return null
    val family = if (resourceName == "climate_crisis.ttf") {
        FontFamily(Font(resource = "font/$resourceName", weight = FontWeight.Normal))
    } else {
        createDesktopVariableFontFamily(resourceName)
    }
    return DesktopFontFamilyResolution(resourceName, family)
}

@OptIn(ExperimentalTextApi::class)
private fun createDesktopVariableFontFamily(resourceName: String): FontFamily {
    val weights = listOf(
        FontWeight.Thin,
        FontWeight.ExtraLight,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
    )
    return FontFamily(
        *weights.map { weight ->
            Font(
                resource = "font/$resourceName",
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        }.toTypedArray(),
    )
}
