// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import engine.xray.XrayOutboundPlan
import engine.xray.XrayProxyOutboundServer
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.VLESS
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
 * The AmneziaWG runner is one process-wide native runtime behind a private loopback SOCKS port.
 * The same plan serves Android's VpnService and the Desktop in-process Core.
 */
class AmneziaWgBridgePlanTest {
    private fun awg(host: String = "198.51.100.10") = AmneziaWg(
        remarks = "awg",
        server = host,
        secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
        publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
        s3 = "60",
        s4 = "80",
    )

    @Test
    fun noAmneziaWgServerMeansNoBridge() {
        val plan = planAmneziaWgBridge(
            servers = emptyList(),
            dnsServers = listOf("9.9.9.9"),
            reservedPorts = emptySet(),
        )

        assertNull(plan)
    }

    @Test
    fun theBridgeGetsAFreePortOutsideTheReservedOnesAndTheRunnerConfigWithDns() {
        val plan = assertNotNull(
            planAmneziaWgBridge(
                servers = listOf(awg()),
                dnsServers = listOf("9.9.9.9"),
                reservedPorts = setOf(10_809),
            ),
        )

        assertTrue(plan.socksPort in 1024..65_535)
        assertTrue(plan.socksPort != 10_809)
        assertTrue(plan.configJson.contains("\"s3\":60"))
        assertTrue(plan.configJson.contains("\"dnsServers\":[\"9.9.9.9\"]"))
        assertEquals("198.51.100.10", plan.server.server)
    }

    @Test
    fun theSameTunnelListedTwiceSharesOneRunnerButDifferentTunnelsAreRejected() {
        assertNotNull(
            planAmneziaWgBridge(listOf(awg(), awg()), listOf("1.1.1.1"), emptySet()),
        )

        val other = awg().apply { secretKey = "different-secret" }
        val error = assertFailsWith<IllegalArgumentException> {
            planAmneziaWgBridge(listOf(awg(), other), listOf("1.1.1.1"), emptySet())
        }
        assertTrue(error.message.orEmpty().contains("multiple different AmneziaWG"))
    }

    @Test
    fun finalMaskIsRejectedBecauseTheNativeRunnerCannotApplyIt() {
        val masked = awg().apply { finalMask = """{"type":"salamander"}""" }

        assertFailsWith<IllegalArgumentException> {
            planAmneziaWgBridge(listOf(masked), listOf("1.1.1.1"), emptySet())
        }
    }

    @Test
    fun aHostnameEndpointIsResolvedForTheRunnerButTheServerKeepsItsName() {
        val plan = assertNotNull(
            planAmneziaWgBridge(
                servers = listOf(awg(host = "relay.example")),
                dnsServers = listOf("9.9.9.9"),
                reservedPorts = emptySet(),
                resolveEndpoint = { source -> source.withNativeRunnerEndpointResolved { listOf("198.51.100.25") } },
            ),
        )

        assertTrue(plan.configJson.contains("198.51.100.25"))
        assertEquals("relay.example", plan.server.server)
    }

    @Test
    fun onlyTheMatchingOutboundIsRewrittenToTheLoopbackSocks() {
        val awgServer = awg()
        val regular = VLESS(remarks = "v", id = "u", server = "v.example", port = "443")
        val outbounds = XrayOutboundPlan(
            proxyOutbounds = listOf(
                XrayProxyOutboundServer(tag = "awg", server = awgServer),
                XrayProxyOutboundServer(tag = "vless", server = regular),
            ),
            balancers = emptyList(),
            observatorySelectors = emptyList(),
            routeTargets = emptyMap(),
            dnsHostServers = emptyList(),
        )
        val plan = assertNotNull(planAmneziaWgBridge(listOf(awgServer), listOf("1.1.1.1"), emptySet()))

        val rewritten = outbounds.withAmneziaWgBridge(plan)

        val awgOutbound = assertNotNull(rewritten.proxyOutbounds.first { it.tag == "awg" }.customOutbound)
        val server = awgOutbound.getValue("settings").jsonObject.getValue("servers").jsonArray.single().jsonObject
        assertEquals("socks", awgOutbound.getValue("protocol").jsonPrimitive.content)
        assertEquals("127.0.0.1", server.getValue("address").jsonPrimitive.content)
        assertEquals(plan.socksPort, server.getValue("port").jsonPrimitive.content.toInt())
        assertNull(rewritten.proxyOutbounds.first { it.tag == "vless" }.customOutbound)
    }
}
