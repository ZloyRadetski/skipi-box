// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeTrackColor: Color = MiuixTheme.colorScheme.primary,
    inactiveTrackColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    thumbColor: Color = MiuixTheme.colorScheme.primary,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val range = (maxVal - minVal).coerceAtLeast(0.001f)
    val fraction = ((value - minVal) / range).coerceIn(0f, 1f)

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val density = LocalDensity.current
    val thumbSizeDp = 20.dp
    val thumbSizePx = remember(density) { with(density) { thumbSizeDp.toPx() } }

    fun calculateValue(xPos: Float): Float {
        val availableWidth = (widthPx - thumbSizePx).coerceAtLeast(1f)
        val relativeX = (xPos - thumbSizePx / 2f).coerceIn(0f, availableWidth)
        val newFraction = (relativeX / availableWidth).coerceIn(0f, 1f)
        var rawVal = minVal + newFraction * range
        if (steps > 0) {
            val stepSize = range / (steps + 1)
            rawVal = (minVal + ((rawVal - minVal) / stepSize).roundToInt() * stepSize).coerceIn(minVal, maxVal)
        }
        return rawVal
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(minVal, maxVal, steps, thumbSizePx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    currentOnValueChange(calculateValue(down.position.x))

                    horizontalDrag(down.id) { change ->
                        change.consume()
                        currentOnValueChange(calculateValue(change.position.x))
                    }
                    currentOnValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            val trackHeight = size.height
            val trackWidth = size.width
            val availableWidth = (trackWidth - thumbSizePx).coerceAtLeast(0f)
            val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            // 1. Inactive Track
            drawRoundRect(
                color = inactiveTrackColor,
                size = size,
                cornerRadius = cornerRadius,
            )

            // 2. Active Track
            val activeWidth = (thumbSizePx / 2f + availableWidth * fraction).coerceIn(0f, trackWidth)
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = activeTrackColor,
                    size = Size(width = activeWidth, height = trackHeight),
                    cornerRadius = cornerRadius,
                )
            }
        }

        // Thumb
        Box(
            modifier = Modifier
                .offset {
                    val availableWidth = (widthPx - thumbSizePx).coerceAtLeast(0f)
                    IntOffset(x = (availableWidth * fraction).roundToInt(), y = 0)
                }
                .size(thumbSizeDp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}