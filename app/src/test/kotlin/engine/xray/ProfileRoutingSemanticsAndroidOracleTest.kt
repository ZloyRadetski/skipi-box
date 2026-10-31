// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.withActiveTrafficConfigApplied
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import features.proxy.server.model.ProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Source oracle for the Desktop active-profile runtime: the routing meaning Android produces for a
 * Shadowrocket profile. Outbound tags differ between hosts, so rules are compared by what their
 * target is (protocol and address, direct, block, or balancer members), not by tag.
 */
class ProfileRoutingSemanticsAndroidOracleTest {
    private val alpha = ProxyServerState(
        id = 1,
        groupId = 0,
        server = ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@alpha.example:443#Alpha"),
    )
    private val beta = ProxyServerState(
        id = 2,
        groupId = 0,
        server = ProxyServer.parse("vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@beta.example:443#Beta"),
    )

    private fun androidRoutingProjection(profile: String): List<String> {
        val state = AppState(
            proxyServers = listOf(alpha, beta),
            selectedProxyServerId = alpha.id,
            trafficConfigs = listOf(TrafficConfigState(id = 7, name = "P", rawConfig = profile)),
            activeTrafficConfigId = 7,
        ).withActiveTrafficConfigApplied()
        val built = XrayConfigFactory.buildXrayConfigResult(
            XrayConfigRequest(
                appState = state,
                selectedServer = alpha,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(accessLogPath = "/tmp/access.log", errorLogPath = "/tmp/error.log"),
                collectTrafficStats = false,
            ),
        )
        return routingProjection(Json.parseToJsonElement(built.json).jsonObject)
    }

    @Test
    fun profileRulesRouteToTheTargetsTheProfileNames() {
        // The vpn-tun-in DNS hijack rule belongs to Android's VPN inbound and is host-specific.
        val projection = androidRoutingProjection(PROFILE).filterNot { it.startsWith("inboundTag=[vpn-tun-in]") }

        assertEquals(
            listOf(
                "inboundTag=[dns-direct] -> direct",
                "inboundTag=[dns-proxy] -> vless:alpha.example",
                "domain=[domain:example.com] -> direct",
                "domain=[keyword:ads] -> block",
                "domain=[full:proxy.example] -> balancer:leastPing:[vless:alpha.example, vless:beta.example]",
                "ip=[10.0.0.0/8] -> direct",
                "network=tcp,udp -> vless:alpha.example",
            ),
            projection,
        )
    }

    @Test
    fun geoPortRejectAndFinalDirectRulesKeepTheirMeaning() {
        val projection = androidRoutingProjection(SPECIAL).filterNot { it.startsWith("inboundTag=[vpn-tun-in]") }

        assertEquals(
            listOf(
                "inboundTag=[dns-direct] -> direct",
                "inboundTag=[dns-proxy] -> vless:alpha.example",
                "ip=[geoip:CN] -> direct",
                "port=22 -> direct",
                "domain=[domain:drop.example] -> block",
                "domain=[https://example.com/list.txt] -> direct",
                "network=tcp,udp -> direct",
            ),
            projection,
        )
    }

    private fun androidDnsAndStrategy(profile: String): Pair<kotlinx.serialization.json.JsonObject, String> {
        val state = AppState(
            proxyServers = listOf(alpha, beta),
            selectedProxyServerId = alpha.id,
            trafficConfigs = listOf(
                // Android stores a profile with its [SKIPI] settings already read into androidSettings.
                TrafficConfigState(id = 7, name = "P", rawConfig = profile).withSkipiSettingsReadFromRawConfig(),
            ),
            activeTrafficConfigId = 7,
        ).withActiveTrafficConfigApplied()
        val built = XrayConfigFactory.buildXrayConfigResult(
            XrayConfigRequest(
                appState = state,
                selectedServer = alpha,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(accessLogPath = "/tmp/access.log", errorLogPath = "/tmp/error.log"),
                collectTrafficStats = false,
            ),
        )
        val root = Json.parseToJsonElement(built.json).jsonObject
        return root.getValue("dns").jsonObject to root.getValue("routing").jsonObject.getValue("domainStrategy").jsonPrimitive.content
    }

    @Test
    fun profileDnsSectionAndHostsComeFromTheProfile() {
        val (dns, _) = androidDnsAndStrategy(DNS_PROFILE)

        assertEquals("UseIP", dns.getValue("queryStrategy").jsonPrimitive.content)
        assertEquals(
            listOf("203.0.113.9", "203.0.113.10"),
            dns.getValue("hosts").jsonObject.getValue("profile.example").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("1.1.1.1", "https://dns.example/dns-query"),
            dns.getValue("servers").jsonArray.filterIsInstance<JsonPrimitive>().map { it.content },
        )
    }

