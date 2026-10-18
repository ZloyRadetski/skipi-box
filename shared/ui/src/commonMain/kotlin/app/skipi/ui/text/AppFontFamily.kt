// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.text

import app.modes.FontFamilyModeClimateCrisis
import app.modes.FontFamilyModeDefault
import app.modes.FontFamilyModeGolosText
import app.modes.FontFamilyModeInter
import app.modes.FontFamilyModeJetBrainsMono
import app.modes.FontFamilyModeManrope
import app.modes.FontFamilyModeOnest
import app.modes.FontFamilyModeUnbounded
import app.modes.normalizeFontFamilyMode

/** Returns the bundled font asset Android associates with a persisted font-family mode. */
fun fontFamilyAssetName(fontFamilyMode: Int): String? = when (normalizeFontFamilyMode(fontFamilyMode)) {
    FontFamilyModeDefault -> null
    FontFamilyModeInter -> "inter.ttf"
    FontFamilyModeGolosText -> "golos_text.ttf"
    FontFamilyModeManrope -> "manrope.ttf"
    FontFamilyModeJetBrainsMono -> "jetbrains_mono.ttf"
    FontFamilyModeClimateCrisis -> "climate_crisis.ttf"
    FontFamilyModeUnbounded -> "unbounded.ttf"
    FontFamilyModeOnest -> "onest.ttf"
    else -> null
}
