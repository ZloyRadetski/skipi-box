// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import app.AppState
import app.skipi.app.model.LocalProxyPreferences
import app.skipi.app.model.PersistedSettings
import data.backup.toAppBackupFile
import data.backup.toRestorePreview
import data.repository.applyPersistedSettings
import data.repository.toPersistedSettings
import platform.LocalProxyAuthMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LocalProxyHttpPersistenceTest {
    private val configured = AppState(
        enableLocalHttpProxy = true,
        localHttpProxyPort = "21001",
        localProxyAuthMode = LocalProxyAuthMode.FromJson,
        enableLocalHttpProxyAuth = true,
        localHttpProxyAuthMode = LocalProxyAuthMode.Manual,
        localHttpProxyUsername = "h-user",
        localHttpProxyPassword = "h-pass",
    )

    private fun AppState.localProxyFields() = listOf(
        enableLocalHttpProxy, localHttpProxyPort, localProxyAuthMode, enableLocalHttpProxyAuth,
        localHttpProxyAuthMode, localHttpProxyUsername, localHttpProxyPassword,
    )

    @Test
    fun theHttpInboundAndBothAuthModesSurviveThePersistedRecords() {
        val restored = AppState().applyPersistedSettings(configured.toPersistedSettings())

        assertEquals(configured.localProxyFields(), restored.localProxyFields())
    }

    @Test
    fun aRecordWrittenBeforeTheHttpSettingsLeavesThemAtTheirOwnValues() {
        val legacy = PersistedSettings(
            localProxy = LocalProxyPreferences(
                port = "10808",
                dynamicPort = false,
                listenAllInterfaces = false,
                authenticationEnabled = true,
                username = "u",
                password = "p",
            ),
        )

        val restored = configured.applyPersistedSettings(legacy)

        assertEquals(configured.localProxyFields(), restored.localProxyFields())
    }

    @Test
    fun theHttpInboundAndBothAuthModesSurviveABackup() {
        val file = configured.toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1)

        val restored = file.toRestorePreview().restoredState

        assertEquals(configured.localProxyFields(), restored.localProxyFields())
    }

    @Test
    fun aBackupWithoutTheHttpSettingsRestoresTheirDefaults() {
        val defaults = AppState()
        val file = configured.toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1)
        val oldSettings = file.data.settings.copy(
            enableLocalHttpProxy = false,
            localHttpProxyPort = "10809",
            localProxyAuthMode = "Auto",
            enableLocalHttpProxyAuth = false,
            localHttpProxyAuthMode = "Auto",
            localHttpProxyUsername = "",
            localHttpProxyPassword = "",
        )

        val restored = file.copy(data = file.data.copy(settings = oldSettings)).toRestorePreview().restoredState

        assertFalse(restored.enableLocalHttpProxy)
        assertEquals("10809", restored.localHttpProxyPort)
        assertEquals(LocalProxyAuthMode.Auto, restored.localProxyAuthMode)
        assertFalse(restored.enableLocalHttpProxyAuth)
        assertEquals(false, restored.localHttpProxyUsername.isEmpty(), "a blank stored login is regenerated, never left empty")
        assertEquals(defaults.localHttpProxyPort, restored.localHttpProxyPort)
    }

    @Test
    fun anUnknownBackedUpAuthModeFallsBackToAuto() {
        val file = configured.toAppBackupFile(createdAtMillis = 1L, appVersionName = "1", appVersionCode = 1)
        val future = file.data.settings.copy(localProxyAuthMode = "FromTheFuture", localHttpProxyAuthMode = "")

        val restored = file.copy(data = file.data.copy(settings = future)).toRestorePreview().restoredState

        assertEquals(LocalProxyAuthMode.Auto, restored.localProxyAuthMode)
        assertEquals(LocalProxyAuthMode.Auto, restored.localHttpProxyAuthMode)
    }
}
