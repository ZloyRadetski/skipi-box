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
import app.modes.normalizeFontFamilyMode

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.Bold),
)

val GolosTextFontFamily = FontFamily(
    Font(R.font.golos_text, FontWeight.Normal),
    Font(R.font.golos_text, FontWeight.Medium),
    Font(R.font.golos_text, FontWeight.Bold),
)

val ManropeFontFamily = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.Bold),
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
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
