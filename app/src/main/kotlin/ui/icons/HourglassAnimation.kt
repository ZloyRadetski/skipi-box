// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.ui.icons.AnimatedHourglassIcon as SharedAnimatedHourglassIcon
import app.skipi.ui.icons.StaticHourglass as SharedStaticHourglass

@Composable
fun AnimatedHourglassIcon(modifier: Modifier = Modifier, color: Color = Color.Unspecified, isPinging: Boolean = true, size: Dp = 20.dp) =
    SharedAnimatedHourglassIcon(modifier, color, isPinging, size)

@Composable
fun StaticHourglass(modifier: Modifier = Modifier, color: Color = Color.Unspecified, size: Dp = 20.dp) =
    SharedStaticHourglass(modifier, color, size)
