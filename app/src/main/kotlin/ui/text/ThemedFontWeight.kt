// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight

/**
 * Global holder for the font weight shift configured by the user's font weight
 * setting. Kept outside composition locals because miuix window components
 * (dialogs, dropdown popups) re-create compositions where app-provided locals
 * do not reach; a snapshot state read works everywhere.
 */
object ThemedTypography {
    var weightShift by mutableIntStateOf(0)
        private set

    fun updateWeightShift(newShift: Int) {
        if (weightShift != newShift) {
            weightShift = newShift
        }
    }
}

/**
 * Resolves a requested font weight against the user's font weight preference.
 * Use this instead of raw [FontWeight] constants so that explicit weights keep
 * their relative difference while still following the global setting.
 */
@Composable
fun themedFontWeight(requested: FontWeight): FontWeight {
    val shift = ThemedTypography.weightShift
    return if (shift == 0) {
        requested
    } else {
        FontWeight((requested.weight + shift).coerceIn(100, 900))
    }
}
