// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class XrayConfigSupportTest {
    @Test
    fun keeps_stable_tags_and_sniffing_overrides_in_common_code() {
        assertEquals("proxy", XrayTags.PROXY)
        assertEquals("tun", XrayProtocols.TUN)
        assertEquals(listOf("http", "tls", "quic"), xraySniffingDestOverrides(enableFakeDns = false))
        assertEquals(listOf("http", "tls", "quic", "fakedns"), xraySniffingDestOverrides(enableFakeDns = true))
    }

    @Test
    fun rewrites_json_without_losing_existing_fields() {
        val original = buildJsonObject {
            put("keep", "yes")
            put("remove", "no")
            put(
                "streamSettings",
                buildJsonObject {
                    put("sockopt", buildJsonObject { put("mark", 1) })
                },
            )
        }

        val rewritten = original
            .updatedWithout(setOf("remove")) { put("added", "value") }
            .updatedNestedObject("streamSettings", "sockopt") { put("mark", 255) }

        assertEquals("yes", rewritten["keep"]?.jsonPrimitive?.content)
        assertEquals("value", rewritten["added"]?.jsonPrimitive?.content)
        assertEquals(null, rewritten["remove"])
        assertEquals(
            255,
            rewritten["streamSettings"]
                ?.jsonObject
                ?.get("sockopt")
                ?.jsonObject
                ?.get("mark")
                ?.jsonPrimitive
                ?.content
                ?.toInt(),
        )
        assertEquals("{\"keep\":\"yes\"}\n", "{\"keep\":\"yes\"}\r\n".withSingleTrailingLf())
    }
}
