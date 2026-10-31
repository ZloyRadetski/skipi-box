// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import engine.network.NetworkCidrAddress
import engine.xray.XrayOutboundPlan
import engine.xray.XrayProxyOutboundServer
import features.proxy.server.model.AmneziaWg
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeBridgePlanTest {

    @Test
    fun nativeBridgeUsesTunDnsAndOnlyRewritesItsOwnOutbound() {
        val awg = amneziaWg()
        val plan = outboundPlan(
            XrayProxyOutboundServer(tag = "awg", server = awg),
        )

        val result = buildNativeBridgePlan(
            rawOutboundPlan = plan,
            tunOptions = tunOptions(dns = "9.9.9.9"),
            reservedPorts = emptySet(),
        )

        assertTrue(result.amneziaWgSocksPort in 1024..65535)
        assertNotNull(result.activeAmneziaWgBridge)
        assertTrue(result.amneziaWgConfigJson.orEmpty().contains("\"s3\":60"))
        assertTrue(result.amneziaWgConfigJson.orEmpty().contains("\"dnsServers\":[\"9.9.9.9\"]"))

        val awgOutbound = result.outboundPlan.proxyOutbounds.first { it.tag == "awg" }.customOutbound
        assertNotNull(awgOutbound)
        assertTrue(awgOutbound.toString().contains("127.0.0.1"))
    }

    @Test
    fun hostnameEndpointIsResolvedBeforeNativeAmneziaWgConfiguration() {
        val awg = amneziaWg().copy(server = "relay.example", port = "50125")
        val result = buildNativeBridgePlan(
            rawOutboundPlan = outboundPlan(XrayProxyOutboundServer(tag = "awg", server = awg)),
            tunOptions = tunOptions(dns = "9.9.9.9"),
            reservedPorts = emptySet(),
            resolveAmneziaWgEndpoint = { source ->
                source.withNativeRunnerEndpointResolved {
                    listOf("2001:db8::10", "198.51.100.25")
                }
            },
        )

        assertTrue(result.amneziaWgConfigJson.orEmpty().contains("198.51.100.25:50125"))
        assertTrue(!result.amneziaWgConfigJson.orEmpty().contains("relay.example"))
        assertEquals("relay.example", result.activeAmneziaWgBridge?.server?.server)
    }

    @Test
    fun unresolvedHostnameEndpointIsDeferredToNativeAmneziaWgResolver() {
        val awg = amneziaWg().copy(server = "relay.example", port = "50125")

        val result = buildNativeBridgePlan(
            rawOutboundPlan = outboundPlan(XrayProxyOutboundServer(tag = "awg", server = awg)),
            tunOptions = tunOptions(dns = "9.9.9.9"),
            reservedPorts = emptySet(),
            resolveAmneziaWgEndpoint = { source ->
                source.withNativeRunnerEndpointResolved { emptyList() }
            },
        )

        assertTrue(result.amneziaWgConfigJson.orEmpty().contains("relay.example:50125"))
        assertEquals("relay.example", result.activeAmneziaWgBridge?.server?.server)
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

    @Test
    fun unsafeAmneziaWgObfuscationIsRejectedBeforeTheNativeRunnerStarts() {
        val server = amneziaWg().apply { jc = "129" }

        val error = assertFailsWith<IllegalArgumentException> {
            server.toNativeRunnerConfigJson(
                tag = "skipi_awg_runtime",
                dnsServers = listOf("9.9.9.9"),
            )
        }

        assertTrue(error.message.orEmpty().contains("AmneziaWgJcOutOfRange"))
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

    private fun amneziaWg() = AmneziaWg(
        remarks = "awg",
        server = "198.51.100.10",
        secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
        publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
        s3 = "60",
        s4 = "80",
    )
}
