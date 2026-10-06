// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.text

import androidx.compose.ui.text.font.FontWeight
import app.modes.FontWeightModeBold
import app.modes.FontWeightModeDefault
import app.modes.FontWeightModeLight
import app.modes.FontWeightModeMedium
import app.modes.FontWeightModeNormal
import app.modes.FontWeightModeSemiBold
import app.modes.normalizeFontWeightMode

fun resolveFontWeight(fontWeightMode: Int): FontWeight? = when (normalizeFontWeightMode(fontWeightMode)) {
    FontWeightModeLight -> FontWeight.Light
    FontWeightModeNormal -> FontWeight.Normal
    FontWeightModeMedium -> FontWeight.Medium
    FontWeightModeSemiBold -> FontWeight.SemiBold
    FontWeightModeBold -> FontWeight.Bold
    else -> null
}
