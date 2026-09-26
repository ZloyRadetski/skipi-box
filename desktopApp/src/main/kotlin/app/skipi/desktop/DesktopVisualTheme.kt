// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.skipi.ui.theme.AppColors
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.namedThemePaletteFor

/** A small shared palette switch so AMOLED changes every desktop section, not just Settings. */
internal val LocalDesktopThemeMode = staticCompositionLocalOf { DesktopThemeMode.Dark }

internal val DesktopContentBackground: Color
    @Composable get() = SkipiTheme.colors.background

@Composable
internal fun DesktopAppTheme(content: @Composable () -> Unit) {
    val themeMode = LocalDesktopThemeMode.current
    val appColors = remember(themeMode) {
        desktopAppColors(themeMode)
    }

    ProvideSkipiTheme(
        colors = appColors,
        visualProfile = SkipiVisualProfile.Desktop,
        content = content,
    )
}

private fun desktopAppColors(themeMode: DesktopThemeMode): AppColors {
    if (themeMode == DesktopThemeMode.Light) {
        return AppColors(
            background = Color(0xFFF5F6FA),
            onBackground = Color(0xFF1A1B20),
            accent = Color(0xFF345D9D),
            onAccent = Color.White,
            surface = Color.White,
            onSurface = Color(0xFF1A1B20),
            surfaceVariant = Color(0xFFE7EAF1),
            onSurfaceVariant = Color(0xFF5E6471),
            isDark = false,
        )
    }

    val dark = themeMode != DesktopThemeMode.Sakura
    val named = namedThemePaletteFor(themeMode.name)
    val amoled = themeMode == DesktopThemeMode.Amoled
    return AppColors(
        background = when {
            amoled -> Color.Black
            named != null -> named.background
            else -> Color(0xFF141519)
        },
        onBackground = named?.onBackground ?: Color(0xFFF1F1F3),
        accent = named?.accent ?: Color(0xFFF1F1F3),
        onAccent = named?.onAccent ?: Color(0xFF202126),
        surface = when {
            amoled -> Color(0xFF0A0A0A)
            named != null -> named.surface
            else -> Color(0xFF202126)
        },
        onSurface = named?.onSurface ?: Color(0xFFF1F1F3),
        surfaceVariant = when {
            amoled -> Color(0xFF141414)
            named != null -> named.surfaceVariant
            else -> Color(0xFF2A2C32)
        },
        onSurfaceVariant = named?.onSurfaceVariant ?: Color(0xFF9A9DA8),
        isDark = dark,
    )
}
