// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ConfigProfileLibrary
import features.proxy.server.model.Custom
import features.proxy.server.model.encodePersistedProxyServer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyAuthMode

/** The login a raw JSON carries on its own inbounds reaches the Core config and the options hev reads. */
class DesktopLocalProxyJsonAuthPlanTest {
    private val rawJson = """
        {
          "inbounds": [
            {"protocol": "socks", "port": 1080, "settings": {"auth": "password", "users": [{"user": "json-socks", "pass": "json-socks-pass"}]}},
            {"protocol": "http", "port": 8080, "settings": {"accounts": [{"user": "json-http", "pass": "json-http-pass"}]}}
          ],
          "outbounds": [{"tag": "raw-out", "protocol": "freedom"}]
        }
    """.trimIndent()

    private fun library(json: String): DesktopServerLibrary = DesktopServerLibrary(
        selectedServerId = 10,
        servers = listOf(
            DesktopStoredProxyServer(
                10,
                Custom(remarks = "Raw", overrideInboundAndDns = true, configJson = json).encodePersistedProxyServer(),
            ),
        ),
    )

    private fun settings(socks: LocalProxyAuthMode, http: LocalProxyAuthMode) = DesktopAppSettings(
        useSystemProxy = false,
        useWindowsSystemProxy = false,
        enableLocalHttpProxy = true,
        localProxyPort = 20_480,
        localHttpProxyPort = 20_481,
        localProxyUsername = "stored-socks",
        localProxyPassword = "stored-socks-pass",
        localHttpProxyUsername = "stored-http",
        localHttpProxyPassword = "stored-http-pass",
    ).withSocksAuthMode(socks).withHttpAuthMode(http)

    private fun inbound(config: String, protocol: String): JsonObject =
        Json.parseToJsonElement(config).jsonObject.getValue("inbounds").jsonArray.map { it.jsonObject }
            .first { it.getValue("protocol").jsonPrimitive.content == protocol }

    private fun plan(settings: DesktopAppSettings, runtime: DesktopLocalProxyRuntime, json: String = rawJson): DesktopTunnelStartPlan {
        val root = Files.createTempDirectory("skipi-jsonauth")
        return try {
            prepareDesktopTunnelStartPlan(
                settings = settings,
                serverLibrary = library(json),
                configLibrary = ConfigProfileLibrary(),
                latencyByServerId = emptyMap(),
                localProxyRuntime = runtime,
                resourceFiles = DesktopProfileResourceFiles(xrayRootDir = root.toFile()),
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun autoTakesTheLoginOfTheCustomJsonForBothInbounds() {
        val runtime = DesktopLocalProxyRuntime()

        val config = plan(settings(LocalProxyAuthMode.Auto, LocalProxyAuthMode.Auto), runtime).configuration

        val socks = inbound(config, "socks").getValue("settings").jsonObject.getValue("users").jsonArray.single().jsonObject
        assertEquals("json-socks", socks.getValue("user").jsonPrimitive.content)
        val http = inbound(config, "http").getValue("settings").jsonObject.getValue("accounts").jsonArray.single().jsonObject
        assertEquals("json-http", http.getValue("user").jsonPrimitive.content)
    }

    @Test
    fun theOptionsHevReadsCarryTheSameResolvedLogin() {
        val runtime = DesktopLocalProxyRuntime()
        plan(settings(LocalProxyAuthMode.Auto, LocalProxyAuthMode.Off), runtime)
        runtime.onCoreStarted(Result.success(DesktopCoreState(isRunning = true)))

        val active = assertNotNull(runtime.activeOptions(coreRunning = true))

        assertEquals("json-socks", active.username)
        assertEquals("json-socks-pass", active.password)
        assertEquals("", active.httpUsername)
    }

    @Test
    fun manualKeepsTheStoredLoginEvenWhenTheJsonHasOne() {
        val runtime = DesktopLocalProxyRuntime()

        val config = plan(settings(LocalProxyAuthMode.Manual, LocalProxyAuthMode.Manual), runtime).configuration

        val socks = inbound(config, "socks").getValue("settings").jsonObject.getValue("users").jsonArray.single().jsonObject
        assertEquals("stored-socks", socks.getValue("user").jsonPrimitive.content)
        val http = inbound(config, "http").getValue("settings").jsonObject.getValue("accounts").jsonArray.single().jsonObject
        assertEquals("stored-http", http.getValue("user").jsonPrimitive.content)
    }

    @Test
    fun autoFallsBackToTheStoredLoginWhenTheJsonHasNone() {
        val runtime = DesktopLocalProxyRuntime()
        val bare = """{"outbounds": [{"tag": "raw-out", "protocol": "freedom"}]}"""

        val config = plan(settings(LocalProxyAuthMode.Auto, LocalProxyAuthMode.FromJson), runtime, bare).configuration

        val socks = inbound(config, "socks").getValue("settings").jsonObject.getValue("users").jsonArray.single().jsonObject
        assertEquals("stored-socks", socks.getValue("user").jsonPrimitive.content)
        assertEquals(false, inbound(config, "http").getValue("settings").jsonObject.containsKey("accounts"), "from JSON without a JSON login stays open")
    }

    @Test
    fun offLeavesBothInboundsOpenWhateverTheJsonSays() {
        val runtime = DesktopLocalProxyRuntime()

        val config = plan(settings(LocalProxyAuthMode.Off, LocalProxyAuthMode.Off), runtime).configuration

        assertEquals("noauth", inbound(config, "socks").getValue("settings").jsonObject.getValue("auth").jsonPrimitive.content)
        assertEquals(false, inbound(config, "http").getValue("settings").jsonObject.containsKey("accounts"))
    }
}
