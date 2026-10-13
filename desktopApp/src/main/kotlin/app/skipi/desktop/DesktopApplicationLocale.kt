// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.util.Locale

/** Applies a persisted language tag and restores the process locale captured at startup. */
class DesktopApplicationLocale(
    systemLocale: Locale,
    private val applyLocale: (Locale) -> Unit,
) {
    private val startupLocale = systemLocale

    fun apply(languageTag: String?) {
        val locale = languageTag
            ?.takeIf(String::isNotBlank)
            ?.let(Locale::forLanguageTag)
            ?: startupLocale
        applyLocale(locale)
    }
}
