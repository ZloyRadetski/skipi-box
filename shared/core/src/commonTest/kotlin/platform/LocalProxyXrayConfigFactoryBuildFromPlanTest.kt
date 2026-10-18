// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import engine.xray.XrayBalancerPlan
import engine.xray.XrayDnsPlan
import engine.xray.XrayDnsRoutingOptions
import engine.xray.XrayDnsTcpFallback
import engine.xray.XrayFragmentOutboundOptions
import engine.xray.XrayMuxOutboundOptions
import engine.xray.XrayOutboundPlan
import engine.xray.XrayOutboundRenderingOptions
import engine.xray.XrayProxyOutboundServer
import engine.xray.XrayRouteTarget
import engine.xray.XrayRouteTargetKind
import engine.xray.XrayTags
import features.proxy.server.model.VLESS
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LocalProxyXrayConfigFactoryBuildFromPlanTest {
    @Test
    fun generatedPlanKeepsTheLocalListenerDnsAndNoDefaultRouteShape() {
        val options = LocalProxyXrayConfigOptions(
            socksPort = 18_080,
            httpProxyPort = 18_081,
            enableHttpProxy = true,
            listenAddress = "0.0.0.0",
            username = "desktop-user",
            password = "desktop-secret",
            logLevel = "debug",
            dnsServers = listOf("192.0.2.53", "2001:db8::53"),
            includeDefaultRouting = false,
        )
        val rendered = LocalProxyXrayConfigFactory.buildFromPlan(
            plan = outboundPlan(
                outbounds = listOf(proxyOutbound(XrayTags.PROXY)),
                proxyTargetKind = XrayRouteTargetKind.Outbound,
            ),
            options = options,
            renderingOptions = renderingOptions(
                primaryOutboundTag = XrayTags.PROXY,
                enableLocalDns = true,
                dnsPlan = dnsPlan(),
                dnsProxyOutboundTag = XrayTags.PROXY,
            ),
        )
        val json = Json.parseToJsonElement(rendered).jsonObject

        assertEquals("debug", json.getValue("log").jsonObject.getValue("loglevel").jsonPrimitive.content)
        assertEquals(
            listOf(LocalProxyXrayConfigFactory.SocksInboundTag, LocalProxyXrayConfigFactory.HttpInboundTag),
            json.getValue("inbounds").jsonArray.tags(),
        )
        val inbounds = json.getValue("inbounds").jsonArray
        val socks = inbounds[0].jsonObject
        val socksSettings = socks.getValue("settings").jsonObject
        assertEquals(18_080, socks.getValue("port").jsonPrimitive.content.toInt())
        assertEquals("0.0.0.0", socks.getValue("listen").jsonPrimitive.content)
        assertEquals("password", socksSettings.getValue("auth").jsonPrimitive.content)
        assertEquals(
            "desktop-user",
            socksSettings.getValue("users").jsonArray.single().jsonObject.getValue("user").jsonPrimitive.content,
        )
        assertEquals(
            "desktop-secret",
            socksSettings.getValue("users").jsonArray.single().jsonObject.getValue("pass").jsonPrimitive.content,
        )
        val http = inbounds[1].jsonObject
        assertEquals("http", http.getValue("protocol").jsonPrimitive.content)
        assertEquals(18_081, http.getValue("port").jsonPrimitive.content.toInt())
        assertEquals(DefaultLocalHttpProxyListenAddress, http.getValue("listen").jsonPrimitive.content)

        val dns = json.getValue("dns").jsonObject
        assertEquals(
            listOf("192.0.2.53", "2001:db8::53"),
            dns.getValue("servers").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("UseIPv4", dns.getValue("queryStrategy").jsonPrimitive.content)
        val outbounds = json.getValue("outbounds").jsonArray
        assertEquals(listOf(XrayTags.PROXY, XrayTags.DIRECT, XrayTags.BLOCK, XrayTags.DNS_OUT), outbounds.tags())
        val dnsOutbound = outbounds.last().jsonObject
        assertEquals(
            XrayTags.PROXY,
            dnsOutbound.getValue("streamSettings").jsonObject.getValue("sockopt")
                .jsonObject.getValue("dialerProxy").jsonPrimitive.content,
        )
        assertEquals(
            5_353,
            dnsOutbound.getValue("settings").jsonObject.getValue("rewritePort").jsonPrimitive.content.toInt(),
        )
        assertNull(json["routing"])
        assertNull(json["observatory"])
        assertNull(json["burstObservatory"])
        assertEquals(true, rendered.endsWith("\n"))
    }

    @Test
    fun chainOutboundOrderAndDialerLinksSurviveLocalCompositionWithMux() {
        val json = build(
            plan = outboundPlan(
                outbounds = listOf(
                    proxyOutbound(XrayTags.PROXY, dialerProxyTag = "proxy-chain-1", allowFragment = false),
                    proxyOutbound("proxy-chain-1", allowFragment = false),
                ),
                proxyTargetKind = XrayRouteTargetKind.Outbound,
            ),
            renderingOptions = renderingOptions(
                primaryOutboundTag = XrayTags.PROXY,
                mux = XrayMuxOutboundOptions(
                    concurrency = "8",
                    xudpConcurrency = "7",
                    xudpProxyUdp443Mode = 0,
                ),
            ),
        )

        val outbounds = json.getValue("outbounds").jsonArray
        assertEquals(listOf(XrayTags.PROXY, "proxy-chain-1", XrayTags.DIRECT, XrayTags.BLOCK), outbounds.tags())
        val outer = outbounds[0].jsonObject
        val outerSockopt = outer.getValue("streamSettings").jsonObject.getValue("sockopt").jsonObject
        assertEquals("proxy-chain-1", outerSockopt.getValue("dialerProxy").jsonPrimitive.content)
        val mux = outer.getValue("mux").jsonObject
        assertEquals("true", mux.getValue("enabled").jsonPrimitive.content)
        assertEquals(8, mux.getValue("concurrency").jsonPrimitive.content.toInt())
        assertEquals(7, mux.getValue("xudpConcurrency").jsonPrimitive.content.toInt())
        assertEquals("reject", mux.getValue("xudpProxyUDP443").jsonPrimitive.content)

        val inner = outbounds[1].jsonObject
        val innerSockopt = inner["streamSettings"]?.jsonObject?.get("sockopt")?.jsonObject
        assertNull(innerSockopt?.get("dialerProxy"))
        assertEquals("true", inner.getValue("mux").jsonObject.getValue("enabled").jsonPrimitive.content)
    }

    @Test
    fun fragmentIsAttachedOnlyToOutboundsAllowedByTheAndroidPlan() {
        val json = build(
            plan = outboundPlan(
                outbounds = listOf(
                    proxyOutbound(XrayTags.PROXY, allowFragment = true),
                    proxyOutbound("proxy-no-fragment", allowFragment = false),
                ),
                proxyTargetKind = XrayRouteTargetKind.Outbound,
            ),
            renderingOptions = renderingOptions(
                primaryOutboundTag = XrayTags.PROXY,
                fragment = XrayFragmentOutboundOptions(
                    domainStrategy = "UseIPv4",
                    packets = "tlshello",
                    length = "10-20",
                    interval = "1-2",
                ),
            ),
        )

        val outbounds = json.getValue("outbounds").jsonArray
        assertEquals(
            listOf(XrayTags.PROXY, "proxy-no-fragment", XrayTags.DIRECT, XrayTags.BLOCK, XrayTags.FRAGMENT),
            outbounds.tags(),
        )
        val proxySockopt = outbounds[0].jsonObject.getValue("streamSettings").jsonObject
            .getValue("sockopt").jsonObject
        assertEquals(XrayTags.FRAGMENT, proxySockopt.getValue("dialerProxy").jsonPrimitive.content)
        val noFragmentSockopt = outbounds[1].jsonObject["streamSettings"]?.jsonObject?.get("sockopt")?.jsonObject
        assertNull(noFragmentSockopt?.get("dialerProxy"))
        val fragment = outbounds.last().jsonObject
        assertEquals("freedom", fragment.getValue("protocol").jsonPrimitive.content)
        val settings = fragment.getValue("settings").jsonObject
        assertEquals("UseIPv4", settings.getValue("domainStrategy").jsonPrimitive.content)
        val fragmentSettings = settings.getValue("fragment").jsonObject
        assertEquals("tlshello", fragmentSettings.getValue("packets").jsonPrimitive.content)
        assertEquals("10-20", fragmentSettings.getValue("length").jsonPrimitive.content)
        assertEquals("1-2", fragmentSettings.getValue("interval").jsonPrimitive.content)
    }

    @Test
    fun balancerKeepsAndroidFinalTcpUdpRouteAndObservatoryWithoutDefaultRouting() {
        val json = build(
            plan = outboundPlan(
                outbounds = listOf(
                    proxyOutbound("proxy-policy-10"),
                    proxyOutbound("proxy-policy-20"),
                ),
                proxyTargetKind = XrayRouteTargetKind.Balancer,
                balancers = listOf(
                    XrayBalancerPlan(
                        tag = XrayTags.PROXY,
                        selector = "proxy-policy-",
                        strategy = "leastPing",
                        fallbackTag = "proxy-policy-20",
                    ),
                ),
                selectors = listOf("proxy-policy-"),
                probeUrl = "http://127.0.0.1:18082/probe",
                probeInterval = "17s",
                // The Android standard Observatory renderer intentionally has no timeout field.
                probeTimeout = "4s",
            ),
            options = LocalProxyXrayConfigOptions(includeDefaultRouting = false),
            renderingOptions = renderingOptions(primaryOutboundTag = XrayTags.DEFAULT_ROUTE_LOOPBACK),
        )

        val outbounds = json.getValue("outbounds").jsonArray
        assertEquals(
            listOf(
                XrayTags.DEFAULT_ROUTE_LOOPBACK,
                "proxy-policy-10",
                "proxy-policy-20",
                XrayTags.DIRECT,
                XrayTags.BLOCK,
            ),
            outbounds.tags(),
        )
        val routing = json.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray
        val loopbackRoute = rules.first().jsonObject
        assertEquals(
            listOf(XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND),
            loopbackRoute.getValue("inboundTag").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(XrayTags.PROXY, loopbackRoute.getValue("balancerTag").jsonPrimitive.content)
        assertNull(loopbackRoute["network"])

        val finalRoute = rules.last().jsonObject
        assertEquals("tcp,udp", finalRoute.getValue("network").jsonPrimitive.content)
        assertEquals(XrayTags.PROXY, finalRoute.getValue("balancerTag").jsonPrimitive.content)
        assertNull(finalRoute["outboundTag"])
        assertFalse(rules.any { rule ->
            rule.jsonObject["ip"]?.jsonArray?.any { it.jsonPrimitive.content == "geoip:private" } == true
        })

        val balancer = routing.getValue("balancers").jsonArray.single().jsonObject
        assertEquals(XrayTags.PROXY, balancer.getValue("tag").jsonPrimitive.content)
        assertEquals(
            listOf("proxy-policy-"),
            balancer.getValue("selector").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("leastPing", balancer.getValue("strategy").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("proxy-policy-20", balancer.getValue("fallbackTag").jsonPrimitive.content)

        val observatory = json.getValue("observatory").jsonObject
        assertEquals("http://127.0.0.1:18082/probe", observatory.getValue("probeURL").jsonPrimitive.content)
        assertEquals("17s", observatory.getValue("probeInterval").jsonPrimitive.content)
        assertEquals("true", observatory.getValue("enableConcurrency").jsonPrimitive.content)
        assertEquals(
            listOf("proxy-policy-"),
            observatory.getValue("subjectSelector").jsonArray.map { it.jsonPrimitive.content },
        )
        assertNull(observatory["probeTimeout"])
        assertNull(json["burstObservatory"])
    }

    @Test
    fun generatedPlanValidationMatchesTheExistingSingleServerFactoryOrder() {
        val validationCases = listOf(
            LocalProxyXrayConfigOptions(
                socksPort = 0,
                httpProxyPort = 0,
                enableHttpProxy = true,
                httpProxyListenAddress = "0.0.0.0",
                listenAddress = "",
            ) to "Local SOCKS port must be in 1..65535",
            LocalProxyXrayConfigOptions(
                socksPort = 18_080,
                httpProxyPort = 0,
                enableHttpProxy = true,
                httpProxyListenAddress = "0.0.0.0",
                listenAddress = "",
            ) to "Local HTTP proxy port must be in 1..65535",
            LocalProxyXrayConfigOptions(
                socksPort = 18_080,
                httpProxyPort = 18_080,
                enableHttpProxy = true,
                httpProxyListenAddress = "0.0.0.0",
                listenAddress = "",
            ) to "Local HTTP proxy port must differ from the SOCKS port",
            LocalProxyXrayConfigOptions(
                socksPort = 18_080,
                httpProxyPort = 18_081,
                enableHttpProxy = true,
                httpProxyListenAddress = "0.0.0.0",
                listenAddress = "",
            ) to "Windows system HTTP proxy must listen on $DefaultLocalHttpProxyListenAddress",
            LocalProxyXrayConfigOptions(
                socksPort = 18_080,
                httpProxyPort = 18_081,
                enableHttpProxy = false,
                listenAddress = "",
            ) to "Local SOCKS listen address must not be blank",
        )

        validationCases.forEach { (options, expectedMessage) ->
            val existingFactoryError = assertFailsWith<IllegalArgumentException> {
                LocalProxyXrayConfigFactory.build(server = validVless(), options = options)
            }
            assertEquals(expectedMessage, existingFactoryError.message)

            val generatedFactoryError = assertFailsWith<IllegalArgumentException> {
                LocalProxyXrayConfigFactory.buildFromPlan(
                    plan = outboundPlan(
                        outbounds = listOf(proxyOutbound(XrayTags.PROXY)),
                        proxyTargetKind = XrayRouteTargetKind.Outbound,
                    ),
                    options = options,
                    renderingOptions = renderingOptions(primaryOutboundTag = XrayTags.PROXY),
                )
            }
            assertEquals(existingFactoryError.message, generatedFactoryError.message)
        }
    }

    private fun build(
        plan: XrayOutboundPlan,
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(),
        renderingOptions: XrayOutboundRenderingOptions = renderingOptions(),
    ) = Json.parseToJsonElement(
        LocalProxyXrayConfigFactory.buildFromPlan(plan, options, renderingOptions),
    ).jsonObject

    private fun outboundPlan(
        outbounds: List<XrayProxyOutboundServer>,
        proxyTargetKind: XrayRouteTargetKind,
        balancers: List<XrayBalancerPlan> = emptyList(),
        selectors: List<String> = emptyList(),
        probeUrl: String? = null,
        probeInterval: String? = null,
        probeTimeout: String? = null,
    ) = XrayOutboundPlan(
        proxyOutbounds = outbounds,
        balancers = balancers,
        observatorySelectors = selectors,
        routeTargets = mapOf(
            XrayTags.PROXY to XrayRouteTarget(XrayTags.PROXY, proxyTargetKind),
        ),
        dnsHostServers = emptyList(),
        observatoryProbeUrl = probeUrl,
        observatoryProbeInterval = probeInterval,
        observatoryProbeTimeout = probeTimeout,
    )

    private fun proxyOutbound(
        tag: String,
        dialerProxyTag: String? = null,
        allowFragment: Boolean = true,
    ) = XrayProxyOutboundServer(
        tag = tag,
        customOutbound = buildJsonObject {
            put("tag", tag)
            put("protocol", "socks")
            put(
                "settings",
                buildJsonObject {
                    put("address", "127.0.0.1")
                    put("port", 10_000)
                },
            )
        },
        dialerProxyTag = dialerProxyTag,
        allowFragment = allowFragment,
    )

    private fun renderingOptions(
        primaryOutboundTag: String? = XrayTags.PROXY,
        enableLocalDns: Boolean = false,
        dnsPlan: XrayDnsPlan? = null,
        dnsProxyOutboundTag: String? = null,
        mux: XrayMuxOutboundOptions? = null,
        fragment: XrayFragmentOutboundOptions? = null,
    ) = XrayOutboundRenderingOptions(
        primaryOutboundTag = primaryOutboundTag,
        enableLocalDns = enableLocalDns,
        dnsPlan = dnsPlan,
        dnsProxyOutboundTag = dnsProxyOutboundTag,
        transport = null,
        mux = mux,
        fragment = fragment,
    )

    private fun dnsPlan() = XrayDnsPlan(
        servers = Json.parseToJsonElement("[\"1.1.1.1\"]").jsonArray,
        queryStrategy = "UseIPv4",
        tag = XrayTags.PROXY_DNS,
        hosts = Json.parseToJsonElement("{}").jsonObject,
        fakeDns = null,
        nonIpQueryFallback = XrayDnsTcpFallback("9.9.9.9", 5_353),
        routingOptions = XrayDnsRoutingOptions(routeProxyDns = true, routeDirectDns = false),
    )

    private fun validVless() = VLESS(
        remarks = "Selected",
        id = "8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6",
        server = "example.com",
        port = "443",
    )

    private fun JsonArray.tags(): List<String> = map { item ->
        item.jsonObject.getValue("tag").jsonPrimitive.content
    }
}
