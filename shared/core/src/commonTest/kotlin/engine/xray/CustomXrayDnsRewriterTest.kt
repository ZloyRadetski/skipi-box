// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomXrayDnsRewriterTest {
    @Test
    fun replacesFixedDnsPartsWhileKeepingTheCustomProxyAndRules() {
        val result = rewriteCustomXrayDnsConfig(
            CustomXrayDnsRewriteRequest(
                config = buildJsonObject {
                    put("fakedns", buildJsonObject {})
                    put("fakeDns", buildJsonObject {})
                    put(
                        "outbounds",
                        buildJsonArray {
                            add(buildJsonObject { put("protocol", "vless") })
                            add(buildSimpleOutbound(XrayTags.DIRECT, XrayProtocols.FREEDOM))
                            add(buildSimpleOutbound(XrayTags.DNS_OUT, XrayProtocols.DNS))
                        },
                    )
                    put(
                        "routing",
                        buildJsonObject {
                            put(
                                "rules",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("outboundTag", XrayTags.BLOCK)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
                inbounds = listOf(buildJsonObject { put("tag", XrayTags.VPN_TUN_INBOUND) }),
                dnsPlan = XrayDnsPlan(
                    servers = buildJsonArray {},
                    queryStrategy = "UseIPv4",
                    tag = XrayTags.PROXY_DNS,
                    hosts = buildJsonObject {},
                    fakeDns = null,
                    nonIpQueryFallback = XrayDnsTcpFallback("1.1.1.1"),
                    routingOptions = XrayDnsRoutingOptions(
                        routeProxyDns = true,
                        routeDirectDns = true,
                    ),
                ),
                enableLocalDns = true,
                dnsHijackInboundTags = listOf(XrayTags.VPN_TUN_INBOUND),
                directOutbound = buildFreedomOutbound(XrayTags.DIRECT, "UseIPv4"),
            ),
        )

        val outboundTags = result.getValue("outbounds").jsonArray.map { outbound ->
            outbound.jsonObject["tag"]?.jsonPrimitive?.content
        }
        val rules = result.getValue("routing").jsonObject.getValue("rules").jsonArray

        assertEquals(listOf(XrayTags.PROXY, XrayTags.DIRECT, XrayTags.DNS_OUT), outboundTags)
        assertEquals(4, rules.size)
        assertEquals(
            XrayTags.DNS_OUT,
            rules[0].jsonObject.getValue("outboundTag").jsonPrimitive.content,
        )
        assertEquals(
            XrayTags.DIRECT,
            rules[1].jsonObject.getValue("outboundTag").jsonPrimitive.content,
        )
        assertEquals(
            XrayTags.PROXY,
            rules[2].jsonObject.getValue("outboundTag").jsonPrimitive.content,
        )
        assertEquals(
            XrayTags.BLOCK,
            rules[3].jsonObject.getValue("outboundTag").jsonPrimitive.content,
        )
        assertFalse(result.containsKey("fakedns"))
        assertFalse(result.containsKey("fakeDns"))
        assertTrue(result.containsKey("dns"))
    }
}
