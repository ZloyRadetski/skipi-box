// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.anim

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * How often decorative animations (breathing background, connection waves) advance. An animation driven by the
 * frame clock redraws the whole window on every frame of the display (60 to 144 times a second), which kept a
 * connected window at a fifth of a CPU core; these slow movements look the same at 25 frames a second.
 */
const val DecorativeFrameMillis = 40L

/** Milliseconds since this clock was enabled, advancing every [DecorativeFrameMillis]; it rests at 0 while disabled. */
@Composable
fun rememberDecorativeClock(enabled: Boolean): State<Long> {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            clock.longValue = 0L
            return@LaunchedEffect
        }
        // Time comes from the frame clock itself, so it also follows a test or a paused frame clock.
        var startedAtNanos = -1L
        while (true) {
            // Waiting for a frame means a stopped or covered window costs nothing.
            withFrameNanos { nanos ->
                if (startedAtNanos < 0L) startedAtNanos = nanos
                clock.longValue = (nanos - startedAtNanos) / 1_000_000L
            }
            delay(DecorativeFrameMillis)
        }
    }
    return clock
}

/** 0 to 1 over [durationMillis], then back to 0 over the next [durationMillis], and so on. */
fun pulseFraction(elapsedMillis: Long, durationMillis: Int, easing: Easing = FastOutSlowInEasing): Float {
    if (durationMillis <= 0) return 0f
    val phase = (elapsedMillis.coerceAtLeast(0L) % (2L * durationMillis)).toFloat() / durationMillis
    val linear = if (phase <= 1f) phase else 2f - phase
    return easing.transform(linear.coerceIn(0f, 1f))
}

/**
 * Progress 0 to 1 of an animation that waits [delayMillis], travels for [durationMillis] and starts over; it
 * stays at 0 during the wait. One period is [delayMillis] plus [durationMillis].
 */
fun restartProgress(elapsedMillis: Long, durationMillis: Int, delayMillis: Int, easing: Easing): Float {
    if (durationMillis <= 0) return 0f
    val period = (delayMillis + durationMillis).toLong()
    val inPeriod = elapsedMillis.coerceAtLeast(0L) % period
    if (inPeriod < delayMillis) return 0f
    return easing.transform(((inPeriod - delayMillis).toFloat() / durationMillis).coerceIn(0f, 1f))
}
