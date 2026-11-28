// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import app.modes.BottomBarSizeSmall
import app.modes.ClassicButtonSizeGiant
import features.backup.decodeAppBackup
import features.backup.encodeAppBackup
import features.updater.AppUpdateChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** Preferences a person sets once and expects back after a restore. */
class BackupPreferencesTest {
    private fun restore(state: AppState): AppState {
        val backup = state.toAppBackupFile(createdAtMillis = 0L, appVersionName = "1.0", appVersionCode = 1)
        return decodeAppBackup(encodeAppBackup(backup)).toRestorePreview().restoredState
    }

    @Test
    fun backgroundBlurDefaultsToNoneAndComesBackAfterARestore() {
        assertEquals(0, AppState().backgroundPhotoBlurPercent)
        assertEquals(0, features.backup.AppBackupSettings().backgroundPhotoBlurPercent)

        val restored = restore(AppState(backgroundPhotoBlurPercent = 75, backgroundPhotoDimPercent = 30))

        assertEquals(75, restored.backgroundPhotoBlurPercent)
        assertEquals(30, restored.backgroundPhotoDimPercent)
    }

    @Test
    fun backupWithoutBackgroundBlurRestoresWithoutBlur() {
        val backup = AppState(backgroundPhotoBlurPercent = 75).toAppBackupFile(createdAtMillis = 0L, appVersionName = "1.0", appVersionCode = 1)
        val json = kotlinx.serialization.json.Json.parseToJsonElement(encodeAppBackup(backup)).jsonObject
        val settings = json.getValue("data").jsonObject.getValue("settings").jsonObject
        val old = JsonObject(json + ("data" to JsonObject(json.getValue("data").jsonObject + ("settings" to JsonObject(settings - "backgroundPhotoBlurPercent")))))

        val restored = decodeAppBackup(old.toString()).toRestorePreview().restoredState

        assertEquals(0, restored.backgroundPhotoBlurPercent)
    }

    @Test
    fun updateAndLayoutPreferencesComeBackAfterARestore() {
        val chosen = AppState(
            appUpdateChannel = AppUpdateChannel.Nightly,
            autoCheckAppUpdates = false,
            autoInstallAppUpdatesAtNight = false,
            updateAppViaProxy = false,
            bottomBarSize = BottomBarSizeSmall,
            classicButtonSize = ClassicButtonSizeGiant,
            fakeDnsIpPool = "198.19.0.0/16",
            fakeDnsPoolSize = 4096,
            hevTcpReadWriteTimeoutMillis = 120_000,
            resourceFileUserAgent = "my-agent/1",
        )

        val restored = restore(chosen)

        assertEquals(AppUpdateChannel.Nightly, restored.appUpdateChannel)
        assertEquals(false, restored.autoCheckAppUpdates)
        assertEquals(false, restored.autoInstallAppUpdatesAtNight)
        assertEquals(false, restored.updateAppViaProxy)
        assertEquals(BottomBarSizeSmall, restored.bottomBarSize)
        assertEquals(ClassicButtonSizeGiant, restored.classicButtonSize)
        assertEquals("198.19.0.0/16", restored.fakeDnsIpPool)
        assertEquals(4096, restored.fakeDnsPoolSize)
        assertEquals(120_000, restored.hevTcpReadWriteTimeoutMillis)
        assertEquals("my-agent/1", restored.resourceFileUserAgent)
    }

