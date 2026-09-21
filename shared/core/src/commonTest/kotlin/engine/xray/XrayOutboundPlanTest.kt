// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XrayOutboundPlanTest {
    @Test
    fun routeTargetsRenderTheCorrectXrayDestinationField() {
        val outbound = buildJsonObject {
            XrayRouteTarget("proxy", XrayRouteTargetKind.Outbound).applyTo(this)
        }
        val balancer = buildJsonObject {
            XrayRouteTarget("auto", XrayRouteTargetKind.Balancer).applyTo(this)
        }

        assertEquals("proxy", outbound["outboundTag"]?.jsonPrimitive?.content)
        assertEquals("auto", balancer["balancerTag"]?.jsonPrimitive?.content)
    }

    @Test
    fun balancersAndObservatoryKeepTheirPortableDefaults() {
        val balancer = buildXrayBalancers(
            listOf(
                XrayBalancerPlan(
                    tag = "auto",
                    selector = "auto-member-",
                    strategy = "leastPing",
                    fallbackTag = "auto-member-1",
                ),
            ),
        ).single()
        val observatory = buildXrayObservatory(
            selectors = listOf("auto-member-", "auto-member-"),
            probeUrl = "",
            fallbackProbeUrl = "https://probe.example/check",
            fallbackProbeInterval = "10s",
        )

        assertEquals("auto", balancer["tag"]?.jsonPrimitive?.content)
        assertEquals("auto-member-", balancer["selector"]?.jsonArray?.single()?.jsonPrimitive?.content)
        assertEquals("leastPing", balancer["strategy"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertEquals("auto-member-1", balancer["fallbackTag"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("auto-member-"),
            observatory?.get("subjectSelector")?.jsonArray?.map { entry -> entry.jsonPrimitive.content },
        )
        assertEquals("https://probe.example/check", observatory?.get("probeURL")?.jsonPrimitive?.content)
        assertEquals("10s", observatory?.get("probeInterval")?.jsonPrimitive?.content)
        assertNull(buildXrayObservatory(emptyList(), fallbackProbeUrl = "url", fallbackProbeInterval = "1m"))
    }
}
