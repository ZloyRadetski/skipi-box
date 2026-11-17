// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.model

/** The language mode of a stored tag; region and case are ignored. Null for a tag SKIPI has no translation for. */
fun languageModeOfTagOrNull(tag: String): Int? = when (tag.lowercase().substringBefore('-')) {
    "en" -> app.modes.LanguageModeEnglish
    "zh" -> app.modes.LanguageModeChinese
    "ru" -> app.modes.LanguageModeRussian
    "fa" -> app.modes.LanguageModePersian
    else -> null
}

/** No tag, or one without a translation, follows the system language. */
fun languageModeOfTag(tag: String?): Int =
    tag?.let(::languageModeOfTagOrNull) ?: app.modes.LanguageModeSystem

/** The tag stored for a language mode; null for "system". */
fun languageTagOfMode(mode: Int): String? = when (mode) {
    app.modes.LanguageModeEnglish -> "en"
    app.modes.LanguageModeChinese -> "zh"
    app.modes.LanguageModeRussian -> "ru"
    app.modes.LanguageModePersian -> "fa"
    else -> null
}
