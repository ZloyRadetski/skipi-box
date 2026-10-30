// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.background

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.skipi.ui.anim.rememberInfinitePulse
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import app.skipi.ui.settings.AppearanceSettingValues

/** Palette and opacity of the "connection" background before the breathing pulse is applied. */
data class ConnectionBackgroundSpec(
    val firstColor: Color,
    val secondColor: Color,
    val primaryAlpha: Float,
    val secondaryAlpha: Float,
)

fun connectionBackgroundSpec(isRunning: Boolean, isDark: Boolean): ConnectionBackgroundSpec =
    if (isRunning) {
        ConnectionBackgroundSpec(
            firstColor = Color(0xFF10B981),
            secondColor = Color(0xFF06B6D4),
            primaryAlpha = if (isDark) 0.58f else 0.40f,
            secondaryAlpha = if (isDark) 0.32f else 0.20f,
        )
    } else {
        ConnectionBackgroundSpec(
            firstColor = if (isDark) Color(0xFF6366F1) else Color(0xFF4F46E5),
            secondColor = if (isDark) Color(0xFF8B5CF6) else Color(0xFF7C3AED),
            primaryAlpha = if (isDark) 0.45f else 0.30f,
            secondaryAlpha = if (isDark) 0.22f else 0.14f,
        )
    }

fun backgroundDimAlpha(percent: Int): Float = percent.coerceIn(0, 100) / 100f

/** Top and middle stops (the page colour closes the gradient) used while no photo is stored. */
fun photoFallbackGradient(isDark: Boolean): List<Color> =
    if (isDark) listOf(Color(0xFF312E81), Color(0xFF1E1B4B)) else listOf(Color(0xFFC7D2FE), Color(0xFFE0E7FF))

/**
 * Shared page background: classic solid, custom photo, connection gradient or animated aurora.
 * The host supplies the decoded photo and whether animations should rest (power save).
 */
@Composable
fun SkipiAppBackground(
    style: Int,
    isDark: Boolean,
    solidColor: Color,
    isConnected: Boolean,
    dimPercent: Int,
    photo: ImageBitmap?,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (style) {
        AppearanceSettingValues.BackgroundStylePhoto -> Box(modifier.fillMaxSize().background(solidColor)) {
            if (photo != null) {
                Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (dimPercent.coerceIn(0, 100) > 0) DimLayer(dimPercent)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(photoFallbackGradient(isDark) + solidColor)))
            }
            content()
        }

        AppearanceSettingValues.BackgroundStyleConnection -> {
            // Breathing runs only while connected; when idle the background rests statically.
            val pulse = rememberInfinitePulse(
                enabled = isConnected,
                initialValue = 0.90f,
                targetValue = 1.10f,
                durationMillis = 2400,
                restingValue = 1.00f,
            )
            val spec = connectionBackgroundSpec(isConnected, isDark)
            val first = animateColorAsState(spec.firstColor, tween(500), label = "activeColor1")
            val second = animateColorAsState(spec.secondColor, tween(500), label = "activeColor2")
            // Pulse and colour are read while drawing so the breathing never recomposes the page.
            Box(
                modifier
                    .fillMaxSize()
                    .background(solidColor)
                    .drawBehind {
                        val breathing = pulse.value
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(
                                    first.value.copy(alpha = (spec.primaryAlpha * breathing).coerceIn(0f, 1f)),
                                    second.value.copy(alpha = (spec.secondaryAlpha * breathing).coerceIn(0f, 1f)),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = 1400f,
                            ),
                        )
                    },
            ) { content() }
        }

        AppearanceSettingValues.BackgroundStyleAurora -> Box(modifier.fillMaxSize().background(solidColor)) {
            if (reduceMotion) {
                Canvas(Modifier.fillMaxSize()) { drawAuroraMesh(isDark, t1 = 0.50f, t2 = 0.35f, t3 = 0.65f, t4 = 0.45f) }
            } else {
                // The mesh drifts over tens of seconds, so ~20 redraws per second look identical to 60
                // while costing a third of the GPU work; time is read only while drawing.
                val elapsed = remember { mutableLongStateOf(0L) }
                LaunchedEffect(Unit) {
                    val started = TimeSource.Monotonic.markNow()
                    while (true) {
                        // Waiting for a frame means a stopped or covered window costs nothing.
                        withFrameNanos { elapsed.longValue = started.elapsedNow().inWholeMilliseconds }
                        delay(AuroraFrameMillis)
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val phases = auroraPhases(elapsed.longValue)
                    drawAuroraMesh(isDark, phases.t1, phases.t2, phases.t3, phases.t4)
                }
            }
            if (dimPercent.coerceIn(0, 100) > 0) DimLayer(dimPercent)
            content()
        }

        else -> Box(modifier.fillMaxSize().background(solidColor)) { content() }
    }
}

@Composable
private fun DimLayer(percent: Int) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = backgroundDimAlpha(percent))))
}

private const val AuroraFrameMillis = 50L

/** The four independent drift phases of the aurora mesh. */
data class AuroraPhases(val t1: Float, val t2: Float, val t3: Float, val t4: Float)

private val AuroraDurationsMillis = longArrayOf(17_000, 13_000, 21_000, 11_000)

/** Phases at [elapsedMillis]: each ping-pongs 0..1..0 over twice its duration with fast-out-slow-in easing. */
fun auroraPhases(elapsedMillis: Long): AuroraPhases {
    fun phase(duration: Long): Float {
        val cycle = elapsedMillis.coerceAtLeast(0L) % (2 * duration)
        val linear = if (cycle <= duration) cycle.toFloat() / duration else 2f - cycle.toFloat() / duration
        return FastOutSlowInEasing.transform(linear.coerceIn(0f, 1f))
    }
    return AuroraPhases(
        phase(AuroraDurationsMillis[0]),
        phase(AuroraDurationsMillis[1]),
        phase(AuroraDurationsMillis[2]),
        phase(AuroraDurationsMillis[3]),
    )
}
