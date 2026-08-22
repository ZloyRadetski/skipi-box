// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun rememberSystemAccentColor(): Color = Color(0xFF0070F3)

@Composable
actual fun PlatformThemeEffects(
    statusBarDark: Boolean,
    navigationBarDark: Boolean,
    colorMode: Int,
) {
    // Desktop windows have no system bars or splash screen theming.
}
