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

private fun desktopAppColors(themeMode: DesktopThemeMode) = AppColors(
    background = if (themeMode == DesktopThemeMode.Amoled) Color.Black else Color(0xFF141519),
    onBackground = Color(0xFFF1F1F3),
    accent = Color(0xFFF1F1F3),
    onAccent = Color(0xFF202126),
    surface = if (themeMode == DesktopThemeMode.Amoled) Color(0xFF0A0A0A) else Color(0xFF202126),
    onSurface = Color(0xFFF1F1F3),
    surfaceVariant = if (themeMode == DesktopThemeMode.Amoled) Color(0xFF141414) else Color(0xFF2A2C32),
    onSurfaceVariant = Color(0xFF9A9DA8),
    isDark = true,
)
