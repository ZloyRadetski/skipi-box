// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomXrayConfigHardeningTest {
    private fun rewrite(extraTopLevel: Map<String, String>) = rewriteCustomXrayDnsConfig(
        CustomXrayDnsRewriteRequest(
            config = buildJsonObject {
                extraTopLevel.forEach { (key, listen) -> put(key, buildJsonObject { put("listen", listen) }) }
                put("outbounds", buildJsonArray { add(buildJsonObject { put("protocol", "vless") }) })
                put("policy", buildJsonObject { put("levels", buildJsonObject {}) })
            },
            inbounds = listOf(buildJsonObject { put("tag", XrayTags.VPN_TUN_INBOUND) }),
            dnsPlan = XrayDnsPlan(
                servers = buildJsonArray {},
                queryStrategy = "UseIPv4",
                tag = XrayTags.PROXY_DNS,
                hosts = buildJsonObject {},
                fakeDns = null,
                nonIpQueryFallback = XrayDnsTcpFallback("1.1.1.1"),
                routingOptions = XrayDnsRoutingOptions(routeProxyDns = true, routeDirectDns = true),
            ),
            enableLocalDns = true,
            dnsHijackInboundTags = listOf(XrayTags.VPN_TUN_INBOUND),
            directOutbound = buildFreedomOutbound(XrayTags.DIRECT, "UseIPv4"),
        ),
    )

    @Test
    fun importedConfigCannotOpenItsOwnNetworkListeners() {
        // `metrics` and `api` bind sockets of their own, outside the inbounds
        // SKIPI controls, so an imported profile must not bring them along.
        val result = rewrite(mapOf("metrics" to "0.0.0.0:9999", "api" to "0.0.0.0:9998", "reverse" to "x"))

        assertFalse(result.containsKey("metrics"))
        assertFalse(result.containsKey("api"))
        assertFalse(result.containsKey("reverse"))
    }

    @Test
    fun harmlessSectionsOfTheImportedConfigAreKept() {
        val result = rewrite(emptyMap())

        assertTrue(result.containsKey("policy"))
    }
}
