// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class GeneralSettingsCapabilitiesTest {
    @Test
    fun defaultsExposeEveryExistingAndroidGeneralSettingsControl() {
        assertEquals(
            setOf(
                GeneralSettingsControl.Language,
                GeneralSettingsControl.Haptics,
                GeneralSettingsControl.TrafficStatsNotifications,
                GeneralSettingsControl.ResourceFileNotifications,
                GeneralSettingsControl.DeletionConfirmation,
                GeneralSettingsControl.UrlSchemes,
                GeneralSettingsControl.BroadcastControl,
                GeneralSettingsControl.BackupAndReset,
            ),
            GeneralSettingsCapabilities().visibleControls,
        )
    }

    @Test
    fun desktopShowsEveryControlItCanHonourLikeAndroid() {
        assertEquals(
            setOf(
                GeneralSettingsControl.Language,
                GeneralSettingsControl.DeletionConfirmation,
                GeneralSettingsControl.UrlSchemes,
                GeneralSettingsControl.BackupAndReset,
                GeneralSettingsControl.CloseToBackground,
            ),
            GeneralSettingsCapabilities.Desktop.visibleControls,
        )
    }

    @Test
    fun closingToTheBackgroundIsADesktopWindowChoiceAndOffByDefault() {
        assertEquals(false, GeneralSettingsControl.CloseToBackground in GeneralSettingsCapabilities().visibleControls)
        assertEquals(
            false,
            GeneralSettingsState(
                languageMode = GeneralSettingsValues.LanguageSystem,
                enableHaptics = true,
                enableTrafficStatsNotification = false,
                trafficStatsNotificationRefreshIntervalSeconds = GeneralSettingsValues.MinTrafficRefreshSeconds,
                enableResourceFileNotifications = false,
                enableDeletionConfirmation = true,
                enableBroadcastControl = false,
            ).closeToBackground,
        )
    }
}
