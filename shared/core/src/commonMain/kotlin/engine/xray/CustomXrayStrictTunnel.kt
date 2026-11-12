// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The strict full tunnel for a raw Xray profile: no rule may send traffic out of the device directly, and
 * whatever the rules do not match goes to the profile's proxy outbound.
 *
 * A rule that targets a `freedom` outbound (whatever its tag) is dropped, and a catch-all to the proxy
 * closes the rule list, so the profile's own "first outbound is the default" cannot leave through a direct
 * one. SKIPI's DNS route for its direct DNS upstream stays: it carries name lookups, not application
 * traffic, and the proxy's own address still has to be resolved. A profile with no proxy outbound is
 * returned as it is, because there is nothing to route to.
 */
fun JsonObject.withStrictFullTunnelRouting(): JsonObject {
    val outbounds = (this["outbounds"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val freedomTags = outbounds
        .filter { outbound -> outbound.stringValue("protocol").equals(XrayProtocols.FREEDOM, ignoreCase = true) }
        .mapNotNull { outbound -> outbound.stringValue("tag") }
        .toSet() + XrayTags.DIRECT
    val proxyTag = outbounds.firstOrNull { it.stringValue("tag") == XrayTags.PROXY }?.stringValue("tag")
        ?: outbounds.firstOrNull { outbound ->
            val protocol = outbound.stringValue("protocol")?.lowercase()
            val tag = outbound.stringValue("tag")
            !tag.isNullOrBlank() && tag !in freedomTags && tag !in XrayTags.FIXED_OUTBOUND_TAGS &&
                protocol != XrayProtocols.FREEDOM && protocol != XrayProtocols.BLACKHOLE && protocol != XrayProtocols.DNS
        }?.stringValue("tag")
        ?: return this

    val routing = objectValue("routing") ?: buildJsonObject {}
    val rules = routing.arrayValue("rules").orEmpty().filter { element ->
        val rule = element as? JsonObject ?: return@filter true
        val toDirect = rule.stringValue("outboundTag") in freedomTags
        !toDirect || rule.isDirectDnsUpstreamRoute()
    }
    return updated {
        put(
            "routing",
            routing.updated {
                put(
                    "rules",
                    buildJsonArray {
                        rules.forEach(::add)
                        add(
                            buildJsonObject {
                                put("network", "tcp,udp")
                                put("outboundTag", proxyTag)
                            },
                        )
                    },
                )
            },
        )
    }
}

private fun JsonObject.isDirectDnsUpstreamRoute(): Boolean {
    val inbounds = (this["inboundTag"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
    return inbounds.isNotEmpty() && inbounds.all { tag -> tag == XrayTags.DIRECT_DNS }
}
