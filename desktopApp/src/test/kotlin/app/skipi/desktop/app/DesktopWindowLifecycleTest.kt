// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.backup.decodeAppBackup
import features.backup.encodeAppBackup
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowLifecycleTest {
    @Test
    fun theCloseButtonHidesToTheTrayUnlessTheUserChoseToQuit() {
        assertEquals(DesktopWindowCloseAction.HideToBackground, desktopWindowCloseAction(DesktopAppSettings()))
        assertEquals(
            DesktopWindowCloseAction.ExitApplication,
            desktopWindowCloseAction(DesktopAppSettings(closeToBackground = false)),
        )
    }

    @Test
    fun withoutATrayTheCloseButtonQuitsBecauseAHiddenWindowCouldNotComeBack() {
        assertEquals(
            DesktopWindowCloseAction.ExitApplication,
            desktopWindowCloseAction(DesktopAppSettings(closeToBackground = true), traySupported = false),
        )
    }

    @Test
    fun aHiddenStartCreatesNoWindowOnlyWhenTheBackgroundIsChosen() {
        assertTrue(desktopWindowShownAtStart(hidden = false, closeToBackground = false))
        assertTrue(desktopWindowShownAtStart(hidden = false, closeToBackground = true))
        // Without the choice a hidden start keeps the old minimized window.
        assertTrue(desktopWindowShownAtStart(hidden = true, closeToBackground = false))
        assertFalse(desktopWindowShownAtStart(hidden = true, closeToBackground = true))
    }

    @Test
    fun theChoiceIsSavedAndOlderSettingsFilesMeanHideToTheTray() {
        val directory = createTempDirectory("skipi-window-settings")
        val path = directory.resolve("settings.json")
        DesktopSettingsLibraries.save(path, DesktopAppSettings(closeToBackground = false)).getOrThrow()
        assertFalse(DesktopSettingsLibraries.load(path).getOrThrow().closeToBackground)

        val legacy = directory.resolve("legacy.json").apply { writeText("""{"themeMode":"Dark"}""") }
        assertTrue(DesktopSettingsLibraries.load(legacy).getOrThrow().closeToBackground)
    }

    @Test
    fun theChoiceBelongsToThisMachineLikeLaunchOnLogin() {
        fun restore(data: DesktopUserData, current: DesktopUserData) = decodeAppBackup(
            encodeAppBackup(data.toAppBackupFile(createdAtMillis = 1L, appVersionName = "0.5.0", appVersionCode = 1)),
        ).toDesktopRestorePreview(current).userData.settings

        val chosenHere = DesktopUserData(settings = DesktopAppSettings(closeToBackground = false))
        assertTrue(restore(chosenHere, current = DesktopUserData()).closeToBackground)
        assertFalse(restore(DesktopUserData(), current = chosenHere).closeToBackground)
        assertFalse(DesktopAppSettings(closeToBackground = false).withVpnSettingsReset().closeToBackground)
    }
}
