// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayTrafficStatsConfigTest {
    @Test
    fun enablesAllCoreTrafficCountersWithoutAddingAnApiListener() {
        val config = buildJsonObject {
            put("policy", buildJsonObject { put("preserved", true) })
        }.withXrayTrafficStatsConfig()

        val system = config.getValue("policy").jsonObject.getValue("system").jsonObject
        assertTrue(config.containsKey("stats"))
        assertEquals(true, config.getValue("policy").jsonObject.getValue("preserved").jsonPrimitive.content.toBoolean())
        assertEquals(true, system.getValue("statsInboundUplink").jsonPrimitive.content.toBoolean())
        assertEquals(true, system.getValue("statsInboundDownlink").jsonPrimitive.content.toBoolean())
        assertEquals(true, system.getValue("statsOutboundUplink").jsonPrimitive.content.toBoolean())
        assertEquals(true, system.getValue("statsOutboundDownlink").jsonPrimitive.content.toBoolean())
        assertFalse(config.containsKey("api"))
    }

    @Test
    fun optionalBuilderLeavesStatsOutWhenDisabled() {
        val config = buildJsonObject {
            putXrayTrafficStatsConfig(enabled = false)
        }

        assertFalse(config.containsKey("stats"))
        assertFalse(config.containsKey("policy"))
    }
}
