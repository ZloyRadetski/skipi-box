// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.modes.FontSizeModeSmall
import app.modes.FontWeightModeBold
import kotlin.test.Test
import kotlin.test.assertEquals

class AppearanceTypographyStylesTest {
    @Test
    fun missingFontSizeModeUsesAndroidSmallDefault() {
        val colors = AppColors(
            background = Color.Black,
            onBackground = Color.White,
            accent = Color.Blue,
            onAccent = Color.White,
            surface = Color.DarkGray,
            onSurface = Color.White,
            surfaceVariant = Color.Gray,
            onSurfaceVariant = Color.LightGray,
            isDark = true,
        )
        val styles = resolveAppearanceTypographyStyles(
            baseStyles = listOf(TextStyle(fontSize = 20.sp)),
            colors = colors,
            fontFamily = null,
            fontSizeMode = null,
            fontWeightMode = null,
        )

        assertEquals(FontSizeModeSmall / 100f, styles.fontSizeScale)
        assertEquals(20.sp, styles.main.single().fontSize)
        assertEquals(20.sp * (FontSizeModeSmall / 100f), styles.popup.single().fontSize)
    }

    @Test
    fun mainAndPopupStylesShareFontWeightAndColorButOnlyPopupScales() {
        val base = listOf(TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal))
        val colors = AppColors(
            background = Color.Black,
            onBackground = Color.White,
            accent = Color.Blue,
            onAccent = Color.White,
            surface = Color.DarkGray,
            onSurface = Color(0xFFABCDEF),
            surfaceVariant = Color.Gray,
            onSurfaceVariant = Color.LightGray,
            isDark = true,
        )

        val styles = resolveAppearanceTypographyStyles(
            baseStyles = base,
            colors = colors,
            fontFamily = FontFamily.Monospace,
            fontSizeMode = 55,
            fontWeightMode = FontWeightModeBold,
        )

        assertEquals(1, styles.main.size)
        assertEquals(1, styles.popup.size)
        assertEquals(20.sp, styles.main.single().fontSize)
        assertEquals(24.sp, styles.main.single().lineHeight)
        assertEquals(11.sp, styles.popup.single().fontSize)
        assertEquals(24.sp * 0.55f, styles.popup.single().lineHeight)
        assertEquals(FontFamily.Monospace, styles.main.single().fontFamily)
        assertEquals(FontFamily.Monospace, styles.popup.single().fontFamily)
        assertEquals(FontWeight.Bold, styles.main.single().fontWeight)
        assertEquals(FontWeight.Bold, styles.popup.single().fontWeight)
        assertEquals(colors.onSurface, styles.main.single().color)
        assertEquals(colors.onSurface, styles.popup.single().color)
        assertEquals(300, styles.fontWeightShift)
        assertEquals(0.55f, styles.fontSizeScale)
    }
}
