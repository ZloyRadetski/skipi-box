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

/**
 * The saved language must be in effect before the first screen and before any text is read
 * outside composition (notifications); otherwise a restart falls back to the system language.
 */
internal fun applyDesktopStartupLanguage(
    settings: DesktopAppSettings,
    systemLocale: Locale = Locale.getDefault(),
    applyLocale: (Locale) -> Unit = Locale::setDefault,
) {
    DesktopApplicationLocale(systemLocale, applyLocale).apply(settings.languageTag)
}
