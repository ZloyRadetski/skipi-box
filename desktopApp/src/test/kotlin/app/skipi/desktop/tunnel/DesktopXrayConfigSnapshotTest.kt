// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.encodePersistedProxyServer
import features.routing.model.RouteRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import platform.LocalProxyXrayConfigOptions
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Characterization (golden file) tests of the Xray JSON the Desktop builder produces today. They record
 * behaviour, odd or not, as a safety net for merging the Android and Desktop config building.
 *
 * Update the goldens with `UPDATE_GOLDEN=1 ./gradlew :desktopApp:test --tests "*DesktopXrayConfigSnapshotTest"`.
 * Goldens live in `desktopApp/src/test/resources/xray-snapshots/` and are read from the file system.
 */
class DesktopXrayConfigSnapshotTest {
    @Test
    fun plainVless() = snapshot(
        "plain-vless",
        library(selected = 1, vless(1, "a.example", "Alpha")),
    )

    @Test
    fun rulesToOtherServerDirectBlock() = snapshot(
        "rules-to-other-server-direct-block",
        library(selected = 1, vless(1, "a.example", "Alpha"), vless(2, "b.example", "Beta")),
        routing = RoutingConfigRecord(
            rules = listOf(
                RouteRule(id = 1, domain = listOf("geosite:steam"), outboundTag = "2"),
                RouteRule(id = 2, domain = listOf("domain:ru"), outboundTag = "direct"),
                RouteRule(id = 3, domain = listOf("geosite:category-ads-all"), outboundTag = "block"),
            ),
        ),
    )

    @Test
    fun strategyGroup() = snapshot(
        "strategy-group",
        library(
            selected = 1,
            vless(1, "a.example", "Alpha"),
            vless(2, "b.example", "Beta"),
            vless(3, "c.example", "Gamma"),
            stored(90, leastPingGroup(listOf(2, 3))),
        ),
        routing = RoutingConfigRecord(
            defaultOutboundTag = "90",
            rules = listOf(RouteRule(id = 1, domain = listOf("geosite:google"), outboundTag = "90")),
        ),
    )

    @Test
    fun chain() = snapshot(
        "chain",
        library(
            selected = 1,
            vless(1, "a.example", "Alpha"),
            vless(2, "b.example", "Beta"),
            vless(3, "c.example", "Gamma"),
            vless(4, "d.example", "Delta"),
            stored(95, ChainProxy(remarks = "Three hops", proxyServerIds = listOf(2, 3, 4))),
        ),
        routing = RoutingConfigRecord(
            rules = listOf(RouteRule(id = 1, domain = listOf("geosite:netflix"), outboundTag = "95")),
        ),
    )

    @Test
    fun rawJsonGlobalRules() = snapshot(
        "raw-json-global-rules",
        library(
            selected = 41,
            raw(41, "Raw", RAW_WITH_OWN_ROUTING),
            vless(2, "b.example", "Beta"),
            vless(3, "c.example", "Gamma"),
            vless(4, "d.example", "Delta"),
            stored(90, leastPingGroup(listOf(3, 4))),
        ),
        routing = RoutingConfigRecord(
            rules = listOf(
                RouteRule(id = 1, domain = listOf("geosite:steam"), outboundTag = "2"),
                RouteRule(id = 2, domain = listOf("geosite:google"), outboundTag = "90"),
                RouteRule(id = 3, domain = listOf("domain:ru"), outboundTag = "direct"),
            ),
        ),
    )

    @Test
    fun rawJsonNoRules() = snapshot(
        "raw-json-no-rules",
        library(selected = 41, raw(41, "Raw", RAW_WITH_OWN_ROUTING)),
    )

    @Test
    fun rawJsonProfileGroup() = snapshot(
        "raw-json-profile-group",
        library(
            selected = 41,
            raw(41, "Raw", RAW_WITH_OWN_ROUTING),
            raw(1, "Alpha", RAW_WITH_OWN_ROUTING.replace("edge.example", "a.example")),
            raw(2, "Beta", RAW_WITH_OWN_ROUTING.replace("edge.example", "b.example")),
        ),
        profile = PROFILE_WITH_GROUP,
    )

    @Test
    fun profileWithGroup() = snapshot(
        "profile-with-group",
        library(
            selected = 1,
            vless(1, "a.example", "Alpha"),
            vless(2, "b.example", "Beta"),
            vless(3, "c.example", "Gamma"),
        ),
        profile = PROFILE_WITH_GROUP,
    )

    @Test
    fun profileChain() = snapshot(
        "profile-chain",
        library(
            selected = 1,
            vless(1, "a.example", "Alpha"),
            vless(2, "b.example", "Beta"),
            vless(3, "c.example", "Gamma"),
            stored(95, ChainProxy(remarks = "Hops", proxyServerIds = listOf(2, 3))),
        ),
        profile = """
            [Rule]
            DOMAIN-SET,geosite:netflix,Hops
            DOMAIN-SET,geosite:steam,DIRECT
            FINAL,PROXY
        """.trimIndent() + "\n",
    )

