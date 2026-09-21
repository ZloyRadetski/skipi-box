// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight

object ThemedTypography {
    var weightShift by mutableIntStateOf(0)
        private set

    fun updateWeightShift(newShift: Int) {
        if (weightShift != newShift) {
            weightShift = newShift
        }
    }
}

@Composable
fun themedFontWeight(requested: FontWeight): FontWeight {
    val shift = ThemedTypography.weightShift
    return if (shift == 0) {
        requested
    } else {
        FontWeight((requested.weight + shift).coerceIn(100, 900))
    }
}
