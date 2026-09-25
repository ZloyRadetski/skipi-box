// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import app.skipi.ui.text.ThemedTypography as SharedThemedTypography
import app.skipi.ui.text.themedFontWeight as sharedThemedFontWeight

/**
 * Compatibility facade for Android-only callers. The state itself belongs to
 * shared UI so that Android and shared-window compositions cannot drift.
 */
object ThemedTypography {
    val weightShift: Int
        get() = SharedThemedTypography.weightShift

    fun updateWeightShift(newShift: Int) {
        SharedThemedTypography.updateWeightShift(newShift)
    }
}

/**
 * Resolves a requested font weight against the user's font weight preference.
 * Use this instead of raw [FontWeight] constants so that explicit weights keep
 * their relative difference while still following the global setting.
 */
@Composable
fun themedFontWeight(requested: FontWeight): FontWeight = sharedThemedFontWeight(requested)
