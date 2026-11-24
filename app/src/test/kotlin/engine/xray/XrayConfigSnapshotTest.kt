// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.withActiveTrafficConfigApplied
import features.config.TrafficConfigState
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.routing.model.RouteRule
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Test

/**
 * Characterization tests: they record what the Android config builder produces today.
 * Regenerate goldens with `UPDATE_GOLDEN=1 ./gradlew :app:testDebugUnitTest --tests "*XrayConfigSnapshotTest"`.
 */
class XrayConfigSnapshotTest {
    private val goldenDir = File("src/test/resources/xray-snapshots")
    private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

    private fun vless(id: Int, name: String) = ProxyServerState(
        id = id,
        groupId = 0,
        server = VLESS(
            remarks = name,
            id = "4219d973-8792-462f-8747-df766f70f13$id",
            server = "${name.lowercase()}.example",
            port = "443",
        ),
    )

    private fun entry(id: Int, server: ProxyServer<*>) = ProxyServerState(id = id, groupId = 0, server = server)

    private val alpha = vless(1, "Alpha")
    private val beta = vless(2, "Beta")
    private val gamma = vless(3, "Gamma")

    private val rawServer = entry(
        10,
        Custom(
            remarks = "Raw",
            overrideInboundAndDns = true,
            configJson = """
                {"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"raw.example","port":443,"users":[{"id":"4219d973-8792-462f-8747-df766f70f137","encryption":"none"}]}]}},
                              {"tag":"local","protocol":"freedom"}],
                 "routing":{"rules":[{"domain":["direct.example"],"outboundTag":"local"}]}}
            """.trimIndent(),
        ),
    )

    private fun build(appState: AppState, selected: ProxyServerState): String =
        XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = appState,
                selectedServer = selected,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths("/tmp/access.log", "/tmp/error.log"),
                dataDir = null,
            ),
        )

    private fun JsonElement.normalized(): JsonElement = when (this) {
        is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { (k, v) -> k to v.normalized() })
        is JsonArray -> JsonArray(map { it.normalized() })
        else -> this
    }

    private fun check(scenario: String, config: String) {
        val text = prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(config).normalized()) + "\n"
        val file = File(goldenDir, "$scenario.json")
        if (System.getenv("UPDATE_GOLDEN") == "1") {
            goldenDir.mkdirs()
            file.writeText(text)
            return
        }
        if (!file.exists()) {
            fail("Missing golden ${file.path}. Generate with: UPDATE_GOLDEN=1 ./gradlew :app:testDebugUnitTest --tests \"*XrayConfigSnapshotTest\"")
        }
        assertEquals(file.readText().replace("\r\n", "\n"), text, "Golden mismatch for $scenario")
    }

    @Test
    fun plainVless() {
        check("plain-vless", build(AppState(proxyServers = listOf(alpha)), alpha))
    }

    @Test
    fun rulesToOtherServerDirectBlock() {
        val state = AppState(
            proxyServers = listOf(alpha, beta),
            defaultRouteOutboundTag = "proxy",
            routeRules = listOf(
                RouteRule(id = 1, domain = listOf("domain:server.example"), outboundTag = "2"),
                RouteRule(id = 2, domain = listOf("domain:direct.example"), outboundTag = "direct"),
                RouteRule(id = 3, domain = listOf("domain:block.example"), outboundTag = "block"),
            ),
        )
        check("rules-to-other-server-direct-block", build(state, alpha))
    }

    private val group = entry(
        20,
        StrategyGroup(remarks = "Auto", strategy = "leastPing", proxyServerIds = listOf(2, 3)),
    )

    @Test
    fun strategyGroup() {
        val state = AppState(
            proxyServers = listOf(alpha, beta, gamma, group),
            defaultRouteOutboundTag = "20",
            routeRules = listOf(RouteRule(id = 1, domain = listOf("domain:group.example"), outboundTag = "20")),
        )
        check("strategy-group", build(state, alpha))
    }

    @Test
    fun chain() {
        val chain = entry(30, ChainProxy(remarks = "Hops", proxyServerIds = listOf(2, 3, 1)))
        val state = AppState(
            proxyServers = listOf(alpha, beta, gamma, chain),
            routeRules = listOf(RouteRule(id = 1, domain = listOf("domain:chain.example"), outboundTag = "30")),
        )
        check("chain", build(state, alpha))
    }

    @Test
    fun rawJsonOverrideGlobalRules() {
        val state = AppState(
            proxyServers = listOf(rawServer, beta, gamma, group),
            routeRules = listOf(
                RouteRule(id = 1, domain = listOf("domain:server.example"), outboundTag = "2"),
                RouteRule(id = 2, domain = listOf("domain:group.example"), outboundTag = "20"),
            ),
        )
        check("raw-json-override-global-rules", build(state, rawServer))
    }

    @Test
    fun rawJsonNoRules() {
        check("raw-json-no-rules", build(AppState(proxyServers = listOf(rawServer, beta)), rawServer))
    }

    @Test
    fun profileWithGroup() {
        val auto = entry(
            20,
            StrategyGroup(
                remarks = "Auto",
                strategy = "leastPing",
                proxyServerIds = listOf(2, 3),
                sourceTrafficConfigId = 7,
                sourcePolicyGroupName = "Auto",
            ),
        )
        val profile = TrafficConfigState(
            id = 7,
            name = "Snapshot",
            rawConfig = """
                [General]
                [Proxy Group]
                Auto = url-test, Beta, Gamma, url=http://www.gstatic.com/generate_204, interval=600
                [Rule]
                DOMAIN-SET,geosite:openai,Auto
                DOMAIN-SUFFIX,beta-only.example,Beta
                DOMAIN-SUFFIX,direct.example,DIRECT
                DOMAIN-SUFFIX,ads.example,REJECT
                FINAL,PROXY
            """.trimIndent(),
        )
        val state = AppState(
            proxyServers = listOf(alpha, beta, gamma, auto),
            trafficConfigs = listOf(profile),
            activeTrafficConfigId = 7,
        ).withActiveTrafficConfigApplied()
        check("profile-with-group", build(state, alpha))
    }

    @Test
    fun strictFullTunnel() {
        val state = AppState(
            proxyServers = listOf(alpha),
            enableStrictFullTunnel = true,
            routeRules = listOf(RouteRule(id = 1, domain = listOf("domain:direct.example"), outboundTag = "direct")),
        )
        check("strict-full-tunnel", build(state, alpha))
    }

    @Test
    fun localDnsFakeDns() {
        val state = AppState(
            proxyServers = listOf(alpha),
            enableVpnLocalDns = true,
            enableFakeDns = true,
        )
        check("local-dns-fakedns", build(state, alpha))
    }
}