    @Test
    fun explicitRouteDomainStrategyIsUsedAndIpv6AloneDoesNotChangeIt() {
        assertEquals("IPOnDemand", androidDnsAndStrategy(DNS_PROFILE + "\n[SKIPI]\nroute-domain-strategy = IPOnDemand\n").second)
        assertEquals("AsIs", androidDnsAndStrategy(DNS_PROFILE).second)
    }

    companion object {
        val DNS_PROFILE = """
            [General]
            dns-server = 1.1.1.1,https://dns.example/dns-query
            ipv6 = true

            [Host]
            profile.example = 203.0.113.9, 203.0.113.10

            [Proxy Group]
            Fast = url-test, Alpha, Beta, url=https://example.com/ping, interval=60

            [Rule]
            DOMAIN,profile.example,Fast
            FINAL,PROXY
        """.trimIndent() + "\n"


        val SPECIAL = """
            [Rule]
            GEOIP,CN,DIRECT
            DST-PORT,22,DIRECT
            DOMAIN-SUFFIX,drop.example,REJECT-DROP
            USER-AGENT,*foo*,DIRECT
            DOMAIN-SET,https://example.com/list.txt,DIRECT
            FINAL,DIRECT
        """.trimIndent() + "\n"

        val PROFILE = """
            [Proxy Group]
            Fast = url-test, Alpha, Beta, url=https://example.com/ping, interval=60

            [Rule]
            DOMAIN-SUFFIX,example.com,DIRECT
            DOMAIN-KEYWORD,ads,REJECT
            DOMAIN,proxy.example,Fast
            IP-CIDR,10.0.0.0/8,DIRECT,no-resolve
            FINAL,PROXY
        """.trimIndent() + "\n"
    }
}

/** Matchers plus a description of where the rule sends traffic; DNS upstream rules are listed too. */
internal fun routingProjection(root: JsonObject): List<String> {
    val outbounds = root.getValue("outbounds").jsonArray.map { it.jsonObject }
    val byTag = outbounds.associateBy { it.getValue("tag").jsonPrimitive.content }
    val balancers = root["routing"]?.jsonObject?.get("balancers")?.jsonArray.orEmpty()
        .map { it.jsonObject }
        .associateBy { it.getValue("tag").jsonPrimitive.content }

    fun describeOutbound(tag: String): String {
        val outbound = byTag[tag] ?: return "missing:$tag"
        val protocol = outbound.getValue("protocol").jsonPrimitive.content
        return when (protocol) {
            "freedom" -> "direct"
            "blackhole" -> "block"
            else -> {
                val settings = outbound["settings"]?.jsonObject
                val address = settings?.get("address")?.jsonPrimitive?.content
                    ?: settings?.get("vnext")?.jsonArray?.firstOrNull()?.jsonObject?.get("address")?.jsonPrimitive?.content
                    ?: settings?.get("servers")?.jsonArray?.firstOrNull()?.jsonObject?.get("address")?.jsonPrimitive?.content
                "$protocol:$address"
            }
        }
    }

    fun describeTarget(rule: JsonObject): String {
        rule["outboundTag"]?.jsonPrimitive?.content?.let { return describeOutbound(it) }
        val balancerTag = rule.getValue("balancerTag").jsonPrimitive.content
        val balancer = balancers.getValue(balancerTag)
        val strategy = balancer["strategy"]?.jsonObject?.get("type")?.jsonPrimitive?.content
        val selectors = balancer.getValue("selector").jsonArray.map { it.jsonPrimitive.content }
        val members = byTag.keys.filter { tag -> selectors.any(tag::startsWith) }.map(::describeOutbound)
        return "balancer:$strategy:${members.sorted()}"
    }

    return root["routing"]?.jsonObject?.get("rules")?.jsonArray.orEmpty().map { element ->
        val rule = element.jsonObject
        val matchers = rule.filterKeys { it != "type" && it != "outboundTag" && it != "balancerTag" && it != "ruleTag" }
            .entries.sortedBy { it.key }
            .joinToString(" ") { (key, value) -> "$key=${value.render()}" }
        "$matchers -> ${describeTarget(rule)}"
    }
}

private fun JsonElement.render(): String = when (this) {
    is JsonArray -> map { it.render() }.toString()
    is JsonPrimitive -> content
    else -> toString()
}
