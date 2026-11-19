// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import engine.hevtun.toConfigYaml
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

    @Test
    fun planWithoutHevHasNoHevKey() {
        assertNull(parse(buildCoreSessionPlanJson("{}", null, 0, false))["hev"])
        assertNull(parse(buildCoreSessionPlanJson("{}", null, 0, false, hevConfigYaml = "  ", hevTunFd = 7))["hev"])
    }

    @Test
    fun planWithHevCarriesYamlAndTunFd() {
        val yaml = "tunnel:\n  mtu: 1500\nmisc:\n  log-file: '/x/hev.log'\n"
        val plan = parse(buildCoreSessionPlanJson("{}", null, 0, false, hevConfigYaml = yaml, hevTunFd = 42))
        val hev = plan.getValue("hev").jsonObject
        assertEquals(yaml, hev.getValue("configYaml").jsonPrimitive.content)
        assertEquals(42, hev.getValue("tunFd").jsonPrimitive.int)
    }

    @Test
    fun hevYamlKeepsTheLogPath() {
        val config = engine.hevtun.HevSocks5TunnelConfig(
            configPath = "/d/tun.yml",
            logPath = "/d/logs/hev.log",
            socksAddress = "127.0.0.1",
            socksPort = 10808,
            mtu = 1500,
            ipv4Address = "172.19.0.1",
            ipv6Address = null,
        )
        val yaml = config.toConfigYaml()
        assertTrue("/d/logs/hev.log" in yaml)
        assertTrue("10808" in yaml)
    }

    @Test
    fun sessionErrorNamingHevGetsTheHevLogAppended() {
        val log = java.io.File.createTempFile("hev", ".log")
        try {
            log.writeText("ERROR: failed to initialize tunnel\n")
            assertEquals(
                "HEV TUN is not ready yet: ERROR: failed to initialize tunnel",
                withHevDiagnostics("HEV TUN is not ready yet", log.absolutePath),
            )
            assertEquals("xray failed", withHevDiagnostics("xray failed", log.absolutePath))
            assertEquals("HEV TUN exited", withHevDiagnostics("HEV TUN exited", ""))
        } finally {
            log.delete()
        }
    }
}
