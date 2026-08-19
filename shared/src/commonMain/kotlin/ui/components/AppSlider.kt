// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
    inactiveTrackColor: Color = MiuixTheme.colorScheme.surface,
    thumbColor: Color = MiuixTheme.colorScheme.primary,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val range = (maxVal - minVal).coerceAtLeast(0.001f)
    val fraction = ((value - minVal) / range).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(minVal, maxVal, steps) {
                detectTapGestures { offset ->
                    val newFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    var rawVal = minVal + newFraction * range
                    if (steps > 0) {
                        val stepSize = range / (steps + 1)
                        rawVal = (minVal + ((rawVal - minVal) / stepSize).roundToInt() * stepSize).coerceIn(minVal, maxVal)
                    }
                    onValueChange(rawVal)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(minVal, maxVal, steps) {
                detectHorizontalDragGestures(
                    onDragEnd = { onValueChangeFinished?.invoke() }
                ) { change, _ ->
                    change.consume()
                    val newFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                    var rawVal = minVal + newFraction * range
                    if (steps > 0) {
                        val stepSize = range / (steps + 1)
                        rawVal = (minVal + ((rawVal - minVal) / stepSize).roundToInt() * stepSize).coerceIn(minVal, maxVal)
                    }
                    onValueChange(rawVal)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Inactive Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(inactiveTrackColor.copy(alpha = 0.6f))
        )
        // Active Track
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(activeTrackColor)
        )
        // Thumb
        Box(
            modifier = Modifier
                .offset {
                    val availableWidth = (widthPx - 20.dp.toPx()).coerceAtLeast(0f)
                    IntOffset(x = (availableWidth * fraction).roundToInt(), y = 0)
                }
                .size(20.dp)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}
