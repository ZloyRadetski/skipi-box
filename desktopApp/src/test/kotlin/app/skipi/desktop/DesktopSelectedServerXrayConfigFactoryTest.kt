// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0
package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import engine.xray.XrayTags
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopSelectedServerXrayConfigFactoryTest {
    @Test
    fun ordinarySelectedServerKeepsTheLocalListenerShapeAndIsValidated() {
        val server = Socks(remarks = "Local route", server = "proxy.example", port = "1080")
        val result = build(
            library = library(selectedId = 10, stored(10, server)),
            options = LocalProxyXrayConfigOptions(
                socksPort = 19_080,
                listenAddress = "127.0.0.1",
                username = "desktop-user",
                password = "desktop-secret",
            ),
        )
        val root = Json.parseToJsonElement(result).jsonObject
        val socks = root.getValue("inbounds").jsonArray.single().jsonObject
        val settings = socks.getValue("settings").jsonObject
        assertEquals(19_080, socks.getValue("port").jsonPrimitive.content.toInt())
        assertEquals("password", settings.getValue("auth").jsonPrimitive.content)
        assertEquals("desktop-user", settings.getValue("users").jsonArray.single().jsonObject.getValue("user").jsonPrimitive.content)
        assertEquals(listOf("proxy", "direct", "block"), root.getValue("outbounds").jsonArray.tags())

        val invalid = buildResult(
            library = library(
                selectedId = 11,
                stored(11, Socks(remarks = "Invalid", server = "", port = "0")),
            ),
        )
        assertTrue(assertNotNull(invalid).isFailure)
    }

    @Test
    fun selectedChainUsesReversedHopOrderAndItsDialerLinks() {
        val library = library(
            selectedId = 99,
            stored(10, Socks(remarks = "Hop one", server = "hop-one.example", port = "1080")),
            stored(20, Socks(remarks = "Hop two", server = "hop-two.example", port = "2080")),
            stored(99, ChainProxy(remarks = "Two hops", proxyServerIds = listOf(10, 20))),
        )

        val root = Json.parseToJsonElement(build(library)).jsonObject
        val outbounds = root.getValue("outbounds").jsonArray
        assertEquals(listOf("proxy", "proxy-chain-1", "direct", "block"), outbounds.tags())
        assertEquals(
            "hop-two.example",
            outbounds[0].jsonObject.getValue("settings").jsonObject.getValue("address").jsonPrimitive.content,
        )
        assertEquals(
            "proxy-chain-1",
            outbounds[0].jsonObject.getValue("streamSettings").jsonObject
                .getValue("sockopt").jsonObject.getValue("dialerProxy").jsonPrimitive.content,
        )
        assertEquals(
            "hop-one.example",
            outbounds[1].jsonObject.getValue("settings").jsonObject.getValue("address").jsonPrimitive.content,
        )
        assertFalse(
            outbounds[1].jsonObject["streamSettings"]?.jsonObject?.get("sockopt")?.jsonObject
                ?.containsKey("dialerProxy") == true,
        )
    }

    @Test
    fun nullAndMinusTwoStoredGroupsMapToManualAndAutoMembershipIds() {
        val manual = library(
            selectedId = 90,
            stored(10, Socks(remarks = "Manual member", server = "manual.example", port = "1080"), subscriptionId = null),
            stored(20, Socks(remarks = "Auto member", server = "auto.example", port = "1080"), subscriptionId = -2),
            stored(
                90,
                StrategyGroup(
                    remarks = "Manual filter",
                    strategy = StrategyGroupConstants.TYPE_SELECT,
                    subscriptionGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                ),
            ),
        )
        val manualRoot = Json.parseToJsonElement(build(manual)).jsonObject
        val manualOutbounds = manualRoot.getValue("outbounds").jsonArray
        assertEquals(listOf("proxy", "direct", "block"), manualOutbounds.tags())
        assertEquals(
            "manual.example",
            manualOutbounds.first().jsonObject.getValue("settings").jsonObject.getValue("address").jsonPrimitive.content,
        )

        val automatic = library(
            selectedId = 91,
            stored(10, Socks(remarks = "Manual member", server = "manual.example", port = "1080"), subscriptionId = null),
            stored(20, Socks(remarks = "Auto member", server = "auto.example", port = "1080"), subscriptionId = -2),
            stored(
                91,
                StrategyGroup(
                    remarks = "Auto filter",
                    strategy = StrategyGroupConstants.TYPE_SELECT,
                    subscriptionGroupId = -2,
                ),
            ),
        )
        val automaticRoot = Json.parseToJsonElement(build(automatic)).jsonObject
        val automaticOutbounds = automaticRoot.getValue("outbounds").jsonArray
        assertEquals(listOf("proxy", "direct", "block"), automaticOutbounds.tags())
        assertEquals(
            "auto.example",
            automaticOutbounds.first().jsonObject.getValue("settings").jsonObject.getValue("address").jsonPrimitive.content,
        )
    }

    @Test
    fun selectedAutomaticGroupUsesTheLatestLatencyAndSubscriptionProbeSnapshot() {
        val library = library(
            selectedId = 90,
            stored(10, Socks(remarks = "Alpha", server = "alpha.example", port = "1080")),
            stored(20, Socks(remarks = "Beta", server = "beta.example", port = "1080")),
            stored(30, Socks(remarks = "Gamma", server = "gamma.example", port = "1080")),
            stored(
                90,
                StrategyGroup(
                    remarks = "Automatic",
                    strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                    proxyServerIds = listOf(10, 20, 30),
                    probeUrl = " ",
                    probeInterval = "23s",
                    probeTimeout = "4s",
                ),
            ),
        )

        val firstRoot = Json.parseToJsonElement(
            build(
                library = library,
                subscriptionPingUrl = "http://127.0.0.1:18082/first",
                latencyByServerId = mapOf(
                    10 to DesktopServerLatencyResult.Success(18),
                    20 to DesktopServerLatencyResult.Success(70),
                    30 to DesktopServerLatencyResult.Timeout,
                ),
            ),
        ).jsonObject
        val latestRoot = Json.parseToJsonElement(
            build(
                library = library,
                subscriptionPingUrl = "http://127.0.0.1:18083/latest",
                latencyByServerId = mapOf(
                    10 to DesktopServerLatencyResult.Timeout,
                    20 to DesktopServerLatencyResult.Success(70),
                    30 to DesktopServerLatencyResult.Error("offline"),
                ),
            ),
        ).jsonObject

        val firstBalancer = firstRoot.getValue("routing").jsonObject.getValue("balancers").jsonArray.single().jsonObject
        val latestBalancer = latestRoot.getValue("routing").jsonObject.getValue("balancers").jsonArray.single().jsonObject
        assertEquals("proxy-policy-10", firstBalancer.getValue("fallbackTag").jsonPrimitive.content)
        assertEquals("proxy-policy-20", latestBalancer.getValue("fallbackTag").jsonPrimitive.content)
        assertEquals(
            "http://127.0.0.1:18083/latest",
            latestRoot.getValue("observatory").jsonObject.getValue("probeURL").jsonPrimitive.content,
        )
        assertEquals("23s", latestRoot.getValue("observatory").jsonObject.getValue("probeInterval").jsonPrimitive.content)
        assertEquals(listOf("proxy-policy-"), latestRoot.getValue("observatory").jsonObject
            .getValue("subjectSelector").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(XrayTags.PROXY, latestRoot.getValue("routing").jsonObject
            .getValue("rules").jsonArray.last().jsonObject.getValue("balancerTag").jsonPrimitive.content)
    }

    @Test
    fun automaticGroupWithOnlyFailedLatencyKeepsTheFirstMemberAsFallback() {
        val library = library(
            selectedId = 90,
            stored(10, Socks(remarks = "First member", server = "first.example", port = "1080")),
            stored(20, Socks(remarks = "Second member", server = "second.example", port = "1080")),
            stored(
                90,
                StrategyGroup(
                    remarks = "Automatic",
                    strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                    proxyServerIds = listOf(10, 20),
                ),
            ),
        )

        val root = Json.parseToJsonElement(
            build(
                library = library,
                latencyByServerId = mapOf(
                    10 to DesktopServerLatencyResult.Timeout,
                    20 to DesktopServerLatencyResult.Error("failure42"),
                ),
            ),
        ).jsonObject

        val balancer = root.getValue("routing").jsonObject.getValue("balancers").jsonArray.single().jsonObject
        assertEquals("proxy-policy-10", balancer.getValue("fallbackTag").jsonPrimitive.content)
    }

    @Test
    fun invalidLocalListenerOptionsFailBeforeAnInvalidSelectedChain() {
        val result = buildResult(
            library = library(
                selectedId = 90,
                stored(90, ChainProxy(remarks = "Invalid chain", proxyServerIds = listOf(10))),
            ),
            options = LocalProxyXrayConfigOptions(socksPort = 0),
        )

        assertEquals(
            "Local SOCKS port must be in 1..65535",
            assertNotNull(result).exceptionOrNull()?.message,
        )
    }

    @Test
    fun configOwnedStrategyGroupUsesTheReferencedConfContent() {
        val library = library(
            selectedId = 90,
            stored(10, Socks(remarks = "Node A", server = "node-a.example", port = "1080")),
            stored(20, Socks(remarks = "Node B", server = "node-b.example", port = "1080")),
            stored(
                90,
                StrategyGroup(
                    remarks = "Config-owned group",
                    strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                    sourceTrafficConfigId = 7,
                    sourcePolicyGroupName = "AUTO",
                    probeUrl = "http://127.0.0.1:18084/probe",
                    probeInterval = "31s",
                ),
            ),
        )

        val root = Json.parseToJsonElement(
            build(
                library = library,
                trafficConfigContentById = mapOf(
                    7 to "[Proxy Group]\nAuto = url-test, Node A, Node B, url=http://127.0.0.1:18084/probe, interval=31",
                ),
                subscriptionPingUrl = "http://127.0.0.1:18085/subscription",
            ),
        ).jsonObject

        assertEquals(
            listOf(
                XrayTags.DEFAULT_ROUTE_LOOPBACK,
                "proxy-policy-10",
                "proxy-policy-20",
                XrayTags.DIRECT,
                XrayTags.BLOCK,
            ),
            root.getValue("outbounds").jsonArray.tags(),
        )
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray
        val loopbackRoute = rules.first().jsonObject
        assertEquals(
            listOf(XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND),
            loopbackRoute.getValue("inboundTag").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(XrayTags.PROXY, loopbackRoute.getValue("balancerTag").jsonPrimitive.content)
        val finalRoute = rules.last().jsonObject
        assertEquals("tcp,udp", finalRoute.getValue("network").jsonPrimitive.content)
        assertEquals(XrayTags.PROXY, finalRoute.getValue("balancerTag").jsonPrimitive.content)
        val observatory = root.getValue("observatory").jsonObject
        assertEquals("http://127.0.0.1:18084/probe", observatory.getValue("probeURL").jsonPrimitive.content)
        assertEquals("31s", observatory.getValue("probeInterval").jsonPrimitive.content)
    }

    @Test
    fun selectedOpaqueFirstRowFailureDoesNotFallThroughToLaterDuplicateId() {
        val result = buildResult(
            library = DesktopServerLibrary(
                selectedServerId = 7,
                servers = listOf(
                    DesktopStoredProxyServer(id = 7, serverJson = "{not-json"),
                    stored(7, Socks(remarks = "Later valid duplicate", server = "later.example", port = "1080")),
                ),
            ),
        )

        assertTrue(assertNotNull(result).isFailure)
    }

    @Test
    fun noSelectionProducesNoGeneratedLocalConfig() {
        assertEquals(null, buildResult(library = DesktopServerLibrary()))
    }

    private fun build(
        library: DesktopServerLibrary,
        trafficConfigContentById: Map<Int, String> = emptyMap(),
        subscriptionPingUrl: String = "http://127.0.0.1:18082/subscription-ping",
        latencyByServerId: Map<Int, DesktopServerLatencyResult> = emptyMap(),
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(socksPort = 19_080),
    ): String = assertNotNull(
        buildResult(library, trafficConfigContentById, subscriptionPingUrl, latencyByServerId, options),
    ).getOrThrow()

    private fun buildResult(
        library: DesktopServerLibrary,
        trafficConfigContentById: Map<Int, String> = emptyMap(),
        subscriptionPingUrl: String = "http://127.0.0.1:18082/subscription-ping",
        latencyByServerId: Map<Int, DesktopServerLatencyResult> = emptyMap(),
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(socksPort = 19_080),
    ) = DesktopSelectedServerXrayConfigFactory.build(
        serverLibrary = library,
        routing = RoutingConfigRecord(),
        trafficConfigContentById = trafficConfigContentById,
        options = options,
        subscriptionPingUrl = subscriptionPingUrl,
        latencyByServerId = latencyByServerId,
    )

    private fun library(selectedId: Int, vararg servers: DesktopStoredProxyServer) =
        DesktopServerLibrary(selectedServerId = selectedId, servers = servers.toList())

    private fun stored(
        id: Int,
        server: ProxyServer<*>,
        subscriptionId: Int? = null,
    ) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )

    private fun kotlinx.serialization.json.JsonArray.tags(): List<String> = map { outbound ->
        outbound.jsonObject.getValue("tag").jsonPrimitive.content
    }
}
