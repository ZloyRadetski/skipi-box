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
    fun theCloseButtonQuitsUnlessTheUserChoseTheBackground() {
        assertEquals(DesktopWindowCloseAction.ExitApplication, desktopWindowCloseAction(DesktopAppSettings()))
        assertEquals(
            DesktopWindowCloseAction.HideToBackground,
            desktopWindowCloseAction(DesktopAppSettings(closeToBackground = true)),
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
    fun theChoiceIsSavedAndOlderSettingsFilesMeanQuit() {
        val directory = createTempDirectory("skipi-window-settings")
        val path = directory.resolve("settings.json")
        DesktopSettingsLibraries.save(path, DesktopAppSettings(closeToBackground = true)).getOrThrow()
        assertTrue(DesktopSettingsLibraries.load(path).getOrThrow().closeToBackground)

        val legacy = directory.resolve("legacy.json").apply { writeText("""{"themeMode":"Dark"}""") }
        assertFalse(DesktopSettingsLibraries.load(legacy).getOrThrow().closeToBackground)
    }

    @Test
    fun theChoiceBelongsToThisMachineLikeLaunchOnLogin() {
        fun restore(data: DesktopUserData, current: DesktopUserData) = decodeAppBackup(
            encodeAppBackup(data.toAppBackupFile(createdAtMillis = 1L, appVersionName = "0.5.0", appVersionCode = 1)),
        ).toDesktopRestorePreview(current).userData.settings

        val chosenHere = DesktopUserData(settings = DesktopAppSettings(closeToBackground = true))
        assertFalse(restore(chosenHere, current = DesktopUserData()).closeToBackground)
        assertTrue(restore(DesktopUserData(), current = chosenHere).closeToBackground)
        assertTrue(DesktopAppSettings(closeToBackground = true).withVpnSettingsReset().closeToBackground)
    }
}
