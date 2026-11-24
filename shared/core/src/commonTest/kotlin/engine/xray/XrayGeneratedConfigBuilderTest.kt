// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XrayGeneratedConfigBuilderTest {
    @Test
    fun assemblesSectionsAndEndsWithOneLf() {
        val result = buildGeneratedXrayConfig(input())
        assertTrue(result.json.endsWith("}\n") && !result.json.endsWith("\n\n"))
        val json = Json.parseToJsonElement(result.json).jsonObject
        assertEquals("warning", json.getValue("log").jsonObject.getValue("loglevel").jsonPrimitive.content)
        assertEquals(listOf("in"), json.getValue("inbounds").jsonArray.tags())
        assertEquals(
            XrayTags.PROXY,
            json.getValue("routing").jsonObject.getValue("rules").jsonArray.last()
                .jsonObject.getValue("outboundTag").jsonPrimitive.content,
        )
        assertTrue(json.containsKey("stats"))
        assertNull(json["fakeDns"])
        assertNull(json["observatory"])
        assertEquals(emptyList(), result.unappliedRules)
    }

    @Test
    fun trafficStatsCanBeSwitchedOff() {
        val json = Json.parseToJsonElement(buildGeneratedXrayConfig(input(collectTrafficStats = false)).json).jsonObject
        assertNull(json["stats"])
        assertNull(json["policy"])
    }

    @Test
    fun dnsOverrideReplacesThePlannedDnsSection() {
        val override = buildJsonObject { put("queryStrategy", "UseIPv4") }
        val json = Json.parseToJsonElement(buildGeneratedXrayConfig(input(dnsOverride = override)).json).jsonObject
        assertEquals(override, json.getValue("dns"))
    }

    @Test
    fun plannedDnsIsUsedWithoutAnOverride() {
        val json = Json.parseToJsonElement(buildGeneratedXrayConfig(input()).json).jsonObject
        assertEquals(buildXrayDnsConfig(dnsPlan()), json.getValue("dns"))
    }

    private fun input(
        plan: XrayOutboundPlan = outboundPlan(),
        collectTrafficStats: Boolean = true,
        dnsOverride: JsonObject? = null,
    ) = XrayGeneratedConfigInput(
        outboundPlan = plan,
        dnsPlan = dnsPlan(),
        log = buildJsonObject { put("loglevel", "warning") },
        inbounds = listOf(buildJsonObject { put("tag", "in") }),
        routeDomainStrategy = 1,
        routeRules = emptyList(),
        defaultRouteOutboundTag = XrayTags.PROXY,
        enableLocalDns = false,
        dnsHijackInboundTags = emptyList(),
        ruleValidator = object : XrayRoutingRuleValidator {
            override fun isDomainRuleValid(rule: String) = true
            override fun isIpRuleValid(rule: String) = true
        },
        collectTrafficStats = collectTrafficStats,
        dnsOverride = dnsOverride,
    )

    private fun outboundPlan() = XrayOutboundPlan(
        proxyOutbounds = listOf(
            XrayProxyOutboundServer(
                tag = XrayTags.PROXY,
                customOutbound = buildJsonObject {
                    put("tag", XrayTags.PROXY)
                    put("protocol", "socks")
                    put(
                        "settings",
                        buildJsonObject {
                            put("address", "127.0.0.1")
                            put("port", 10_000)
                        },
                    )
                },
            ),
        ),
        balancers = emptyList(),
        observatorySelectors = emptyList(),
        routeTargets = mapOf(XrayTags.PROXY to XrayRouteTarget(XrayTags.PROXY, XrayRouteTargetKind.Outbound)),
        dnsHostServers = emptyList(),
        observatoryProbeUrl = null,
        observatoryProbeInterval = null,
        observatoryProbeTimeout = null,
    )

    private fun dnsPlan() = XrayDnsPlan(
        servers = Json.parseToJsonElement("[\"1.1.1.1\"]").jsonArray,
        queryStrategy = "UseIPv4",
        tag = XrayTags.PROXY_DNS,
        hosts = Json.parseToJsonElement("{}").jsonObject,
        fakeDns = null,
        nonIpQueryFallback = XrayDnsTcpFallback("9.9.9.9", 5_353),
        routingOptions = XrayDnsRoutingOptions(routeProxyDns = false, routeDirectDns = false),
    )

    private fun JsonArray.tags(): List<String> = map { it.jsonObject.getValue("tag").jsonPrimitive.content }
}
