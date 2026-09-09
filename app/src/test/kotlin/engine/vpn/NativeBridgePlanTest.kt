// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import engine.network.NetworkCidrAddress
import engine.xray.XrayOutboundPlan
import engine.xray.XrayProxyOutboundServer
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.OlcRtc
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeBridgePlanTest {

    @Test
    fun nativeBridgesUseTunDnsAndOnlyRewriteTheirOwnOutbounds() {
        val olc = olcRtc(key = "a".repeat(64))
        val awg = amneziaWg()
        val plan = outboundPlan(
            XrayProxyOutboundServer(tag = "olc", server = olc),
            XrayProxyOutboundServer(tag = "awg", server = awg),
        )

        val result = buildNativeBridgePlan(
            rawOutboundPlan = plan,
            tunOptions = tunOptions(dns = "9.9.9.9"),
            reservedPorts = emptySet(),
        )

        assertTrue(result.olcRtcYaml.orEmpty().contains("dns: 9.9.9.9:53"))
        assertTrue(!result.olcRtcYaml.orEmpty().contains("dns: 8.8.8.8:53"))
        assertTrue(result.olcRtcSocksPort in 1024..65535)
        assertTrue(result.amneziaWgSocksPort in 1024..65535)
        assertTrue(result.olcRtcSocksPort != result.amneziaWgSocksPort)
        assertNotNull(result.activeOlcRtcBridge)
        assertNotNull(result.activeAmneziaWgBridge)
        assertTrue(result.amneziaWgConfigJson.orEmpty().contains("\"s3\":60"))
        assertTrue(result.amneziaWgConfigJson.orEmpty().contains("\"dnsServers\":[\"9.9.9.9\"]"))

        val olcOutbound = result.outboundPlan.proxyOutbounds.first { it.tag == "olc" }.customOutbound
        val awgOutbound = result.outboundPlan.proxyOutbounds.first { it.tag == "awg" }.customOutbound
        assertNotNull(olcOutbound)
        assertNotNull(awgOutbound)
        assertTrue(olcOutbound.toString().contains("skipi_rtc_"))
        assertTrue(awgOutbound.toString().contains("127.0.0.1"))
    }

    @Test
    fun differentOlcRtcRuntimesAreRejectedInsteadOfBeingSilentlyRewritten() {
        val plan = outboundPlan(
            XrayProxyOutboundServer(tag = "first", server = olcRtc(key = "a".repeat(64))),
            XrayProxyOutboundServer(tag = "second", server = olcRtc(key = "b".repeat(64))),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            buildNativeBridgePlan(plan, tunOptions("1.1.1.1"), emptySet())
        }
        assertTrue(error.message.orEmpty().contains("multiple different olcRTC"))
    }

    @Test
    fun differentAmneziaWgRuntimesAreRejectedInsteadOfBeingSilentlyRewritten() {
        val first = amneziaWg()
        val second = amneziaWg().apply { secretKey = "different-secret" }
        val plan = outboundPlan(
            XrayProxyOutboundServer(tag = "first", server = first),
            XrayProxyOutboundServer(tag = "second", server = second),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            buildNativeBridgePlan(plan, tunOptions("1.1.1.1"), emptySet())
        }
        assertTrue(error.message.orEmpty().contains("multiple different AmneziaWG"))
    }

    @Test
    fun amneziaWgFinalMaskIsRejectedInsteadOfBeingSilentlyDropped() {
        val server = amneziaWg().apply { finalMask = "{}" }
        val error = assertFailsWith<IllegalArgumentException> {
            buildNativeBridgePlan(
                outboundPlan(XrayProxyOutboundServer(tag = "awg", server = server)),
                tunOptions("9.9.9.9"),
                emptySet(),
            )
        }
        assertTrue(error.message.orEmpty().contains("FinalMask"))
    }

    private fun outboundPlan(vararg outbounds: XrayProxyOutboundServer) = XrayOutboundPlan(
        proxyOutbounds = outbounds.toList(),
        balancers = emptyList(),
        observatorySelectors = emptyList(),
        routeTargets = emptyMap(),
        dnsHostServers = emptyList(),
    )

    private fun tunOptions(dns: String) = TunOptions(
        mtu = 1500,
        ipv4Address = NetworkCidrAddress("172.19.0.1", 30),
        ipv6Address = NetworkCidrAddress("fdfe:dcba:9876::1", 126),
        dnsServers = listOf(dns),
    )

    private fun olcRtc(key: String) = OlcRtc(
        remarks = "olc",
        provider = "jitsi",
        transport = "datachannel",
        roomUrl = "room",
        encryptionKey = key,
    )

    private fun amneziaWg() = AmneziaWg(
        remarks = "awg",
        server = "198.51.100.10",
        secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
        publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
        s3 = "60",
        s4 = "80",
    )
}
