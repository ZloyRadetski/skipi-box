// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TUN mode resolves through the tunnel, as Android does with hev: the interface carries the TUN DNS,
 * and Core's DNS answers the port-53 traffic hev hands to the SOCKS inbound. Outside TUN mode, and
 * for a raw JSON SKIPI does not override, nothing changes.
 */
class DesktopTunDnsTest {
    private val rawJson = """
        {
          "outbounds": [
            {"tag": "my-proxy", "protocol": "vless", "settings": {"vnext": [{"address": "edge.example", "port": 443,
              "users": [{"id": "00000000-0000-0000-0000-000000000000", "encryption": "none"}]}]}}
          ]
        }
    """.trimIndent()

    private fun serverLibrary(server: ProxyServer<*>) = DesktopServerLibrary(
        selectedServerId = 1,
        servers = listOf(DesktopStoredProxyServer(1, server.encodePersistedProxyServer())),
    )

    private fun generated(server: ProxyServer<*>, answerTunDns: Boolean): JsonObject =
        Json.parseToJsonElement(
            assertNotNull(
                buildDesktopSelectedServerConfig(
                    serverLibrary = serverLibrary(server),
                    configLibrary = DesktopConfigLibrary(),
                    routing = RoutingConfigRecord(),
                    options = DesktopAppSettings().toLocalProxyXrayConfigOptions(),
                    trafficConfigContentById = emptyMap(),
                    subscriptionPingUrl = "",
                    latencyByServerId = emptyMap(),
                    answerTunDns = answerTunDns,
                ),
            ).getOrThrow(),
        ).jsonObject

    private fun profile(answerTunDns: Boolean): JsonObject {
        val library = DesktopServerLibraries.select(
            DesktopServerLibraries.add(
                DesktopServerLibrary(),
                ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
            ),
            serverId = 1,
        )
        return Json.parseToJsonElement(
            DesktopTrafficProfileXrayConfigFactory.buildGenerated(
                profile = DesktopStoredConfig(id = 1, name = "P", content = "[Rule]\nFINAL,PROXY\n"),
                serverLibrary = library,
                options = DesktopAppSettings().toLocalProxyXrayConfigOptions(),
                answerTunDns = answerTunDns,
            ).json,
        ).jsonObject
    }

    private val socks = Socks(remarks = "Proxy", server = "proxy.example", port = "1080")

