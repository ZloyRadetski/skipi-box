// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.proxy.server.model.NaiveProxy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Naive outbound is TCP only and has its own padding: no mux, no fragment, but a chain hop keeps its dialerProxy. */
class NaiveOutboundRenderTest {
    private val naive = NaiveProxy(remarks = "n", server = "naive.example.com", port = "443", user = "u", pass = "p")

    private val mux = XrayMuxOutboundOptions("8", "16", 0)
    private val fragment = XrayFragmentOutboundOptions(domainStrategy = "AsIs", packets = "tlshello", length = "100-200", interval = "10-20")

    private fun options(mux: XrayMuxOutboundOptions?, fragment: XrayFragmentOutboundOptions?) =
        XrayOutboundRenderingOptions(
            primaryOutboundTag = "proxy",
            enableLocalDns = false,
            dnsPlan = null,
            dnsProxyOutboundTag = null,
            transport = null,
            mux = mux,
            fragment = fragment,
        )

    private fun render(
        server: XrayProxyOutboundServer,
        mux: XrayMuxOutboundOptions? = this.mux,
        fragment: XrayFragmentOutboundOptions? = this.fragment,
    ): JsonObject = renderXrayOutbounds(listOf(server), options(mux, fragment))
        .map { it.jsonObject }.first { it["tag"]?.jsonPrimitive?.content == server.tag }

    @Test
    fun naiveNeverGetsMuxEvenWhenGlobalMuxIsOn() {
        assertNull(render(XrayProxyOutboundServer(tag = "proxy", server = naive))["mux"])
    }

    @Test
    fun naiveNeverGetsFragmentChainingEvenWhenAllowed() {
        val outbound = render(XrayProxyOutboundServer(tag = "proxy", server = naive, allowFragment = true))
        assertNull(outbound["streamSettings"])
    }

    @Test
    fun naiveChainHopKeepsItsDialerProxy() {
        val outbound = render(XrayProxyOutboundServer(tag = "proxy", server = naive, dialerProxyTag = "hop"))
        assertEquals("hop", outbound["streamSettings"]!!.jsonObject["sockopt"]!!.jsonObject["dialerProxy"]!!.jsonPrimitive.content)
        assertNull(outbound["mux"])
    }

    @Test
    fun overlayLeavesNaiveWithoutMuxAndFragment() {
        val rendered = naive.toXrayOutbound("proxy").toJsonObject()
        val out = listOf(rendered).withXrayMuxAndFragmentOverlay(mux, fragment).single()
        assertNull(out["mux"])
        assertNull(out["streamSettings"])
    }
}
