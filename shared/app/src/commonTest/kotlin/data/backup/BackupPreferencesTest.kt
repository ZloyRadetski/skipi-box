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
    fun updateAndLayoutPreferencesComeBackAfterARestore() {
        val chosen = AppState(
            appUpdateChannel = AppUpdateChannel.Nightly,
            autoCheckAppUpdates = false,
            autoInstallAppUpdatesAtNight = false,
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
        assertEquals(BottomBarSizeSmall, restored.bottomBarSize)
        assertEquals(ClassicButtonSizeGiant, restored.classicButtonSize)
        assertEquals("198.19.0.0/16", restored.fakeDnsIpPool)
        assertEquals(4096, restored.fakeDnsPoolSize)
        assertEquals(120_000, restored.hevTcpReadWriteTimeoutMillis)
        assertEquals("my-agent/1", restored.resourceFileUserAgent)
    }

    @Test
    fun aBackupFromBeforeTheseFieldsExistedRestoresTheDefaults() {
        val keys = listOf(
            "appUpdateChannel", "autoCheckAppUpdates", "autoInstallAppUpdatesAtNight", "bottomBarSize",
            "classicButtonSize", "fakeDnsIpPool", "fakeDnsPoolSize", "hevTcpReadWriteTimeoutMillis",
            "resourceFileUserAgent",
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
