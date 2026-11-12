// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_config_download_failed
import app.skipi.ui.resources.desktop_status_error_markers
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopTextTest {
    @Test
    fun failureMessagesAreRecognisedInEveryShippedLanguage() {
        val processLocale = Locale.getDefault()
        try {
            for (tag in listOf("en", "ru", "fa", "zh", "zh-CN")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                val failure = desktopText(Res.string.desktop_config_download_failed, "x")
                assertTrue(
                    desktopStatusMessageIsError(failure, desktopText(Res.string.desktop_status_error_markers)),
                    "$tag: $failure",
                )
            }
        } finally {
            Locale.setDefault(processLocale)
        }
    }

    @Test
    fun ordinaryStatusMessagesAreNotFailures() {
        assertFalse(desktopStatusMessageIsError("Config saved.", "error"))
    }

    @Test
    fun textFollowsTheProcessLocaleAndFillsPlaceholders() {
        val processLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            assertEquals("Failed to download the config: boom", desktopText(Res.string.desktop_config_download_failed, "boom"))
            Locale.setDefault(Locale.forLanguageTag("ru"))
            assertEquals("Не удалось загрузить конфиг: boom", desktopText(Res.string.desktop_config_download_failed, "boom"))
        } finally {
            Locale.setDefault(processLocale)
        }
    }
}
