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
import app.modes.FontWeightModeBold
import app.modes.FontWeightModeDefault
import app.modes.FontWeightModeLight
import app.modes.FontWeightModeMedium
import app.modes.FontWeightModeNormal
import app.modes.FontWeightModeSemiBold
import app.modes.normalizeFontFamilyMode
import app.modes.normalizeFontWeightMode

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Light),
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
    Font(R.font.inter, FontWeight.ExtraBold),
)

val GolosTextFontFamily = FontFamily(
    Font(R.font.golos_text, FontWeight.Light),
    Font(R.font.golos_text, FontWeight.Normal),
    Font(R.font.golos_text, FontWeight.Medium),
    Font(R.font.golos_text, FontWeight.SemiBold),
    Font(R.font.golos_text, FontWeight.Bold),
    Font(R.font.golos_text, FontWeight.ExtraBold),
)

val ManropeFontFamily = FontFamily(
    Font(R.font.manrope, FontWeight.Light),
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
    Font(R.font.manrope, FontWeight.ExtraBold),
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Light),
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
    Font(R.font.jetbrains_mono, FontWeight.ExtraBold),
)

val ClimateCrisisFontFamily = FontFamily(
    Font(R.font.climate_crisis, FontWeight.Normal),
)

fun resolveFontFamily(fontFamilyMode: Int): FontFamily? = when (normalizeFontFamilyMode(fontFamilyMode)) {
    FontFamilyModeInter -> InterFontFamily
    FontFamilyModeGolosText -> GolosTextFontFamily
    FontFamilyModeManrope -> ManropeFontFamily
    FontFamilyModeJetBrainsMono -> JetBrainsMonoFontFamily
    FontFamilyModeClimateCrisis -> ClimateCrisisFontFamily
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
