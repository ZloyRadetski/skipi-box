// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageTagModeTest {
    @Test
    fun aTagMapsToItsLanguageMode() {
        assertEquals(GeneralSettingsValues.LanguageEnglish, GeneralSettingsValues.languageModeOfTag("en"))
        assertEquals(GeneralSettingsValues.LanguageChinese, GeneralSettingsValues.languageModeOfTag("zh"))
        assertEquals(GeneralSettingsValues.LanguageRussian, GeneralSettingsValues.languageModeOfTag("ru"))
        assertEquals(GeneralSettingsValues.LanguagePersian, GeneralSettingsValues.languageModeOfTag("fa"))
    }

    @Test
    fun theRegionAndTheCaseDoNotMatter() {
        assertEquals(GeneralSettingsValues.LanguageChinese, GeneralSettingsValues.languageModeOfTag("zh-CN"))
        assertEquals(GeneralSettingsValues.LanguageRussian, GeneralSettingsValues.languageModeOfTag("RU-ru"))
    }

    @Test
    fun noTagOrAnUnknownOneFollowsTheSystem() {
        assertEquals(GeneralSettingsValues.LanguageSystem, GeneralSettingsValues.languageModeOfTag(null))
        assertEquals(GeneralSettingsValues.LanguageSystem, GeneralSettingsValues.languageModeOfTag("de"))
    }

    @Test
    fun aModeMapsBackToItsTag() {
        assertEquals("en", GeneralSettingsValues.languageTagOfMode(GeneralSettingsValues.LanguageEnglish))
        assertEquals("zh", GeneralSettingsValues.languageTagOfMode(GeneralSettingsValues.LanguageChinese))
        assertEquals("ru", GeneralSettingsValues.languageTagOfMode(GeneralSettingsValues.LanguageRussian))
        assertEquals("fa", GeneralSettingsValues.languageTagOfMode(GeneralSettingsValues.LanguagePersian))
        assertNull(GeneralSettingsValues.languageTagOfMode(GeneralSettingsValues.LanguageSystem))
    }
}
