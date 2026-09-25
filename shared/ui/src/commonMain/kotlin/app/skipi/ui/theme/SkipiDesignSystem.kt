// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SkipiColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val isDark: Boolean,
    val tertiary: Color = primary,
    val onTertiary: Color = onPrimary,
    val tertiaryContainer: Color = primaryContainer,
    val onTertiaryContainer: Color = onPrimaryContainer,
    val surfaceDim: Color = background,
    val surfaceBright: Color = surface,
    val surfaceContainer: Color = surfaceVariant,
    val surfaceContainerHigh: Color = surfaceVariant,
    val surfaceContainerHighest: Color = surfaceVariant,
    val inverseSurface: Color = onSurface,
    val inverseOnSurface: Color = surface,
    val inversePrimary: Color = primary,
    val scrim: Color = Color.Black,
    val disabledContainer: Color = surfaceVariant.copy(alpha = 0.38f),
    val onDisabled: Color = onSurface.copy(alpha = 0.38f),
    val success: Color = Color(0xFF128A3C),
    val onSuccess: Color = Color.White,
    val warning: Color = Color(0xFFD18A00),
    val onWarning: Color = Color.Black,
    val info: Color = primary,
    val onInfo: Color = onPrimary,
)

@Immutable
data class SkipiTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
)

@Immutable
data class SkipiShapes(
    val extraSmall: androidx.compose.foundation.shape.CornerBasedShape,
    val small: androidx.compose.foundation.shape.CornerBasedShape,
    val medium: androidx.compose.foundation.shape.CornerBasedShape,
    val large: androidx.compose.foundation.shape.CornerBasedShape,
    val extraLarge: androidx.compose.foundation.shape.CornerBasedShape,
)

@Immutable
data class SkipiSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp,
) {
    companion object
}

@Immutable
data class SkipiElevation(
    val none: Dp = 0.dp,
    val small: Dp = 2.dp,
    val medium: Dp = 4.dp,
    val large: Dp = 8.dp,
)

@Immutable
data class SkipiMotion(
    val defaultDuration: Int = 300,
    val fastDuration: Int = 150,
    val slowDuration: Int = 500,
)

enum class SkipiWindowClass {
    Compact, Medium, Expanded
}

/** Material adaptive breakpoints, kept pure so they can be tested without Compose UI. */
fun classifySkipiWindow(width: Dp): SkipiWindowClass = when {
    width < 600.dp -> SkipiWindowClass.Compact
    width < 840.dp -> SkipiWindowClass.Medium
    else -> SkipiWindowClass.Expanded
}

fun SkipiSpacing.Companion.forWindowClass(windowClass: SkipiWindowClass): SkipiSpacing = when (windowClass) {
    SkipiWindowClass.Compact -> SkipiSpacing(
        extraSmall = 4.dp,
        small = 8.dp,
        medium = 12.dp,
        large = 16.dp,
        extraLarge = 24.dp,
        huge = 32.dp,
    )
    SkipiWindowClass.Medium -> SkipiSpacing()
    SkipiWindowClass.Expanded -> SkipiSpacing(
        extraSmall = 4.dp,
        small = 10.dp,
        medium = 16.dp,
        large = 24.dp,
        extraLarge = 32.dp,
        huge = 48.dp,
    )
}

sealed class SkipiVisualProfile {
    data object Android : SkipiVisualProfile()
    data object Desktop : SkipiVisualProfile()
}

val LocalSkipiColorScheme = staticCompositionLocalOf<SkipiColorScheme> {
    error("No SkipiColorScheme provided")
}

val LocalSkipiTypography = staticCompositionLocalOf<SkipiTypography> {
    error("No SkipiTypography provided")
}

val LocalSkipiShapes = staticCompositionLocalOf<SkipiShapes> {
    error("No SkipiShapes provided")
}

val LocalSkipiSpacing = staticCompositionLocalOf<SkipiSpacing> {
    SkipiSpacing()
}

val LocalSkipiElevation = staticCompositionLocalOf<SkipiElevation> {
    SkipiElevation()
}

val LocalSkipiMotion = staticCompositionLocalOf<SkipiMotion> {
    SkipiMotion()
}

val LocalSkipiWindowClass = staticCompositionLocalOf<SkipiWindowClass> {
    SkipiWindowClass.Compact
}

val LocalSkipiVisualProfile = staticCompositionLocalOf<SkipiVisualProfile> {
    SkipiVisualProfile.Android
}
