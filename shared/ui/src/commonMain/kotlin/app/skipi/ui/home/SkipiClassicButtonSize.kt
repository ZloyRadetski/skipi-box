// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyHomeButtonSize

/** The thin ring around the classic button is this much wider than the button itself. */
const val SkipiClassicButtonRingRatio = 1.1f

/**
 * Outer diameter (the thin ring) of the classic connection button. Large is the full base size; a phone-width
 * window uses a smaller base than a wide one.
 */
fun skipiClassicButtonOuterSize(size: ProxyHomeButtonSize, compact: Boolean): Dp {
    val base = if (compact) 248.dp else 280.dp
    val scale = when (size) {
        ProxyHomeButtonSize.Small -> 0.6f
        ProxyHomeButtonSize.Medium -> 0.8f
        ProxyHomeButtonSize.Large -> 1f
        ProxyHomeButtonSize.Giant -> 1.2f
    }
    return base * scale
}
