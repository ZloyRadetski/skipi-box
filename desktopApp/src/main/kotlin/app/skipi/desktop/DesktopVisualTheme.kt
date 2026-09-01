// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** A small shared palette switch so AMOLED changes every desktop section, not just Settings. */
internal val LocalDesktopThemeMode = staticCompositionLocalOf { DesktopThemeMode.Dark }

internal val DesktopContentBackground: Color
    @Composable get() = when (LocalDesktopThemeMode.current) {
        DesktopThemeMode.Dark -> Color(0xFF141519)
        DesktopThemeMode.Amoled -> Color.Black
    }

internal fun desktopColorScheme(themeMode: DesktopThemeMode): ColorScheme = darkColorScheme(
    background = if (themeMode == DesktopThemeMode.Amoled) Color.Black else Color(0xFF141519),
    surface = Color(0xFF202126),
    surfaceContainer = Color(0xFF202126),
    primary = Color(0xFFF1F1F3),
    onPrimary = Color(0xFF202126),
    onBackground = Color(0xFFF1F1F3),
    onSurface = Color(0xFFF1F1F3),
    secondary = Color(0xFF9A9DA8),
)
