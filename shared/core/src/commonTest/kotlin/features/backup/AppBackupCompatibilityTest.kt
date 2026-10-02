// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.routing.model.RouteRule
import features.settings.servicecontrol.ServiceControlSchedule
import features.settings.servicecontrol.ServiceControlSettings
import features.settings.servicecontrol.ServiceControlWifi
import features.settings.servicecontrol.ServiceControlWifiRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AppBackupCompatibilityTest {
    @Test
    fun decodesLegacyV1FixtureAndReencodesStableFields() {
        val input = """
            {
              "format": "skipi-backup",
              "version": 1,
              "createdAtMillis": 1720000000000,
              "appVersionName": "1.8.0",
              "appVersionCode": 10800,
              "futureRootField": "ignored",
              "data": {
                "settings": {"selectedProxyServerId": 52, "proxyServerListLayout": 3, "futureSetting": true},
                "subscriptionGroups": [{"id": 42, "name": "Work subscription", "url": "https://example.com/subscription", "profileTitle": "Work profile", "trafficTotalBytes": 1048576}],
                "proxyServers": [{"id": 52, "groupId": 42, "protocol": "http", "payload": {"remarks": "Fixture endpoint", "server": "proxy.example.com", "port": "8080", "futureProtocolField": "ignored"}}],
                "trafficConfigs": [{"id": 6, "name": "Mobile profile", "rawConfig": "[General]\\ndns-server = system"}],
                "activeTrafficConfigId": 6,
                "proxyAppListSelectedApps": ["com.example.browser"],
                "futureDataField": [1, 2, 3]
              }
            }
        """.trimIndent()

        val migrated = decodeAppBackup(input).migrateAppBackup()
        val encoded = encodeAppBackup(migrated)
        val json = Json.parseToJsonElement(encoded).jsonObject
        val data = json.getValue("data").jsonObject
        val server = data.getValue("proxyServers").jsonArray.single().jsonObject
        val payload = server.getValue("payload").jsonObject

        assertEquals(AppBackupFormat, json.getValue("format").jsonPrimitive.content)
        assertEquals(1, json.getValue("version").jsonPrimitive.content.toInt())
        assertEquals(52, data.getValue("settings").jsonObject.getValue("selectedProxyServerId").jsonPrimitive.content.toInt())
        assertEquals("Work subscription", data.getValue("subscriptionGroups").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals(42, server.getValue("groupId").jsonPrimitive.content.toInt())
        assertEquals("Fixture endpoint", payload.getValue("remarks").jsonPrimitive.content)
        assertEquals(6, data.getValue("activeTrafficConfigId").jsonPrimitive.content.toInt())
        assertEquals("com.example.browser", data.getValue("proxyAppListSelectedApps").jsonArray.single().jsonPrimitive.content)
        assertFalse(json.containsKey("futureRootField"))
        assertFalse(data.containsKey("futureDataField"))
        assertFalse(data.getValue("settings").jsonObject.containsKey("futureSetting"))
        // Protocol payload is deliberately opaque here; protocol-aware cleanup belongs to the app mapper.
        assertTrue(payload.containsKey("futureProtocolField"))
    }

    @Test
    fun roundTripsV1DataAndPreservesFieldNames() {
        val file = AppBackupFile(
            format = AppBackupFormat,
            version = CurrentAppBackupVersion,
            createdAtMillis = 1234L,
            appVersionName = "1.8.0",
            appVersionCode = 10800,
            data = AppBackupData(
                settings = AppBackupSettings(selectedProxyServerId = 52, enableLocalProxyAuth = false),
                proxyServers = listOf(AppBackupProxyServer(id = 52, groupId = 42, protocol = "http")),
                activeTrafficConfigId = 6,
            ),
        )

        val encoded = encodeAppBackup(file)
        val decoded = decodeAppBackup(encoded)
        val root = Json.parseToJsonElement(encoded).jsonObject
        val data = root.getValue("data").jsonObject
        val settings = data.getValue("settings").jsonObject

        assertEquals(file, decoded)
        assertTrue(settings.containsKey("enableLocalProxyAuth"))
        assertTrue(data.containsKey("activeTrafficConfigId"))
        assertEquals(52, settings.getValue("selectedProxyServerId").jsonPrimitive.content.toInt())
    }

    @Test
    fun validatesFormatAndSupportedVersion() {
        assertFailsWith<IllegalArgumentException> {
            AppBackupFile(format = "other", version = 1).migrateAppBackup()
        }
        assertFailsWith<IllegalArgumentException> {
            AppBackupFile(format = AppBackupFormat, version = 0).migrateAppBackup()
        }
        val newer = assertFailsWith<IllegalArgumentException> {
            AppBackupFile(format = AppBackupFormat, version = 2).migrateAppBackup()
        }
        assertTrue(newer.message.orEmpty().contains("newer version"))
    }

    @Test
    fun sharedMappersRoundTripPortableModels() {
        val serviceControl = ServiceControlSettings(
            enabled = true,
            schedule = ServiceControlSchedule(enabled = true, startCron = "0 8 * * *", stopCron = "0 22 * * *"),
            wifi = ServiceControlWifi(
                enabled = true,
                connectStart = ServiceControlWifiRule(enabled = true, ssids = listOf("Home"), bssids = listOf("aa:bb")),
            ),
        )
        val routeRule = RouteRule(
            id = 8,
            remarks = "Work domains",
            outboundTag = "direct",
            domain = listOf("domain:example.org"),
            ip = listOf("192.0.2.0/24"),
            process = listOf("browser"),
            port = "443",
            protocol = "tcp",
            network = "tcp",
            enabled = false,
        )
        val trafficConfig = TrafficConfigState(
            id = 6,
            name = "Mobile",
            rawConfig = "[General]\\ndns-server = system",
            sourceUrl = "https://example.org/profile",
            resourceSettings = TrafficConfigResourceSettings(userAgent = "Fixture agent"),
        )

        assertEquals(serviceControl, serviceControl.toAppBackupServiceControl().toServiceControlSettings())
        assertEquals(routeRule, routeRule.toAppBackupRouteRule().toRouteRule())
        assertEquals(trafficConfig, trafficConfig.toAppBackupTrafficConfig().toTrafficConfigState())
    }
}
