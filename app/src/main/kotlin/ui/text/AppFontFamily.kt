// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.text

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.R
import app.modes.FontFamilyModeClimateCrisis
import app.modes.FontFamilyModeDefault
import app.modes.FontFamilyModeGolosText
import app.modes.FontFamilyModeInter
import app.modes.FontFamilyModeJetBrainsMono
import app.modes.FontFamilyModeManrope
import app.modes.FontFamilyModeOnest
import app.modes.FontFamilyModeUnbounded
import app.modes.FontWeightModeBold
import app.modes.FontWeightModeDefault
import app.modes.FontWeightModeLight
import app.modes.FontWeightModeMedium
import app.modes.FontWeightModeNormal
import app.modes.FontWeightModeSemiBold
import app.modes.normalizeFontFamilyMode
import app.modes.normalizeFontWeightMode

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

fun resolveFontFamily(fontFamilyMode: Int): FontFamily? = when (normalizeFontFamilyMode(fontFamilyMode)) {
    FontFamilyModeInter -> InterFontFamily
    FontFamilyModeGolosText -> GolosTextFontFamily
    FontFamilyModeManrope -> ManropeFontFamily
    FontFamilyModeJetBrainsMono -> JetBrainsMonoFontFamily
    FontFamilyModeClimateCrisis -> ClimateCrisisFontFamily
    FontFamilyModeUnbounded -> UnboundedFontFamily
    FontFamilyModeOnest -> OnestFontFamily
    else -> null
}

fun resolveFontWeight(fontWeightMode: Int): FontWeight? = when (normalizeFontWeightMode(fontWeightMode)) {
    FontWeightModeLight -> FontWeight.Light
    FontWeightModeNormal -> FontWeight.Normal
    FontWeightModeMedium -> FontWeight.Medium
    FontWeightModeSemiBold -> FontWeight.SemiBold
    FontWeightModeBold -> FontWeight.Bold
    else -> null
}
