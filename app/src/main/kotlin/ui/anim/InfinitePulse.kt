// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember

/**
 * Infinite pulse animation that only drives frames while [enabled] is true.
 *
 * Unlike [androidx.compose.animation.core.rememberInfiniteTransition], which
 * ticks the frame clock for as long as it stays composed, this helper stops
 * the underlying animator and rests at [restingValue] when disabled, so idle
 * screens do not cause continuous recomposition and redraws.
 */
@Composable
fun rememberInfinitePulse(
    enabled: Boolean,
    initialValue: Float,
    targetValue: Float,
    durationMillis: Int,
    restingValue: Float = initialValue,
): State<Float> {
    val animatable = remember { Animatable(restingValue) }
    LaunchedEffect(enabled) {
        if (enabled) {
            animatable.animateTo(
                targetValue,
                infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        } else {
            animatable.stop()
            animatable.snapTo(restingValue)
        }
    }
    return animatable.asState()
}
