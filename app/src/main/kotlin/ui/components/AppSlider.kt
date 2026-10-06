// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.skipi.ui.components.AppSlider as SharedAppSlider
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
) = SharedAppSlider(
    value = value,
    onValueChange = onValueChange,
    valueRange = valueRange,
    steps = steps,
    onValueChangeFinished = onValueChangeFinished,
    modifier = modifier,
    activeTrackColor = activeTrackColor,
    inactiveTrackColor = inactiveTrackColor,
    thumbColor = thumbColor,
)
