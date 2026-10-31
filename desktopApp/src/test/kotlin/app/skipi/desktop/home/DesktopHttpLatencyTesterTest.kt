// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Custom
import features.proxy.server.model.VLESS
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HTTP ping mode: a server is measured over its real transport, so UDP protocols (AmneziaWG,
 * WireGuard, Hysteria2) get a meaningful number where a TCP connect to their port would not.
 */
class DesktopHttpLatencyTesterTest {
    private val vless = VLESS(remarks = "v", id = "8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6", server = "v.example", port = "443")
    private val awg = AmneziaWg(
        remarks = "awg",
        server = "198.51.100.10",
        secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
        publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
    )

    @Test
    fun aRegularServerIsProbedThroughItsOwnSingleOutboundAndNothingElse() {
        val probe = buildDesktopLatencyProbe(vless)

        val root = Json.parseToJsonElement(probe.configJson).jsonObject
        val outbound = root.getValue("outbounds").jsonArray.single().jsonObject
        assertEquals("vless", outbound.getValue("protocol").jsonPrimitive.content)
        assertTrue("inbounds" !in root && "routing" !in root, "the probe needs only the outbound")
        assertNull(probe.amneziaWg)
    }

    @Test
    fun anAmneziaWgServerIsProbedThroughALoopbackSocksBridgeToItsRunner() {
        val probe = buildDesktopLatencyProbe(awg)

        val bridge = assertNotNull(probe.amneziaWg)
        val outbound = Json.parseToJsonElement(probe.configJson).jsonObject
            .getValue("outbounds").jsonArray.single().jsonObject
        assertEquals("socks", outbound.getValue("protocol").jsonPrimitive.content)
        val target = outbound.getValue("settings").jsonObject.let { settings ->
            settings["servers"]?.jsonArray?.firstOrNull()?.jsonObject ?: settings
        }
        assertEquals(bridge.socksPort, target.getValue("port").jsonPrimitive.content.toInt())
        assertTrue(bridge.configJson.contains("dnsServers"))
    }

    @Test
    fun serversWithoutASingleOutboundCannotBeProbed() {
        assertFailsWith<IllegalArgumentException> { buildDesktopLatencyProbe(Custom(remarks = "raw", configJson = "{}")) }
    }

    @Test
    fun theMeasuredDelayBecomesTheServerLatency() = runBlocking<Unit> {
        var seenUrl: String? = null
        val tester = DesktopHttpLatencyTester(
            measure = { _, url, _ -> seenUrl = url; Result.success(87L) },
        )

        val result = tester.test(vless, "https://probe.example/204", timeoutMillis = 5_000)

        assertEquals(DesktopServerLatencyResult.Success(87L), result)
        assertEquals("https://probe.example/204", seenUrl)
    }

    @Test
    fun aFailedMeasurementIsAnErrorWithTheReason() = runBlocking<Unit> {
        val tester = DesktopHttpLatencyTester(
            measure = { _, _, _ -> Result.failure(IllegalStateException("handshake failed")) },
        )

        val result = tester.test(vless, "https://probe.example/204", timeoutMillis = 5_000)

        assertEquals("handshake failed", (result as DesktopServerLatencyResult.Error).message)
    }

    @Test
    fun aMeasurementThatOutlastsTheTimeoutIsATimeout() = runBlocking<Unit> {
        val tester = DesktopHttpLatencyTester(
            measure = { _, _, _ -> Thread.sleep(400); Result.success(1L) },
        )

        val result = tester.test(vless, "https://probe.example/204", timeoutMillis = 50)

        assertEquals(DesktopServerLatencyResult.Timeout, result)
    }

    @Test
    fun anAmneziaWgProbeHandsItsRunnerToTheMeasurement() = runBlocking<Unit> {
        var bridgeSeen: DesktopAmneziaWgBridge? = null
        val tester = DesktopHttpLatencyTester(
            measure = { _, _, bridge -> bridgeSeen = bridge; Result.success(33L) },
        )

        val result = tester.test(awg, "https://probe.example/204", timeoutMillis = 5_000)

        assertEquals(DesktopServerLatencyResult.Success(33L), result)
        assertNotNull(bridgeSeen)
    }
}
