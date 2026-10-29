// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.proxy.server.model.Socks
import kotlinx.serialization.json.Json
import platform.DefaultLocalHttpProxyPort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The standalone Xray document behind "Copy full JSON", identical for every host. */
class XrayExportConfigTest {
    private val server = Socks(remarks = "Proxy", server = "proxy.example", port = "1080")

    private fun plan(balancers: List<XrayBalancerPlan> = emptyList()) = XrayOutboundPlan(
        proxyOutbounds = listOf(XrayProxyOutboundServer(tag = "proxy", server = server)),
        balancers = balancers,
        observatorySelectors = if (balancers.isEmpty()) emptyList() else listOf("proxy"),
        routeTargets = emptyMap(),
        dnsHostServers = emptyList(),
    )

    private fun generated(
        plan: XrayOutboundPlan = plan(),
        socksPort: String = "10808",
        proxyDns: List<String> = listOf("https://8.8.8.8/dns-query"),
        enableIpv6: Boolean = false,
        mux: XrayMuxOutboundOptions? = null,
        fragment: XrayFragmentOutboundOptions? = null,
    ): JsonObject = Json.parseToJsonElement(
        XrayExportConfig.generated(
            XrayExportInput(
                outboundPlan = plan,
                localProxyPort = socksPort,
                proxyDnsServers = proxyDns,
                enableIpv6 = enableIpv6,
                mux = mux,
                fragment = fragment,
            ),
        ),
    ).jsonObject

    @Test
    fun exposesLoopbackSocksAndHttpInboundsOnTheConfiguredPort() {
        val inbounds = generated(socksPort = "20480").getValue("inbounds").jsonArray.map { it.jsonObject }

        assertEquals(listOf("socks", "http"), inbounds.map { it.getValue("tag").jsonPrimitive.content })
        assertEquals(20480, inbounds[0].getValue("port").jsonPrimitive.content.toInt())
        assertEquals(DefaultLocalHttpProxyPort, inbounds[1].getValue("port").jsonPrimitive.content.toInt())
        assertTrue(inbounds.all { it.getValue("listen").jsonPrimitive.content == "127.0.0.1" })
        assertEquals("noauth", inbounds[0].getValue("settings").jsonObject.getValue("auth").jsonPrimitive.content)
    }

    @Test
    fun invalidPortFallsBackAndNeverCollidesWithTheHttpPort() {
        val fallback = generated(socksPort = "70000").getValue("inbounds").jsonArray.first().jsonObject
        val collide = generated(socksPort = DefaultLocalHttpProxyPort.toString()).getValue("inbounds").jsonArray

        assertEquals(10808, fallback.getValue("port").jsonPrimitive.content.toInt())
        assertEquals(10810, collide[1].jsonObject.getValue("port").jsonPrimitive.content.toInt())
    }

    @Test
    fun dnsKeepsUserServersDropsTunnelAddressesAndAddsPublicFallbacks() {
        val dns = generated(proxyDns = listOf("172.19.0.2", " ", "https://dns.example/q", "1.1.1.1"), enableIpv6 = true)
            .getValue("dns").jsonObject

        assertEquals(
            listOf("https://dns.example/q", "1.1.1.1", "8.8.8.8"),
            dns.getValue("servers").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("UseIP", dns.getValue("queryStrategy").jsonPrimitive.content)
        assertEquals("UseIPv4", generated().getValue("dns").jsonObject.getValue("queryStrategy").jsonPrimitive.content)
    }

    @Test
    fun routesPrivateAddressesDirectlyAndEverythingElseThroughTheFirstOutbound() {
        val root = generated()
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }

        assertEquals("IPIfNonMatch", root.getValue("routing").jsonObject.getValue("domainStrategy").jsonPrimitive.content)
        assertEquals("geoip:private", rules[0].getValue("ip").jsonArray.single().jsonPrimitive.content)
        assertEquals("direct", rules[0].getValue("outboundTag").jsonPrimitive.content)
        assertEquals("tcp,udp", rules[1].getValue("network").jsonPrimitive.content)
        assertEquals("proxy", rules[1].getValue("outboundTag").jsonPrimitive.content)
        assertEquals("warning", root.getValue("log").jsonObject.getValue("loglevel").jsonPrimitive.content)
        assertFalse(root.containsKey("observatory"))
    }

    @Test
    fun balancerPlansRouteThroughTheBalancerAndAddObservatory() {
        val root = generated(plan(listOf(XrayBalancerPlan("lb", "proxy", "random"))))
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }

        assertEquals("lb", rules[1].getValue("balancerTag").jsonPrimitive.content)
        assertFalse(rules[1].containsKey("outboundTag"))
        assertEquals(1, root.getValue("routing").jsonObject.getValue("balancers").jsonArray.size)
        assertTrue(root.containsKey("observatory"))
    }

    @Test
    fun muxAndFragmentOverlaysAreRendered() {
        val root = generated(
            mux = XrayMuxOutboundOptions("7", "2", 1),
            fragment = XrayFragmentOutboundOptions("UseIPv4", "tlshello", "5-10", "2-3"),
        )
        val outbounds = root.getValue("outbounds").jsonArray.map { it.jsonObject }

        assertTrue(outbounds.first { it["tag"]?.jsonPrimitive?.content == "proxy" }.containsKey("mux"))
        assertTrue(outbounds.any { it["tag"]?.jsonPrimitive?.content == "fragment" })
    }

    @Test
    fun rawCustomKeepsItsOwnInboundsUnlessOverridden() {
        val raw = """{"log": {"loglevel": "none"}, "inbounds": [{"tag": "mine"}], "outbounds": [{"tag": "o", "protocol": "freedom"}]}"""

        val kept = Json.parseToJsonElement(XrayExportConfig.custom(raw, overrideInboundAndDns = false, localProxyPort = "10808")).jsonObject
        val replaced = Json.parseToJsonElement(XrayExportConfig.custom(raw, overrideInboundAndDns = true, localProxyPort = "10808")).jsonObject

        assertEquals("mine", kept.getValue("inbounds").jsonArray.single().jsonObject.getValue("tag").jsonPrimitive.content)
        assertEquals(listOf("socks", "http"), replaced.getValue("inbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content })
        assertEquals("warning", kept.getValue("log").jsonObject.getValue("loglevel").jsonPrimitive.content)
        assertTrue(kept.containsKey("outbounds"))
    }
}
