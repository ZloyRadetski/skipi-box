// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomXrayStrictTunnelTest {
    private fun outbound(tag: String, protocol: String) = buildJsonObject {
        put("tag", tag)
        put("protocol", protocol)
    }

    private fun rule(outboundTag: String, inboundTag: String? = null, domain: String? = null) = buildJsonObject {
        if (inboundTag != null) put("inboundTag", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(inboundTag)) })
        if (domain != null) put("domain", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(domain)) })
        put("outboundTag", outboundTag)
    }

    private fun config(outbounds: List<JsonObject>, rules: List<JsonObject>) = buildJsonObject {
        put("outbounds", buildJsonArray { outbounds.forEach(::add) })
        put("routing", buildJsonObject { put("rules", buildJsonArray { rules.forEach(::add) }) })
    }

    private fun JsonObject.rules() = this["routing"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun rulesToAFreedomOutboundAreDroppedWhateverItsTag() {
        val result = config(
            outbounds = listOf(outbound("vpn", "vless"), outbound("local", "freedom"), outbound("block", "blackhole")),
            rules = listOf(rule("local", domain = "geosite:discord"), rule("block", domain = "geosite:ads"), rule("vpn", domain = "x")),
        ).withStrictFullTunnelRouting()

        assertEquals(
            listOf("block", "vpn", "vpn"),
            result.rules().map { it["outboundTag"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun theTunnelEndsInACatchAllToTheProxySoNothingFallsToTheFirstOrDirectOutbound() {
        val result = config(
            outbounds = listOf(outbound("direct", "freedom"), outbound("vpn", "vless")),
            rules = emptyList(),
        ).withStrictFullTunnelRouting()

        val last = result.rules().last()
        assertEquals("vpn", last["outboundTag"]!!.jsonPrimitive.content)
        assertEquals("tcp,udp", last["network"]!!.jsonPrimitive.content)
    }

    @Test
    fun skipiOwnDirectDnsRouteSurvives() {
        val result = config(
            outbounds = listOf(outbound("vpn", "vless"), outbound(XrayTags.DIRECT, "freedom")),
            rules = listOf(rule(XrayTags.DIRECT, inboundTag = XrayTags.DIRECT_DNS), rule(XrayTags.DIRECT, domain = "x")),
        ).withStrictFullTunnelRouting()

        assertEquals(2, result.rules().size)
        assertEquals(XrayTags.DIRECT_DNS, result.rules().first()["inboundTag"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun aConfigWithoutAProxyOutboundIsLeftAlone() {
        val source = config(outbounds = listOf(outbound("direct", "freedom")), rules = listOf(rule("direct")))

        assertEquals(source, source.withStrictFullTunnelRouting())
    }
}
