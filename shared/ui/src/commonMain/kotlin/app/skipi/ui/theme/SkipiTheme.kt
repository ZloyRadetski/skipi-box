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
    val materialTypography = remember(miuixTextStyles) {
        miuixTextStyles.toMaterialTypography()
    }

    SideEffect {
        ThemedTypography.updateWeightShift(fontWeightShift)
    }

    val skipiTypography = remember(miuixTextStyles) {
        SkipiTypography(
            displayLarge = miuixTextStyles.headline1,
            displayMedium = miuixTextStyles.headline2,
            displaySmall = miuixTextStyles.title1,
            headlineLarge = miuixTextStyles.headline1,
            headlineMedium = miuixTextStyles.headline2,
            headlineSmall = miuixTextStyles.title1,
            titleLarge = miuixTextStyles.title1,
            titleMedium = miuixTextStyles.title2,
            titleSmall = miuixTextStyles.title3,
            bodyLarge = miuixTextStyles.body1,
            bodyMedium = miuixTextStyles.body2,
            bodySmall = miuixTextStyles.footnote1,
            labelLarge = miuixTextStyles.button,
            labelMedium = miuixTextStyles.footnote1,
            labelSmall = miuixTextStyles.footnote2,
        )
    }
    val skipiColorScheme = remember(colors) { colors.toSkipiColorScheme() }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalSkipiColorScheme provides skipiColorScheme,
        LocalSkipiTypography provides skipiTypography,
        LocalSkipiShapes provides SkipiShapesDefaults,
        LocalSkipiSpacing provides SkipiSpacing.forWindowClass(SkipiWindowClass.Medium),
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
            shapes = MaterialShapes,
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



private val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val SkipiShapesDefaults = SkipiShapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private fun TextStyles.toMaterialTypography() = Typography(
    displayLarge = headline1,
    displayMedium = headline2,
    displaySmall = title1,
    headlineLarge = headline1,
    headlineMedium = headline2,
    headlineSmall = title1,
    titleLarge = title1,
    titleMedium = title2,
    titleSmall = title3,
    bodyLarge = body1,
    bodyMedium = body2,
    bodySmall = footnote1,
    labelLarge = button,
    labelMedium = footnote1,
    labelSmall = footnote2,
)
