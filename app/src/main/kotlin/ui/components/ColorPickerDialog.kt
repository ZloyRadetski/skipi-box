// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.skipi.ui.components.ColorPickerDialog as SharedColorPickerDialog

@Composable
fun ColorPickerDialog(
    show: Boolean,
    title: String,
    initialColor: Color,
    onDismissRequest: () -> Unit,
    onColorSelected: (Color) -> Unit,
) = SharedColorPickerDialog(
    show = show,
    title = title,
    initialColor = initialColor,
    onDismissRequest = onDismissRequest,
    onColorSelected = onColorSelected,
)
