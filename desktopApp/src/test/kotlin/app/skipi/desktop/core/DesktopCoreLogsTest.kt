// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.config.ConfigProfileLibrary
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Android's log settings (level, access log, retention) wired to Desktop's Xray config and viewers. */
class DesktopCoreLogsTest {
    private val directory: Path = Files.createTempDirectory("skipi-desktop-logs-")
    private val paths = DesktopCoreLogPaths(directory)

    @AfterTest
    fun tearDown() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun settingsDefaultsMatchAndroid() {
        val settings = DesktopAppSettings()

        assertEquals("error", settings.coreLogLevel)
        assertFalse(settings.enableAccessLog)
        assertEquals(7, settings.logRetentionDays)
    }

    @Test
    fun retentionOutsideTheAndroidChoicesFallsBackToTheDefault() {
        assertEquals(30, DesktopAppSettings(logRetentionDays = 30).normalized().logRetentionDays)
        assertEquals(0, DesktopAppSettings(logRetentionDays = 0).normalized().logRetentionDays)
        assertEquals(7, DesktopAppSettings(logRetentionDays = 5).normalized().logRetentionDays)
    }

    @Test
    fun logFilesLiveInAnXrayLogsFolderLikeAndroid() {
        assertEquals(directory.resolve("xray").resolve("logs").resolve("error.log").toString(), paths.errorLogPath)
        assertEquals(directory.resolve("xray").resolve("logs").resolve("access.log").toString(), paths.accessLogPath)
    }

    private fun generatedLog(settings: DesktopAppSettings): JsonObject {
        val server = Socks(remarks = "Proxy", server = "proxy.example", port = "1080")
        val json = assertNotNull(
            buildDesktopSelectedServerConfig(
                serverLibrary = DesktopServerLibrary(
                    selectedServerId = 1,
                    servers = listOf(DesktopStoredProxyServer(1, server.encodePersistedProxyServer())),
                ),
                configLibrary = ConfigProfileLibrary(),
                routing = RoutingConfigRecord(),
                options = settings.toLocalProxyXrayConfigOptions(logPaths = paths),
                trafficConfigContentById = emptyMap(),
                subscriptionPingUrl = "http://127.0.0.1:18086/probe",
                latencyByServerId = emptyMap(),
            ),
        ).getOrThrow()
        return Json.parseToJsonElement(json).jsonObject.getValue("log").jsonObject
    }

    @Test
    fun generatedConfigWritesTheErrorLogAndDisablesTheAccessLogByDefault() {
        val log = generatedLog(DesktopAppSettings())

        assertEquals("error", log.getValue("loglevel").jsonPrimitive.content)
        assertEquals("none", log.getValue("access").jsonPrimitive.content)
        assertEquals(paths.errorLogPath, log.getValue("error").jsonPrimitive.content)
        assertEquals("false", log.getValue("dnsLog").jsonPrimitive.content)
    }

    @Test
    fun accessLogAndDebugLevelReachTheConfig() {
        val log = generatedLog(DesktopAppSettings(coreLogLevel = "debug", enableAccessLog = true))

        assertEquals("debug", log.getValue("loglevel").jsonPrimitive.content)
        assertEquals(paths.accessLogPath, log.getValue("access").jsonPrimitive.content)
        assertEquals("true", log.getValue("dnsLog").jsonPrimitive.content)
    }

    @Test
    fun profileAndRawCustomConfigsUseTheSameLogSection() {
        val settings = DesktopAppSettings(coreLogLevel = "info", enableAccessLog = true)
        val options = settings.toLocalProxyXrayConfigOptions(logPaths = paths)
        val library = DesktopServerLibraries.select(
            DesktopServerLibraries.add(
                DesktopServerLibrary(),
                ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
            ),
            serverId = 1,
        )

        val profileLog = Json.parseToJsonElement(
            DesktopTrafficProfileXrayConfigFactory.build(
                profile = DesktopStoredConfig(id = 1, name = "P", content = "[Rule]\nFINAL,PROXY\n"),
                serverLibrary = library,
                options = options,
            ),
        ).jsonObject.getValue("log").jsonObject
        val customLog = Json.parseToJsonElement(
            buildDesktopCustomXrayConfig(
                Custom(remarks = "Raw", configJson = """{"log": {"loglevel": "none"}, "outbounds": []}"""),
                options,
            ),
        ).jsonObject.getValue("log").jsonObject

        for (log in listOf(profileLog, customLog)) {
            assertEquals("info", log.getValue("loglevel").jsonPrimitive.content)
            assertEquals(paths.accessLogPath, log.getValue("access").jsonPrimitive.content)
            assertEquals(paths.errorLogPath, log.getValue("error").jsonPrimitive.content)
        }
    }

    @Test
    fun hostTailsTheErrorLogAndOnlyTailsAccessLogWhenEnabled() = runBlocking {
        val logs = DesktopCoreLogs(paths)
        try {
            Files.createDirectories(Path.of(paths.errorLogPath).parent)
            Files.writeString(Path.of(paths.errorLogPath), "2026/09/11 20:40:05 [Error] boom\n")
            Files.writeString(Path.of(paths.accessLogPath), "2026/09/11 20:40:05 accepted a\n")

            logs.startTailers(DesktopAppSettings(enableAccessLog = false))
            val errors = withTimeout(3_000) { logs.core.entries.first { it.isNotEmpty() } }
            assertTrue(errors.single().message.endsWith("[Error] boom"))
            kotlinx.coroutines.delay(300)
            assertTrue(logs.access.entries.value.isEmpty(), "The access log must not be read while disabled")
            logs.stopTailers()

            logs.startTailers(DesktopAppSettings(enableAccessLog = true))
            val access = withTimeout(3_000) { logs.access.entries.first { it.isNotEmpty() } }
            assertTrue(access.single().message.endsWith("accepted a"))
        } finally {
            logs.stopTailers()
        }
    }

    @Test
    fun clearEmptiesViewersAndFiles() {
        val logs = DesktopCoreLogs(paths)
        Files.createDirectories(Path.of(paths.errorLogPath).parent)
        Files.writeString(Path.of(paths.errorLogPath), "2026/09/11 20:40:05 [Error] boom\n")
        logs.core.append("error", "boom", "2026-09-11 20:40:05")

        logs.clear()

        assertTrue(logs.core.entries.value.isEmpty())
        assertEquals("", Files.readString(Path.of(paths.errorLogPath)))
    }
}
