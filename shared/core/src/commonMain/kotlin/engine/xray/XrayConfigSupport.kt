// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

val XrayConfigJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

val XrayConfigPrettyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** Stable tags used by both generated Xray configurations and their routing. */
object XrayTags {
    const val PROXY = "proxy"
    const val DIRECT = "direct"
    const val BLOCK = "block"
    const val DNS_OUT = "dns-out"
    const val PROXY_DNS = "dns-proxy"
    const val DIRECT_DNS = "dns-direct"
    const val LOCAL_SOCKS_INBOUND = "socks-in"
    const val VPN_APPEND_HTTP_INBOUND = "vpn-http-in"
    const val TPROXY_INBOUND = "tproxy-in"
    const val TPROXY_HTTP_INBOUND = "tproxy-http-in"
    const val FRAGMENT = "fragment"
    const val VPN_TUN_INBOUND = "vpn-tun-in"
    const val TUN2SOCKS_INBOUND = "tun2socks-in"
    const val TUN2SOCKS_HTTP_INBOUND = "tun2socks-http-in"
    const val BPF2SOCKS_INBOUND = "bpf2socks-in"
    const val BPF2SOCKS_HTTP_INBOUND = "bpf2socks-http-in"
    const val DEFAULT_ROUTE_LOOPBACK = "skipi-internal-default-route-loopback"
    const val DEFAULT_ROUTE_LOOPBACK_INBOUND = "skipi-internal-default-route-loopback-in"
    /** Lets an outbound-only DNS dialer re-enter routing and use the proxy balancer. */
    const val DNS_PROXY_LOOPBACK = "skipi-internal-dns-proxy-loopback"
    const val DNS_PROXY_LOOPBACK_INBOUND = "skipi-internal-dns-proxy-loopback-in"

    val FIXED_OUTBOUND_TAGS = setOf(
        PROXY,
        DIRECT,
        BLOCK,
        DNS_OUT,
        FRAGMENT,
    )
}

object XrayProtocols {
    const val TUN = "tun"
    const val DNS = "dns"
    const val FREEDOM = "freedom"
    const val BLACKHOLE = "blackhole"
    const val SOCKS = "socks"
    const val HTTP = "http"
    const val TUNNEL = "tunnel"
    const val LOOPBACK = "loopback"
}

fun String.withSingleTrailingLf(): String = trimEnd('\r', '\n') + "\n"

fun xraySniffingDestOverrides(enableFakeDns: Boolean): List<String> = buildList {
    add("http")
    add("tls")
    add("quic")
    if (enableFakeDns) {
        add("fakedns")
    }
}

fun Iterable<String>.toJsonStringArray(): JsonArray = buildJsonArray {
    forEach { item -> add(JsonPrimitive(item)) }
}

fun Iterable<JsonObject>.toJsonObjectArray(): JsonArray = buildJsonArray {
    forEach { item -> add(item) }
}

fun JsonObject.updated(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
    this@updated.forEach { (name, value) -> put(name, value) }
    block()
}

fun JsonObject.updatedWithout(
    keys: Set<String>,
    block: JsonObjectBuilder.() -> Unit = {},
): JsonObject = buildJsonObject {
    this@updatedWithout.forEach { (name, value) ->
        if (name !in keys) {
            put(name, value)
        }
    }
    block()
}

fun JsonObject.updatedNestedObject(
    name: String,
    nestedName: String,
    block: JsonObjectBuilder.() -> Unit,
): JsonObject {
    val parent = objectValue(name) ?: buildJsonObject {}
    val nested = parent.objectValue(nestedName) ?: buildJsonObject {}
    return updated {
        put(
            name,
            parent.updated {
                put(nestedName, nested.updated(block))
            },
        )
    }
}

fun JsonObject.stringValue(name: String): String? {
    return (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}

fun JsonObject.objectValue(name: String): JsonObject? = this[name] as? JsonObject

fun JsonObject.arrayValue(name: String): JsonArray? = this[name] as? JsonArray

fun JsonObjectBuilder.putIfNotBlank(name: String, value: String?) {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isNotEmpty()) {
        put(name, trimmed)
    }
}

fun JsonObjectBuilder.putJsonStringArrayIfNotEmpty(name: String, values: List<String>) {
    if (values.isNotEmpty()) {
        put(name, values.toJsonStringArray())
    }
}

fun JsonObjectBuilder.putIfNotEmpty(name: String, value: JsonObject) {
    if (value.isNotEmpty()) {
        put(name, value)
    }
}

fun JsonObjectBuilder.putIfNotNull(name: String, value: JsonElement?) {
    if (value != null) {
        put(name, value)
    }
}