    @Test
    fun strictFullTunnel() = snapshot(
        "strict-full-tunnel",
        library(selected = 1, vless(1, "a.example", "Alpha")),
        routing = RoutingConfigRecord(
            rules = listOf(RouteRule(id = 1, domain = listOf("domain:ru"), outboundTag = "direct")),
        ),
        strictFullTunnel = true,
    )

    @Test
    fun tunDnsPlainServer() = snapshot(
        "tun-dns-plain",
        library(selected = 1, vless(1, "a.example", "Alpha")),
        answerTunDns = true,
    )

    @Test
    fun tunDnsProfile() = snapshot(
        "tun-dns-profile",
        library(selected = 1, vless(1, "a.example", "Alpha"), vless(2, "b.example", "Beta")),
        profile = """
            [General]
            dns-server = 1.1.1.1

            [Rule]
            DOMAIN-SET,geosite:steam,DIRECT
            FINAL,PROXY
        """.trimIndent() + "\n",
        answerTunDns = true,
    )

    // ---- fixtures ----

    private fun stored(id: Int, server: ProxyServer<*>) =
        DesktopStoredProxyServer(id = id, serverJson = server.encodePersistedProxyServer())

    private fun vless(id: Int, host: String, name: String) = stored(
        id,
        ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@$host:443?security=none&type=tcp#$name"),
    )

    private fun raw(id: Int, name: String, json: String) =
        stored(id, Custom(remarks = name, overrideInboundAndDns = true, configJson = json))

    private fun leastPingGroup(members: List<Int>) = StrategyGroup(
        remarks = "Fastest",
        strategy = StrategyGroupConstants.TYPE_LEAST_PING,
        proxyServerIds = members,
    )

    private fun library(selected: Int, vararg servers: DesktopStoredProxyServer) =
        DesktopServerLibrary(selectedServerId = selected, servers = servers.toList())

    private fun snapshot(
        name: String,
        library: DesktopServerLibrary,
        routing: RoutingConfigRecord = RoutingConfigRecord(),
        profile: String? = null,
        strictFullTunnel: Boolean = false,
        answerTunDns: Boolean = false,
    ) {
        val configs = if (profile == null) {
            DesktopConfigLibrary()
        } else {
            DesktopConfigLibrary(
                selectedConfigId = 7,
                configs = listOf(DesktopStoredConfig(id = 7, name = "Profile", content = profile)),
            )
        }
        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = configs,
            routing = routing,
            options = LocalProxyXrayConfigOptions(socksPort = 20_480),
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "",
            latencyByServerId = emptyMap(),
            strictFullTunnel = strictFullTunnel,
            answerTunDns = answerTunDns,
        )
        val actual = normalize(assertNotNull(result, "$name: no config").getOrThrow())
        val file = File("src/test/resources/xray-snapshots/$name.json")
        if (System.getenv("UPDATE_GOLDEN") == "1") {
            file.parentFile.mkdirs()
            file.writeText(actual)
            return
        }
        if (!file.isFile) {
            fail("Missing golden ${file.absolutePath}. Generate it with UPDATE_GOLDEN=1 and review it before committing.")
        }
        assertEquals(file.readText().replace("\r\n", "\n"), actual, "Golden $name differs; rerun with UPDATE_GOLDEN=1 if intended")
    }

    private fun normalize(json: String): String {
        val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }
        return pretty.encodeToString(JsonElement.serializer(), sorted(Json.parseToJsonElement(json))) + "\n"
    }

    private fun sorted(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { (k, v) -> k to sorted(v) })
        is JsonArray -> JsonArray(element.map(::sorted))
        else -> element
    }

    private companion object {
        val RAW_WITH_OWN_ROUTING = """
            {
              "outbounds": [
                {"tag": "my-proxy", "protocol": "vless", "settings": {"vnext": [{"address": "edge.example", "port": 443,
                  "users": [{"id": "00000000-0000-0000-0000-000000000000", "encryption": "none"}]}]}},
                {"tag": "direct", "protocol": "freedom"}
              ],
              "routing": {"domainStrategy": "IPIfNonMatch", "rules": [
                {"domain": ["domain:ru"], "outboundTag": "direct"}
              ]}
            }
        """.trimIndent()

        val PROFILE_WITH_GROUP = """
            [Proxy Group]
            Fast = url-test, Alpha, Beta, url=https://example.com/ping, interval=60

            [Rule]
            DOMAIN-SET,geosite:google,Fast
            DOMAIN-SET,geosite:steam,Beta
            DOMAIN-SET,geosite:claude,Raw
            DOMAIN-SET,geosite:discord,DIRECT
            DOMAIN-SET,geosite:category-ads-all,REJECT
            FINAL,PROXY
        """.trimIndent() + "\n"
    }
}
