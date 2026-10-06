// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.text

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.font.FontWeight

fun TextStyle.scaleSkipiFontSize(scaleFactor: Float): TextStyle {
    val newFontSize = if (scaleFactor != 1.0f && fontSize.isSpecified) fontSize * scaleFactor else fontSize
    val newLineHeight = if (scaleFactor != 1.0f && lineHeight.isSpecified) lineHeight * scaleFactor else lineHeight
    return copy(fontSize = newFontSize, lineHeight = newLineHeight)
}

fun TextStyle.applySkipiFontAndWeight(fontFamily: androidx.compose.ui.text.font.FontFamily?, targetWeight: FontWeight?): TextStyle {
    val newFontFamily = fontFamily ?: this.fontFamily
    val currentWeight = this.fontWeight ?: FontWeight.Normal
    val newWeight = if (targetWeight == null) {
        this.fontWeight
    } else {
        val shift = targetWeight.weight - FontWeight.Normal.weight
        FontWeight((currentWeight.weight + shift).coerceIn(100, 900))
    }
    return copy(fontFamily = newFontFamily, fontWeight = newWeight)
}

