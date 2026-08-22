// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun rememberSystemAccentColor(): Color {
    val context = LocalContext.current
    return remember(context) { resolveSystemAccentColor(context) }
}

@Composable
actual fun PlatformThemeEffects(
    statusBarDark: Boolean,
    navigationBarDark: Boolean,
    colorMode: Int,
) {
    SystemBarAppearance(
        statusBarDark = statusBarDark,
        navigationBarDark = navigationBarDark,
    )
}

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
