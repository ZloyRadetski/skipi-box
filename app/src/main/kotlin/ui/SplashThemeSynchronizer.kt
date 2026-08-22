// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

import android.app.Activity
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import app.R
import app.modes.ColorModeDark
import app.modes.ColorModeLight
import app.modes.normalizeColorMode

/**
 * Keeps the window splash screen theme in sync with the selected color mode.
 * Android-only companion to the shared [AppTheme]; call alongside it.
 */
@Composable
fun SplashThemeSynchronizer(colorMode: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    if (view.isInEditMode) return
    LaunchedEffect(view, colorMode) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        val themeId = when (normalizeColorMode(colorMode)) {
            ColorModeLight -> R.style.AppTheme_Starting_Light
            ColorModeDark -> R.style.AppTheme_Starting_Dark
            else -> Resources.ID_NULL
        }
        activity.splashScreen.setSplashScreenTheme(themeId)
    }
}