    /** A raw JSON SKIPI does not override may have no routing at all. */
    private fun JsonObject.rules() =
        this["routing"]?.jsonObject?.get("rules")?.jsonArray?.map { it.jsonObject }.orEmpty()
    private fun JsonObject.outboundTags() =
        getValue("outbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }

    /** The rule sending port 53 of hev's SOCKS inbound to Core's DNS, or null. */
    private fun JsonObject.tunDnsRule(): JsonObject? = rules().firstOrNull { rule ->
        rule["port"]?.jsonPrimitive?.content == "53" && rule["outboundTag"]?.jsonPrimitive?.content == "dns-out"
    }

    private fun assertAnswersTunDns(root: JsonObject) {
        val rule = assertNotNull(root.tunDnsRule(), root.toString())
        assertEquals(listOf("skipi-socks"), rule.getValue("inboundTag").jsonArray.map { it.jsonPrimitive.content })
        // It precedes every user rule, or a FINAL rule would send the queries to the proxy unanswered.
        assertEquals(rule, root.rules().first())
        assertTrue("dns-out" in root.outboundTags(), root.outboundTags().toString())
    }

    private fun assertLeavesDnsAlone(root: JsonObject) {
        assertNull(root.tunDnsRule(), root.toString())
        assertFalse("dns-out" in root.outboundTags(), root.outboundTags().toString())
    }

    @Test
    fun aGeneratedConfigAnswersTheTunDnsOnlyInTunMode() {
        assertAnswersTunDns(generated(socks, answerTunDns = true))
        assertLeavesDnsAlone(generated(socks, answerTunDns = false))
    }

    @Test
    fun aGeneratedConfigResolvesTheServerItselfDirectly() {
        // Core's own lookup of the server comes back through the tunnel as a TUN query; Core's DNS must
        // answer it without the proxy that needs the answer.
        val root = generated(socks, answerTunDns = true)
        val direct = root.getValue("dns").jsonObject.getValue("servers").jsonArray
            .filterIsInstance<JsonObject>()
            .filter { it["tag"]?.jsonPrimitive?.content == "dns-direct" }
        assertTrue(direct.any { server -> "proxy.example" in server["domains"].toString() }, direct.toString())
        assertTrue(
            root.rules().any { rule ->
                rule["inboundTag"]?.jsonArray?.map { it.jsonPrimitive.content } == listOf("dns-direct") &&
                    rule["outboundTag"]?.jsonPrimitive?.content == "direct"
            },
            root.rules().toString(),
        )
    }

    @Test
    fun aTrafficProfileAnswersTheTunDnsOnlyInTunMode() {
        val root = profile(answerTunDns = true)
        assertAnswersTunDns(root)
        val dnsOut = root.getValue("outbounds").jsonArray.map { it.jsonObject }
            .single { it.getValue("tag").jsonPrimitive.content == "dns-out" }
        assertTrue("skipi-proxy" in dnsOut.toString(), dnsOut.toString())

        assertLeavesDnsAlone(profile(answerTunDns = false))
    }

    @Test
    fun aRawJsonAnswersTheTunDnsOnlyWhenSkipiOverridesItsDns() {
        val overridden = Custom(remarks = "Raw", overrideInboundAndDns = true, configJson = rawJson)
        assertAnswersTunDns(generated(overridden, answerTunDns = true))
        assertTrue(serverLibrary(overridden).coreAnswersTunDns())

        val ownDns = Custom(remarks = "Raw", overrideInboundAndDns = false, configJson = rawJson)
        assertLeavesDnsAlone(generated(ownDns, answerTunDns = true))
        assertFalse(serverLibrary(ownDns).coreAnswersTunDns())
    }

    @Test
    fun everyGeneratedServerAnswersTheTunDns() {
        assertTrue(serverLibrary(socks).coreAnswersTunDns())
        assertTrue(DesktopServerLibrary().coreAnswersTunDns())
    }

    @Test
    fun theInterfaceCarriesTheProfilesTunDnsWhenCoreAnswersIt() {
        val settings = DesktopAppSettings(tunVpnDns = "1.1.1.1")
        assertEquals(listOf("1.1.1.1"), desktopTunInterfacePlan(settings, DesktopConfigLibrary(), macInterfaces = null).dnsServers)

        val withProfile = DesktopConfigLibrary(
            selectedConfigId = 1,
            configs = listOf(DesktopStoredConfig(id = 1, name = "P", content = "[General]\ndns-server = 9.9.9.9\n[Rule]\nFINAL,PROXY\n")),
        )
        val profileDns = withProfile.activeProfileRuntime().tunVpnDnsOr(settings.tunVpnDns)
        assertEquals(listOf(profileDns), desktopTunInterfacePlan(settings, withProfile, macInterfaces = null).dnsServers)

        assertEquals(
            emptyList(),
            desktopTunInterfacePlan(settings, DesktopConfigLibrary(), coreAnswersDns = false, macInterfaces = null).dnsServers,
        )
    }

    @Test
    fun localVpnDnsDecidesWhetherATunStartResolvesThroughTheTunnel() {
        val tun = DesktopAppSettings(enableTunMode = true)
        fun profile(localDns: Boolean?) = DesktopConfigLibrary(
            selectedConfigId = 1,
            configs = listOf(
                DesktopStoredConfig(
                    id = 1,
                    name = "P",
                    content = "[SKIPI]\n" + (localDns?.let { "vpn-local-dns = $it\n" } ?: "") + "[Rule]\nFINAL,PROXY\n",
                ),
            ),
        )

        assertTrue(desktopAnswersTunDns(tun, DesktopConfigLibrary()))
        assertTrue(desktopAnswersTunDns(tun, profile(localDns = null)))
        assertTrue(desktopAnswersTunDns(tun, profile(localDns = true)))
        // Either switch turns it off: the application's or the active profile's.
        assertFalse(desktopAnswersTunDns(tun.copy(enableVpnLocalDns = false), DesktopConfigLibrary()))
        assertFalse(desktopAnswersTunDns(tun.copy(enableVpnLocalDns = false), profile(localDns = true)))
        assertFalse(desktopAnswersTunDns(tun, profile(localDns = false)))
        // Outside TUN mode nothing reaches Core through a tunnel.
        assertFalse(desktopAnswersTunDns(DesktopAppSettings(), DesktopConfigLibrary()))
    }

    @Test
    fun withLocalVpnDnsOffTheTunStartLeavesTheSystemResolverAlone() {
        val settings = DesktopAppSettings(enableTunMode = true, enableVpnLocalDns = false)
        val answers = desktopAnswersTunDns(settings, DesktopConfigLibrary())

        assertLeavesDnsAlone(generated(socks, answerTunDns = answers))
        assertEquals(
            emptyList(),
            desktopTunInterfacePlan(settings, DesktopConfigLibrary(), coreAnswersDns = answers, macInterfaces = null).dnsServers,
        )
    }

    @Test
    fun resettingVpnSettingsTurnsLocalVpnDnsBackOn() {
        assertTrue(DesktopAppSettings(enableVpnLocalDns = false).withVpnSettingsReset().enableVpnLocalDns)
    }

    @Test
    fun theDevicePlanCarriesTheDnsAndLeavesItOutWhenThereIsNone() {
        fun openedPlan(coreAnswersDns: Boolean): String {
            var opened = ""
            val device = object : DesktopTunDevice {
                override fun prepare(interfaceName: String): Result<Unit> = Result.success(Unit)
                override fun open(planJson: String): Result<Int> {
                    opened = planJson
                    return Result.success(7)
                }

                override fun close(): Result<Unit> = Result.success(Unit)
            }
            val tunnel = object : DesktopHevTunnel {
                override fun start(configYaml: String, tunFd: Int): Result<Unit> = Result.success(Unit)
                override fun stop(): Result<Unit> = Result.success(Unit)
                override fun isRunning(): Boolean = true
            }
            val plan = desktopTunInterfacePlan(
                DesktopAppSettings(tunVpnDns = "1.1.1.1"),
                DesktopConfigLibrary(),
                coreAnswersDns = coreAnswersDns,
                macInterfaces = null,
            )
            DesktopTunRuntime(device, tunnel).start(
                desktopTunStartRequest(plan, DesktopAppSettings().toLocalProxyXrayConfigOptions(), logPath = ""),
            ).getOrThrow()
            return opened
        }

        val withDns = Json.parseToJsonElement(openedPlan(coreAnswersDns = true)).jsonObject
        assertEquals(listOf("1.1.1.1"), withDns.getValue("dns").jsonArray.map { it.jsonPrimitive.content })
        assertFalse("dns" in Json.parseToJsonElement(openedPlan(coreAnswersDns = false)).jsonObject)
    }
}
