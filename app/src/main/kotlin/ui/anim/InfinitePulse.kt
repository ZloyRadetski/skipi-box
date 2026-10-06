// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.anim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import app.skipi.ui.anim.rememberInfinitePulse as sharedRememberInfinitePulse

@Composable
fun rememberInfinitePulse(enabled: Boolean, initialValue: Float, targetValue: Float, durationMillis: Int, restingValue: Float = initialValue): State<Float> =
    sharedRememberInfinitePulse(enabled, initialValue, targetValue, durationMillis, restingValue)
