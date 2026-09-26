// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.skipi.ui.text.ThemedTypography
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes

/**
 * The common visual root for every SKIPI Compose surface.
 *
 * Shared screens currently use both Material 3 and Miuix controls. Supplying
 * both themes from one palette keeps inline controls, menus and detached Miuix
 * windows in sync across Android and Desktop.
 */
@Composable
fun ProvideSkipiTheme(
    colors: AppColors,
    fontWeightShift: Int = 0,
    visualProfile: SkipiVisualProfile = SkipiVisualProfile.Android,
    content: @Composable () -> Unit,
) {
    val baseMiuixColors = MiuixTheme.colorScheme
    val miuixColors = remember(baseMiuixColors, colors) {
        baseMiuixColors.withSkipiColors(colors)
    }

    ProvideSkipiTheme(
        colors = colors,
        miuixColors = miuixColors,
        miuixTextStyles = MiuixTheme.textStyles,
        fontWeightShift = fontWeightShift,
        visualProfile = visualProfile,
        content = content,
    )
}

/**
 * Advanced root for a host that derives Miuix colors or typography from its
 * own system palette. Desktop normally uses the simpler overload above.
 */
@Composable
fun ProvideSkipiTheme(
    colors: AppColors,
    miuixColors: Colors,
    miuixTextStyles: TextStyles,
    popupMiuixTextStyles: TextStyles = miuixTextStyles,
    fontWeightShift: Int = 0,
    visualProfile: SkipiVisualProfile = SkipiVisualProfile.Android,
    content: @Composable () -> Unit,
) {
    val materialColorScheme = remember(colors, miuixColors) {
        colors.toMaterialColorScheme()
    }
    val materialTypography = remember(miuixTextStyles, fontWeightShift) {
        Typography().withHostFont(miuixTextStyles.main.fontFamily, fontWeightShift)
    }

    SideEffect {
        ThemedTypography.updateWeightShift(fontWeightShift)
    }

    val skipiTypography = remember(materialTypography) { materialTypography.toSkipiTypography() }
    val skipiColorScheme = remember(colors) { colors.toSkipiColorScheme() }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalSkipiColorScheme provides skipiColorScheme,
        LocalSkipiTypography provides skipiTypography,
        LocalSkipiShapes provides shapesFor(visualProfile),
        LocalSkipiSpacing provides SkipiSpacing.forWindowClass(SkipiWindowClass.Medium),
        LocalSkipiHomeMetrics provides SkipiHomeMetrics.forProfile(SkipiWindowClass.Medium, visualProfile),
        LocalSkipiElevation provides SkipiElevation(),
        LocalSkipiMotion provides SkipiMotion(),
        LocalSkipiVisualProfile provides visualProfile,
        // Detached Miuix windows use a fresh density from their own resources.
        // Hosts can therefore provide already-scaled styles only for those
        // windows, while the ordinary composition keeps normal `sp` scaling.
        LocalPopupTextStyles provides popupMiuixTextStyles,
        LocalPopupColors provides miuixColors,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = materialTypography,
            shapes = materialShapes(visualProfile),
        ) {
            MiuixTheme(
                colors = miuixColors,
                textStyles = miuixTextStyles,
            ) {
                // Deriving the class from constraints keeps the same shared screen
                // adaptive on phones, tablets and desktop windows.
                BoxWithConstraints {
                    val windowClass = classifySkipiWindow(maxWidth)
                    CompositionLocalProvider(
                        LocalSkipiWindowClass provides windowClass,
                        LocalSkipiSpacing provides SkipiSpacing.forWindowClass(windowClass),
                        LocalSkipiShapes provides shapesFor(visualProfile),
                        LocalSkipiHomeMetrics provides SkipiHomeMetrics.forProfile(windowClass, visualProfile),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

private fun Colors.withSkipiColors(appColors: AppColors) = copy(
    surface = appColors.surface,
    surfaceContainer = appColors.surface,
    surfaceContainerHigh = appColors.surface,
    surfaceContainerHighest = appColors.surface,
    background = appColors.background,
    surfaceVariant = appColors.surfaceVariant,
    onSurface = appColors.onSurface,
    onBackground = appColors.onBackground,
    onSurfaceContainer = appColors.onSurface,
    onSurfaceVariantSummary = appColors.onSurfaceVariant,
    onBackgroundVariant = appColors.onSurfaceVariant,
    primary = appColors.accent,
    primaryVariant = appColors.accent,
)

private fun AppColors.toSkipiColorScheme() = SkipiColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    outline = outline,
    isDark = isDark,
    tertiary = accent,
    onTertiary = onAccent,
    tertiaryContainer = primaryContainer,
    onTertiaryContainer = onPrimaryContainer,
    surfaceContainerLow = surface,
    surfaceContainerLowest = background,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    disabledContainer = disabledContainer,
    onDisabled = onDisabled,
    success = success,
    onSuccess = onSuccess,
    warning = warning,
    onWarning = onWarning,
    info = info,
    onInfo = onInfo,
)

private fun AppColors.toMaterialColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = primaryContainer,
        onTertiaryContainer = onPrimaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = primaryContainer,
        onTertiaryContainer = onPrimaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
    )
}



private fun shapesFor(profile: SkipiVisualProfile): SkipiShapes {
    val desktop = profile is SkipiVisualProfile.Desktop
    return SkipiShapes(
        extraSmall = RoundedCornerShape(if (desktop) 6.dp else 10.dp),
        small = RoundedCornerShape(if (desktop) 10.dp else 16.dp),
        medium = RoundedCornerShape(if (desktop) 14.dp else 20.dp),
        large = RoundedCornerShape(if (desktop) 18.dp else 26.dp),
        extraLarge = RoundedCornerShape(if (desktop) 24.dp else 32.dp),
    )
}

private fun materialShapes(profile: SkipiVisualProfile) = Shapes(
    extraSmall = RoundedCornerShape(if (profile is SkipiVisualProfile.Desktop) 6.dp else 10.dp),
    small = RoundedCornerShape(if (profile is SkipiVisualProfile.Desktop) 10.dp else 16.dp),
    medium = RoundedCornerShape(if (profile is SkipiVisualProfile.Desktop) 14.dp else 20.dp),
    large = RoundedCornerShape(if (profile is SkipiVisualProfile.Desktop) 18.dp else 26.dp),
    extraLarge = RoundedCornerShape(if (profile is SkipiVisualProfile.Desktop) 24.dp else 32.dp),
)

private fun Typography.withHostFont(
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    fontWeightShift: Int,
) = copy(
    displayLarge = displayLarge.withHostFont(fontFamily, fontWeightShift),
    displayMedium = displayMedium.withHostFont(fontFamily, fontWeightShift),
    displaySmall = displaySmall.withHostFont(fontFamily, fontWeightShift),
    headlineLarge = headlineLarge.withHostFont(fontFamily, fontWeightShift),
    headlineMedium = headlineMedium.withHostFont(fontFamily, fontWeightShift),
    headlineSmall = headlineSmall.withHostFont(fontFamily, fontWeightShift),
    titleLarge = titleLarge.withHostFont(fontFamily, fontWeightShift),
    titleMedium = titleMedium.withHostFont(fontFamily, fontWeightShift),
    titleSmall = titleSmall.withHostFont(fontFamily, fontWeightShift),
    bodyLarge = bodyLarge.withHostFont(fontFamily, fontWeightShift),
    bodyMedium = bodyMedium.withHostFont(fontFamily, fontWeightShift),
    bodySmall = bodySmall.withHostFont(fontFamily, fontWeightShift),
    labelLarge = labelLarge.withHostFont(fontFamily, fontWeightShift),
    labelMedium = labelMedium.withHostFont(fontFamily, fontWeightShift),
    labelSmall = labelSmall.withHostFont(fontFamily, fontWeightShift),
)

private fun androidx.compose.ui.text.TextStyle.withHostFont(
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    fontWeightShift: Int,
) = copy(
    fontFamily = fontFamily,
    fontWeight = androidx.compose.ui.text.font.FontWeight(
        ((fontWeight?.weight ?: androidx.compose.ui.text.font.FontWeight.Normal.weight) + fontWeightShift)
            .coerceIn(100, 900),
    ),
)

private fun Typography.toSkipiTypography() = SkipiTypography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    displaySmall = displaySmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)
