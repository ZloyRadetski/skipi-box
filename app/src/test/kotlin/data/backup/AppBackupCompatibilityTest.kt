// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.backup

import app.AppState
import app.modes.ConnectionDisplayModeCompact
import app.modes.ProxyServerListLayoutDouble
import app.modes.ProxyServerListLayoutMultiple
import features.proxy.server.model.HTTP
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackupCompatibilityTest {
    @Test
    fun restoresAndReencodesLiteralV1HomeFixture() {
        val input = javaClass.getResourceAsStream("/data/backup/app-backup-v1-home.json")!!
            .bufferedReader()
            .use { it.readText() }

        val preview = decodeAppBackup(input).toRestorePreview()
        val restored = preview.restoredState
        assertEquals(1, preview.backup.version)
        assertEquals(52, restored.selectedProxyServerId)
        assertEquals(3, restored.proxyServerListLayout)
        assertTrue(restored.enableLocalProxyAuth)
        assertEquals("Work subscription", restored.subscriptionGroups.single().name)
        assertEquals("Work profile", restored.subscriptionGroups.single().profileTitle)
        assertEquals(1048576L, restored.subscriptionGroups.single().trafficTotalBytes)
        assertEquals(52, restored.proxyServers.single().id)
        assertEquals(42, restored.proxyServers.single().groupId)
        val server = restored.proxyServers.single().server as? HTTP
        assertNotNull(server)
        assertEquals("Fixture endpoint", server!!.remarks)
        assertEquals("proxy.example.com", server.server)
        assertEquals("8080", server.port)
        assertEquals(6, restored.activeTrafficConfigId)
        assertEquals("Mobile profile", restored.trafficConfigs.single().name)
        assertEquals(listOf("com.example.browser"), restored.proxyAppListSelectedApps)

        val output = encodeAppBackup(
            restored.toAppBackupFile(
                createdAtMillis = preview.backup.createdAtMillis,
                appVersionName = preview.backup.appVersionName,
                appVersionCode = preview.backup.appVersionCode,
            ),
        )
        val outputJson = Json.parseToJsonElement(output).jsonObject
        val outputData = outputJson.getValue("data").jsonObject
        val outputSettings = outputData.getValue("settings").jsonObject
        val outputServerPayload = outputData.getValue("proxyServers").jsonArray
            .single().jsonObject.getValue("payload").jsonObject

        assertEquals("skipi-backup", outputJson.getValue("format").jsonPrimitive.content)
        assertEquals(1, outputJson.getValue("version").jsonPrimitive.content.toInt())
        assertTrue(outputSettings.getValue("enableLocalProxyAuth").jsonPrimitive.content.toBoolean())
        assertFalse(outputJson.containsKey("futureRootField"))
        assertFalse(outputData.containsKey("futureDataField"))
        assertFalse(outputSettings.containsKey("futureSettingsField"))
        assertFalse(outputServerPayload.containsKey("futureProtocolField"))

        val roundTrip = decodeAppBackup(output).toRestorePreview().restoredState
        assertEquals(restored.selectedProxyServerId, roundTrip.selectedProxyServerId)
        assertEquals(restored.proxyServers.single().server, roundTrip.proxyServers.single().server)
        assertEquals(restored.subscriptionGroups.single(), roundTrip.subscriptionGroups.single())
        assertEquals(restored.activeTrafficConfigId, roundTrip.activeTrafficConfigId)
        assertEquals(restored.trafficConfigs.single().id, roundTrip.trafficConfigs.single().id)
        assertEquals(restored.proxyServerListLayout, roundTrip.proxyServerListLayout)
    }

    @Test
    fun minimalV1UsesCurrentDefaultsAndFutureVersionIsRejected() {
        val preview = decodeAppBackup(
            """{"format":"skipi-backup","version":1,"data":{}}""",
        ).toRestorePreview()
        val defaults = AppState()

        assertTrue(preview.restoredState.enableLocalProxyAuth)
        assertEquals(defaults.connectionDisplayMode, preview.restoredState.connectionDisplayMode)
        assertEquals(defaults.pinConnectionPanelOnHome, preview.restoredState.pinConnectionPanelOnHome)
        assertEquals(defaults.classicShowFloatingPowerButton, preview.restoredState.classicShowFloatingPowerButton)
        assertEquals(defaults.proxyServerListLayout, preview.restoredState.proxyServerListLayout)
        assertEquals(defaults.enableSubscriptionSwipe, preview.restoredState.enableSubscriptionSwipe)
        assertEquals(defaults.showServerSearch, preview.restoredState.showServerSearch)
        assertEquals(defaults.enableAllProxyGroup, preview.restoredState.enableAllProxyGroup)
        assertEquals(defaults.showTunnelMemoryOnHome, preview.restoredState.showTunnelMemoryOnHome)
        assertEquals(defaults.proxyServerListSort, preview.restoredState.proxyServerListSort)
        assertEquals(defaults.selectedProxyServerId, preview.restoredState.selectedProxyServerId)
        assertEquals(defaults.subscriptionGroups, preview.restoredState.subscriptionGroups)
        assertEquals(defaults.trafficConfigs.single().id, preview.restoredState.activeTrafficConfigId)

        val futureBackup = decodeAppBackup(
            """{"format":"skipi-backup","version":2,"data":{}}""",
        )
        val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            futureBackup.toRestorePreview()
        }
        assertTrue(error.message.orEmpty().contains("newer version"))
    }

    @Test
    fun roundTripsNonDefaultHomeSettingsThroughBackupV1() {
        val originalState = AppState(
            connectionDisplayMode = ConnectionDisplayModeCompact,
            pinConnectionPanelOnHome = true,
            classicShowFloatingPowerButton = true,
            proxyServerListLayout = ProxyServerListLayoutDouble,
            enableSubscriptionSwipe = false,
            showServerSearch = true,
            enableAllProxyGroup = true,
            showTunnelMemoryOnHome = true,
        )

        val backupFile = originalState.toAppBackupFile(
            createdAtMillis = 1720000000000L,
            appVersionName = "1.8.0",
            appVersionCode = 10800,
        )
        val encoded = encodeAppBackup(backupFile)

        val json = Json.parseToJsonElement(encoded).jsonObject
        val settings = json.getValue("data").jsonObject.getValue("settings").jsonObject

        assertEquals(ConnectionDisplayModeCompact, settings.getValue("connectionDisplayMode").jsonPrimitive.content.toInt())
        assertTrue(settings.getValue("pinConnectionPanelOnHome").jsonPrimitive.content.toBoolean())
        assertTrue(settings.getValue("classicShowFloatingPowerButton").jsonPrimitive.content.toBoolean())
        assertEquals(ProxyServerListLayoutDouble, settings.getValue("proxyServerListLayout").jsonPrimitive.content.toInt())
        assertFalse(settings.getValue("enableSubscriptionSwipe").jsonPrimitive.content.toBoolean())
        assertTrue(settings.getValue("showServerSearch").jsonPrimitive.content.toBoolean())
        assertTrue(settings.getValue("enableAllProxyGroup").jsonPrimitive.content.toBoolean())
        assertTrue(settings.getValue("showTunnelMemoryOnHome").jsonPrimitive.content.toBoolean())

        val restoredState = decodeAppBackup(encoded).toRestorePreview().restoredState

        assertEquals(ConnectionDisplayModeCompact, restoredState.connectionDisplayMode)
        assertTrue(restoredState.pinConnectionPanelOnHome)
        assertTrue(restoredState.classicShowFloatingPowerButton)
        assertEquals(ProxyServerListLayoutDouble, restoredState.proxyServerListLayout)
        assertFalse(restoredState.enableSubscriptionSwipe)
        assertTrue(restoredState.showServerSearch)
        assertTrue(restoredState.enableAllProxyGroup)
        assertTrue(restoredState.showTunnelMemoryOnHome)

        val originalStateThreeColumns = originalState.copy(
            proxyServerListLayout = ProxyServerListLayoutMultiple,
        )
        val encodedThreeColumns = encodeAppBackup(
            originalStateThreeColumns.toAppBackupFile(
                createdAtMillis = 1720000000000L,
                appVersionName = "1.8.0",
                appVersionCode = 10800,
            ),
        )
        val restoredThreeColumns = decodeAppBackup(encodedThreeColumns).toRestorePreview().restoredState
        assertEquals(ProxyServerListLayoutMultiple, restoredThreeColumns.proxyServerListLayout)
        assertEquals(ConnectionDisplayModeCompact, restoredThreeColumns.connectionDisplayMode)
        assertTrue(restoredThreeColumns.pinConnectionPanelOnHome)
        assertTrue(restoredThreeColumns.classicShowFloatingPowerButton)
        assertFalse(restoredThreeColumns.enableSubscriptionSwipe)
        assertTrue(restoredThreeColumns.showServerSearch)
        assertTrue(restoredThreeColumns.enableAllProxyGroup)
        assertTrue(restoredThreeColumns.showTunnelMemoryOnHome)
    }
}