    @Test
    fun theFloatingButtonPositionSurvivesABackupAndNonsenseOrAbsenceMeansBottomEnd() {
        val moved = restore(AppState(floatingButtonX = 0.25f, floatingButtonY = 0f))
        assertEquals(0.25f, moved.floatingButtonX)
        assertEquals(0f, moved.floatingButtonY)

        val file = AppState().toAppBackupFile(0L, "1.0", 1)
        val broken = file.copy(
            data = file.data.copy(settings = file.data.settings.copy(floatingButtonX = 42f, floatingButtonY = -1f)),
        )
        assertEquals(1f, broken.toRestorePreview().restoredState.floatingButtonX)
        assertEquals(1f, broken.toRestorePreview().restoredState.floatingButtonY)

        val document = Json.parseToJsonElement(encodeAppBackup(file)).jsonObject
        val data = document.getValue("data").jsonObject
        val settings = data.getValue("settings").jsonObject
            .filterKeys { it != "floatingButtonX" && it != "floatingButtonY" }
        val old = JsonObject(document + ("data" to JsonObject(data + ("settings" to JsonObject(settings))))).toString()
        val restored = decodeAppBackup(old).toRestorePreview().restoredState
        assertEquals(1f, restored.floatingButtonX)
        assertEquals(1f, restored.floatingButtonY)
    }

    @Test
    fun aBackupFromBeforeTheseFieldsExistedRestoresTheDefaults() {
        val keys = listOf(
            "appUpdateChannel", "autoCheckAppUpdates", "autoInstallAppUpdatesAtNight", "bottomBarSize",
            "classicButtonSize", "fakeDnsIpPool", "fakeDnsPoolSize", "hevTcpReadWriteTimeoutMillis",
            "resourceFileUserAgent", "updateAppViaProxy",
        )
        val document = Json.parseToJsonElement(encodeAppBackup(AppState().toAppBackupFile(0L, "0.4.1", 323))).jsonObject
        val data = document.getValue("data").jsonObject
        val settings = data.getValue("settings").jsonObject.filterKeys { it !in keys }
        val old = JsonObject(document + ("data" to JsonObject(data + ("settings" to JsonObject(settings))))).toString()
        val defaults = AppState()

        val restored = decodeAppBackup(old).toRestorePreview().restoredState

        assertEquals(defaults.appUpdateChannel, restored.appUpdateChannel)
        assertEquals(defaults.autoCheckAppUpdates, restored.autoCheckAppUpdates)
        assertEquals(defaults.autoInstallAppUpdatesAtNight, restored.autoInstallAppUpdatesAtNight)
        assertEquals(defaults.updateAppViaProxy, restored.updateAppViaProxy)
        assertEquals(defaults.bottomBarSize, restored.bottomBarSize)
        assertEquals(defaults.classicButtonSize, restored.classicButtonSize)
        assertEquals(defaults.fakeDnsIpPool, restored.fakeDnsIpPool)
        assertEquals(defaults.fakeDnsPoolSize, restored.fakeDnsPoolSize)
        assertEquals(defaults.hevTcpReadWriteTimeoutMillis, restored.hevTcpReadWriteTimeoutMillis)
        assertEquals(defaults.resourceFileUserAgent, restored.resourceFileUserAgent)
    }

    @Test
    fun nonsenseValuesFallBackToTheDefaultsInsteadOfBreakingTheApp() {
        val file = AppState().toAppBackupFile(0L, "1.0", 1)
        val broken = file.copy(
            data = file.data.copy(
                settings = file.data.settings.copy(
                    appUpdateChannel = "weekly",
                    bottomBarSize = 9,
                    classicButtonSize = -1,
                    fakeDnsIpPool = " ",
                    fakeDnsPoolSize = 0,
                    hevTcpReadWriteTimeoutMillis = -5,
                    resourceFileUserAgent = "",
                ),
            ),
        )
        val defaults = AppState()

        val restored = broken.toRestorePreview().restoredState

        assertEquals(defaults.appUpdateChannel, restored.appUpdateChannel)
        assertEquals(defaults.bottomBarSize, restored.bottomBarSize)
        assertEquals(defaults.classicButtonSize, restored.classicButtonSize)
        assertEquals(defaults.fakeDnsIpPool, restored.fakeDnsIpPool)
        assertEquals(defaults.fakeDnsPoolSize, restored.fakeDnsPoolSize)
        assertEquals(defaults.hevTcpReadWriteTimeoutMillis, restored.hevTcpReadWriteTimeoutMillis)
        assertEquals(defaults.resourceFileUserAgent, restored.resourceFileUserAgent)
    }
}
