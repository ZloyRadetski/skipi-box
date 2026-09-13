// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayTrafficStatsConfigTest {
    @Test
    fun generatedConfigCollectsStatsWithoutApiListenerOrStatsService() {
        val config = GeneratedXrayConfig(
            log = buildJsonObject {},
            inbounds = buildJsonArray {},
            outbounds = buildJsonArray {},
        ).toJsonObject()

        assertTrue(config.containsKey("stats"))
        assertFalse(config.containsKey("api"))

        val policySystem = config.getValue("policy").jsonObject
            .getValue("system")
            .jsonObject
        assertEquals(true, policySystem.getValue("statsInboundUplink").jsonPrimitive.boolean)
        assertEquals(true, policySystem.getValue("statsOutboundDownlink").jsonPrimitive.boolean)
        assertFalse(config.toString().contains("StatsService"))
        assertFalse(config.toString().contains("\"listen\""))
    }

    @Test
    fun disabledCollectionDoesNotAddStatsFields() {
        val config = GeneratedXrayConfig(
            log = buildJsonObject {},
            inbounds = buildJsonArray {},
            outbounds = buildJsonArray {},
            collectTrafficStats = false,
        ).toJsonObject()

        assertFalse(config.containsKey("stats"))
        assertFalse(config.containsKey("policy"))
    }
}
