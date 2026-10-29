// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.background

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.skipi.ui.anim.rememberInfinitePulse
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
            val pulse by rememberInfinitePulse(
                enabled = isConnected,
                initialValue = 0.90f,
                targetValue = 1.10f,
                durationMillis = 2400,
                restingValue = 1.00f,
            )
            val spec = connectionBackgroundSpec(isConnected, isDark)
            val first by animateColorAsState(spec.firstColor, tween(500), label = "activeColor1")
            val second by animateColorAsState(spec.secondColor, tween(500), label = "activeColor2")
            Box(
                modifier
                    .fillMaxSize()
                    .background(solidColor)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                first.copy(alpha = spec.primaryAlpha * pulse),
                                second.copy(alpha = spec.secondaryAlpha * pulse),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = 1400f,
                        ),
                    ),
            ) { content() }
        }

        AppearanceSettingValues.BackgroundStyleAurora -> Box(modifier.fillMaxSize().background(solidColor)) {
            if (reduceMotion) {
                Canvas(Modifier.fillMaxSize()) { drawAuroraMesh(isDark, t1 = 0.50f, t2 = 0.35f, t3 = 0.65f, t4 = 0.45f) }
            } else {
                val transition = rememberInfiniteTransition(label = "auroraMesh")
                val t1 by transition.auroraPhase(17000, "auroraT1")
                val t2 by transition.auroraPhase(13000, "auroraT2")
                val t3 by transition.auroraPhase(21000, "auroraT3")
                val t4 by transition.auroraPhase(11000, "auroraT4")
                Canvas(Modifier.fillMaxSize()) { drawAuroraMesh(isDark, t1, t2, t3, t4) }
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

@Composable
private fun InfiniteTransition.auroraPhase(durationMillis: Int, label: String): State<Float> =
    animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = label,
    )
