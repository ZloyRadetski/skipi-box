// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.skipi.ui.components.draggedCardShadow as sharedDraggedCardShadow

fun Modifier.draggedCardShadow(alpha: Float, color: Color): Modifier = sharedDraggedCardShadow(alpha, color)
