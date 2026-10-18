// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.text

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.R
import app.skipi.ui.text.fontFamilyAssetName
import app.skipi.ui.text.resolveFontWeight as sharedResolveFontWeight

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontVariation

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight): Font = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
    ),
)

private fun createVariableFontFamily(resId: Int): FontFamily = FontFamily(
    variableFont(resId, FontWeight.Thin),
    variableFont(resId, FontWeight.ExtraLight),
    variableFont(resId, FontWeight.Light),
    variableFont(resId, FontWeight.Normal),
    variableFont(resId, FontWeight.Medium),
    variableFont(resId, FontWeight.SemiBold),
    variableFont(resId, FontWeight.Bold),
    variableFont(resId, FontWeight.ExtraBold),
    variableFont(resId, FontWeight.Black),
)

val InterFontFamily = createVariableFontFamily(R.font.inter)
val GolosTextFontFamily = createVariableFontFamily(R.font.golos_text)
val ManropeFontFamily = createVariableFontFamily(R.font.manrope)
val JetBrainsMonoFontFamily = createVariableFontFamily(R.font.jetbrains_mono)
val ClimateCrisisFontFamily = FontFamily(
    Font(R.font.climate_crisis, FontWeight.Normal),
)
val UnboundedFontFamily = createVariableFontFamily(R.font.unbounded)
val OnestFontFamily = createVariableFontFamily(R.font.onest)

fun resolveFontFamily(fontFamilyMode: Int): FontFamily? = when (fontFamilyAssetName(fontFamilyMode)) {
    "inter.ttf" -> InterFontFamily
    "golos_text.ttf" -> GolosTextFontFamily
    "manrope.ttf" -> ManropeFontFamily
    "jetbrains_mono.ttf" -> JetBrainsMonoFontFamily
    "climate_crisis.ttf" -> ClimateCrisisFontFamily
    "unbounded.ttf" -> UnboundedFontFamily
    "onest.ttf" -> OnestFontFamily
    else -> null
}

fun resolveFontWeight(fontWeightMode: Int): FontWeight? = sharedResolveFontWeight(fontWeightMode)
