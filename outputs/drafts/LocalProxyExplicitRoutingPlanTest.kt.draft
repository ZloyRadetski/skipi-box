// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import engine.xray.XrayBalancerPlan
import engine.xray.XrayOutboundPlan
import engine.xray.XrayOutboundRenderingOptions
import engine.xray.XrayProxyOutboundServer
import engine.xray.XrayRouteTarget
import engine.xray.XrayRouteTargetKind
import engine.xray.XrayRoutingPlan
import engine.xray.XrayRoutingRequest
import engine.xray.XrayRoutingRuleValidator
import engine.xray.XrayTags
import engine.xray.buildXrayBalancers
import engine.xray.planXrayRouting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Reviewed draft; install after Desktop behavior RED and before shared renderer API RED. */
class LocalProxyExplicitRoutingPlanTest {
    @Test
    fun suppliedPlanReplacesLocalRoutingForEitherDefaultRoutingOption() {
        val userRule = buildJsonObject {
            put("domain", buildJsonArray { add("example.test") })
            put("outboundTag", XrayTags.DIRECT)
        }
        val routingPlan = XrayRoutingPlan(
            domainStrategy = "AsIs",
            rules = buildJsonArray { add(userRule) },
            balancers = emptyList(),
            primaryOutboundTag = XrayTags.DIRECT,
        )
        val expectedRouting = buildJsonObject {
            put("domainStrategy", "AsIs")
            put("rules", buildJsonArray { add(userRule) })
        }

        listOf(false, true).forEach { includeDefaultRouting ->
            val config = buildConfig(
                plan = outboundPlan(proxyTargetKind = XrayRouteTargetKind.Outbound),
                options = LocalProxyXrayConfigOptions(includeDefaultRouting = includeDefaultRouting),
                routingPlan = routingPlan,
            )

            assertEquals(expectedRouting, config.getValue("routing").jsonObject)
            assertEquals(
                XrayTags.DIRECT,
                config.getValue("outbounds").jsonArray.first().jsonObject.getValue("tag").jsonPrimitive.content,
                "The supplied plan's primary outbound must control renderer ordering",
            )
            assertFalse(config.getValue("routing").jsonObject.getValue("rules").jsonArray.any { rule ->
                rule.jsonObject["ip"]?.jsonArray?.any { it.jsonPrimitive.content == "geoip:private" } == true
            }, "Explicit routing must not inherit the local default's private-IP direct rule")
        }
    }

    @Test
    fun suppliedDefaultBalancerPlanAddsAndSelectsTheDefaultRouteLoopback() {
        val balancer = XrayBalancerPlan(
            tag = XrayTags.PROXY,
            selector = "proxy-policy-",
            strategy = "leastPing",
            fallbackTag = "proxy-policy-7",
        )
        val routingPlan = planXrayRouting(
            request = XrayRoutingRequest(
                routeDomainStrategy = 1,
                routeRules = emptyList(),
                defaultRouteOutboundTag = XrayTags.PROXY,
                routeTargets = mapOf(
                    XrayTags.PROXY to XrayRouteTarget(XrayTags.PROXY, XrayRouteTargetKind.Balancer),
                ),
                balancers = buildXrayBalancers(listOf(balancer)),
                enableLocalDns = false,
                routeProxyDns = false,
                routeDirectDns = false,
                dnsHijackInboundTags = emptyList(),
            ),
            ruleValidator = AcceptAllRoutingRules,
        )

        val config = buildConfig(
            plan = outboundPlan(proxyTargetKind = XrayRouteTargetKind.Balancer),
            routingPlan = routingPlan,
        )

        val outbounds = config.getValue("outbounds").jsonArray
        assertEquals(
            XrayTags.DEFAULT_ROUTE_LOOPBACK,
            outbounds.first().jsonObject.getValue("tag").jsonPrimitive.content,
        )
        val routing = config.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray
        assertEquals(
            listOf(XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND),
            rules.first().jsonObject.getValue("inboundTag").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(XrayTags.PROXY, rules.first().jsonObject.getValue("balancerTag").jsonPrimitive.content)
        assertEquals("tcp,udp", rules.last().jsonObject.getValue("network").jsonPrimitive.content)
        assertEquals(XrayTags.PROXY, rules.last().jsonObject.getValue("balancerTag").jsonPrimitive.content)
        assertEquals(XrayTags.PROXY, routing.getValue("balancers").jsonArray.single().jsonObject
            .getValue("tag").jsonPrimitive.content)
    }

    private fun buildConfig(
        plan: XrayOutboundPlan,
        options: LocalProxyXrayConfigOptions = LocalProxyXrayConfigOptions(),
        routingPlan: XrayRoutingPlan,
    ): JsonObject = Json.parseToJsonElement(
        LocalProxyXrayConfigFactory.buildFromPlan(
            plan = plan,
            options = options,
            renderingOptions = XrayOutboundRenderingOptions(
                primaryOutboundTag = XrayTags.PROXY,
                enableLocalDns = false,
                dnsPlan = null,
                dnsProxyOutboundTag = null,
                transport = null,
                mux = null,
                fragment = null,
            ),
            routingPlan = routingPlan,
        ),
    ).jsonObject

    private fun outboundPlan(proxyTargetKind: XrayRouteTargetKind) = XrayOutboundPlan(
        proxyOutbounds = listOf(
            XrayProxyOutboundServer(
                tag = "proxy-policy-7",
                customOutbound = buildJsonObject {
                    put("tag", "proxy-policy-7")
                    put("protocol", "socks")
                },
            ),
        ),
        balancers = emptyList(),
        observatorySelectors = emptyList(),
        routeTargets = mapOf(
            XrayTags.PROXY to XrayRouteTarget(XrayTags.PROXY, proxyTargetKind),
        ),
        dnsHostServers = emptyList(),
    )

    private object AcceptAllRoutingRules : XrayRoutingRuleValidator {
        override fun isDomainRuleValid(rule: String): Boolean = true
        override fun isIpRuleValid(rule: String): Boolean = true
    }
}
