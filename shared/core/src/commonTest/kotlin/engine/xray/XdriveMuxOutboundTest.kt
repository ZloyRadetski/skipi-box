// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.proxy.server.model.V2RayParameters
import features.proxy.server.model.VLESS
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Xray core refuses an xdrive outbound without an enabled, non-negative mux and without a skip-free UDP/443 mode. */
class XdriveMuxOutboundTest {
    private val encryption = "mlkem768x25519plus.native.0rtt.GnDJ0ePW6KrjHz1G78pJeOX5jl8BPzLdv2Hi_t4RmUo"

    private fun vless(type: String) = VLESS(
        remarks = "x",
        id = "11111111-2222-4333-8444-555555555555",
        server = "drive.example.com",
        port = "443",
        encryption = encryption,
        parms = V2RayParameters(type = type, security = "tls", xdService = "google-drive"),
    )

    private fun options(mux: XrayMuxOutboundOptions?) = XrayOutboundRenderingOptions(
        primaryOutboundTag = "proxy",
        enableLocalDns = false,
        dnsPlan = null,
        dnsProxyOutboundTag = null,
        transport = null,
        mux = mux,
        fragment = null,
    )

    private fun rendered(type: String, mux: XrayMuxOutboundOptions?): JsonObject {
        val array: JsonArray =
            renderXrayOutbounds(listOf(XrayProxyOutboundServer(tag = "proxy", server = vless(type))), options(mux))
        return array.map { it.jsonObject }.first { it["tag"]?.jsonPrimitive?.content == "proxy" }
    }

    private fun JsonObject.muxObject() = this["mux"]?.jsonObject

    private fun assertCoreSafe(mux: JsonObject?) {
        assertNotNull(mux, "xdrive needs a mux")
        assertEquals("true", mux["enabled"]?.jsonPrimitive?.content)
        assertTrue(mux["concurrency"]?.jsonPrimitive?.int!! >= 0, "negative concurrency is refused by the core")
        assertTrue(mux["xudpConcurrency"]?.jsonPrimitive?.int!! >= 0, "negative xudpConcurrency is refused by the core")
        assertTrue(mux["xudpProxyUDP443"]?.jsonPrimitive?.content != "skip", "xudpProxyUDP443 skip is refused by the core")
    }

    @Test
    fun anXdriveServerGetsAMuxEvenWhenMuxIsOffGlobally() {
        assertCoreSafe(rendered("xdrive", mux = null).muxObject())
    }

    @Test
    fun globalMuxValuesTheCoreRefusesAreReplacedForXdrive() {
        // concurrency -1, xudpConcurrency -1 and the "skip" UDP/443 mode (index 2) are all rejected for xdrive
        assertCoreSafe(rendered("xdrive", XrayMuxOutboundOptions("-1", "-1", 2)).muxObject())
    }

    @Test
    fun validGlobalMuxValuesAreKeptForXdrive() {
        val mux = rendered("xdrive", XrayMuxOutboundOptions("7", "2", 1)).muxObject()!!

        assertEquals(7, mux["concurrency"]?.jsonPrimitive?.int)
        assertEquals(2, mux["xudpConcurrency"]?.jsonPrimitive?.int)
        assertEquals("allow", mux["xudpProxyUDP443"]?.jsonPrimitive?.content)
    }

    @Test
    fun otherTransportsKeepTheirGlobalMuxBehaviour() {
        assertNull(rendered("raw", mux = null).muxObject())
        val withGlobal = rendered("raw", XrayMuxOutboundOptions("-1", "-1", 2)).muxObject()!!
        assertEquals(-1, withGlobal["concurrency"]?.jsonPrimitive?.int)
        assertEquals("skip", withGlobal["xudpProxyUDP443"]?.jsonPrimitive?.content)
    }

    @Test
    fun theOverlayForAlreadyRenderedOutboundsFollowsTheSameRule() {
        val source = vless("xdrive").toXrayOutbound("proxy").toJsonObject()

        val off = listOf(source).withXrayMuxAndFragmentOverlay(mux = null, fragment = null).single().muxObject()
        val unsafe = listOf(source)
            .withXrayMuxAndFragmentOverlay(XrayMuxOutboundOptions("-1", "-1", 2), null).single().muxObject()

        assertCoreSafe(off)
        assertCoreSafe(unsafe)
    }
}
