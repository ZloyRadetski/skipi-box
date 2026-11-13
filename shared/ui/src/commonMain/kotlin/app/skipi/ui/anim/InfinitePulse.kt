// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.anim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/**
 * Infinite pulse that only drives frames while [enabled] is true, and then at [DecorativeFrameMillis] steps
 * instead of once per display frame, so a connected window does not redraw 60 to 144 times a second. It rests
 * at [restingValue] when disabled. The value is meant to be read while drawing, not in composition.
 */
@Composable
fun rememberInfinitePulse(
    enabled: Boolean,
    initialValue: Float,
    targetValue: Float,
    durationMillis: Int,
    restingValue: Float = initialValue,
): State<Float> {
    val clock = rememberDecorativeClock(enabled)
    return remember(enabled, initialValue, targetValue, durationMillis, restingValue) {
        derivedStateOf {
            if (enabled) {
                initialValue + (targetValue - initialValue) * pulseFraction(clock.value, durationMillis)
            } else {
                restingValue
            }
        }
    }
}
