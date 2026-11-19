// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreSessionPlanTest {
    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun planWithoutAwgHasNoAmneziaWgKey() {
        val plan = parse(buildCoreSessionPlanJson("{\"a\":1}", null, 0, connectionLog = false))
        assertEquals("{\"a\":1}", plan.getValue("xrayConfig").jsonPrimitive.content)
        assertFalse(plan.getValue("connectionLog").jsonPrimitive.boolean)
        assertNull(plan["amneziaWg"])
    }

    @Test
    fun blankAwgConfigOrNoPortOmitsAmneziaWg() {
        assertNull(parse(buildCoreSessionPlanJson("{}", "  ", 1080, false))["amneziaWg"])
        assertNull(parse(buildCoreSessionPlanJson("{}", "{\"x\":1}", 0, false))["amneziaWg"])
    }

    @Test
    fun planWithAwgCarriesConfigStringAndPort() {
        val plan = parse(buildCoreSessionPlanJson("{}", "{\"x\":1}", 1080, connectionLog = true))
        val awg = plan.getValue("amneziaWg").jsonObject
        assertEquals("{\"x\":1}", awg.getValue("config").jsonPrimitive.content)
        assertEquals(1080, awg.getValue("socksPort").jsonPrimitive.int)
        assertTrue(plan.getValue("connectionLog").jsonPrimitive.boolean)
    }

    @Test
    fun configsWithQuotesNewlinesAndUnicodeRoundTripExactly() {
        val xray = "{\n  \"remarks\": \"he said \\\"hi\\\"\n\",\r\n\t\"k\": \"\u0442\u0435\u0441\u0442 \\ /\"\n}"
        val awg = "{\"name\":\"a\\\"b\",\n\"pk\":\"x\ty\"}"
        val plan = parse(buildCoreSessionPlanJson(xray, awg, 4242, false))
        assertEquals(xray, plan.getValue("xrayConfig").jsonPrimitive.content)
        assertEquals(awg, plan.getValue("amneziaWg").jsonObject.getValue("config").jsonPrimitive.content)
    }

    @Test
    fun coreStopTimeoutIsBelowTheKotlinStepDeadline() {
        assertTrue(CoreStopTimeoutMillis > 0)
        assertTrue(CoreStopTimeoutMillis <= NativeStopStepDeadlineMillis - 500)
    }

    @Test
    fun abandonedCoreInstancesMakeTheRuntimeDirty() {
        assertNull(coreAbandonedDirtyReason(0))
        val reason = assertNotNull(coreAbandonedDirtyReason(2))
        assertTrue("abandoned" in reason && "2" in reason)
    }
}
