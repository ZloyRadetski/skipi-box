// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import engine.network.toPortOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import utils.toTrimmedNonEmptyDistinctList

@Serializable
data class Custom(
    var remarks: String = "",
    var overrideInboundAndDns: Boolean = true,
    var configJson: String = "",
) : ProxyServer<Custom> {
    override fun getInfo(): ProxyServerInfo {
        return ProxyServerInfo(remarks, customXrayConfigSummary(configJson), customXrayConfigProtocolDisplayName(configJson))
    }

    override fun toXrayOutbound(tag: String): OutboundObject {
        throw UnsupportedOperationException("Custom proxy servers are converted by XrayConfigFactory")
    }

    override fun update(other: ProxyServer<*>) {
        if (other !is Custom) {
            proxyServerTypeMismatch()
        }
        remarks = other.remarks
        overrideInboundAndDns = other.overrideInboundAndDns
        configJson = other.configJson
    }

    override fun validateBasic(): List<ProxyServerValidationIssue> = validateFull()

    override fun validateFull(): List<ProxyServerValidationIssue> = buildList {
        validateRemarks(remarks)
        validateCustomXrayConfigJson(configJson)
    }

    override fun connectionFingerprint(): String {
        return customConnectionFingerprint(configJson)
    }
}

fun customConnectionFingerprint(configJson: String): String {
    val primary = customXrayConfigPrimaryProxyOutbound(configJson)
    if (primary != null) {
        val protocol = primary.stringValue("protocol").orEmpty().lowercase()
        val settings = primary["settings"]?.toString().orEmpty()
        val streamSettings = primary["streamSettings"]?.toString().orEmpty()
        val endpoint = customXrayConfigProxyOutboundEndpoint(configJson)
        val hostPort = if (endpoint != null) "${endpoint.host.lowercase()}:${endpoint.port}" else ""
        return "custom|$protocol|$hostPort|$settings|$streamSettings"
    }
    val endpoint = customXrayConfigProxyOutboundEndpoint(configJson)
    val hostPort = if (endpoint != null) "${endpoint.host.lowercase()}:${endpoint.port}" else ""
    return "custom|$hostPort|${configJson.trim().hashCode()}"
}

fun parseCustomXrayConfigJsonObject(value: String): JsonObject {
    val text = value.trim()
    if (text.isBlank()) {
        throw IllegalArgumentException("custom JSON is required")
    }
    val element = runCatching {
        CustomXrayConfigJson.parseToJsonElement(text)
    }.getOrNull()
    return element as? JsonObject
        ?: throw IllegalArgumentException("custom JSON must be a JSON object")
}

private fun MutableList<ProxyServerValidationIssue>.validateCustomXrayConfigJson(value: String) {
    val text = value.trim()
    if (!validateRequired(text, "custom JSON")) return
    val element = runCatching {
        CustomXrayConfigJson.parseToJsonElement(text)
    }.getOrNull()
    val config = element as? JsonObject
    if (config == null) {
        addIssue(ProxyServerValidationError.JsonObjectRequired, "custom JSON")
        return
    }
    val outbounds = config["outbounds"] as? JsonArray
    if (outbounds.isNullOrEmpty()) {
        addIssue(ProxyServerValidationError.RequiredField, "custom JSON outbounds")
    }
}

fun formatCustomXrayConfigJson(value: String): String {
    val element = CustomXrayConfigJson.parseToJsonElement(value.trim())
    return CustomXrayConfigPrettyJson.encodeToString(element)
}

fun formatCustomXrayConfigJson(element: JsonElement): String {
    return CustomXrayConfigPrettyJson.encodeToString(element)
}

data class CustomProxyOutboundEndpoint(
    val host: String,
    val port: Int,
)

fun customXrayConfigProxyOutboundEndpoint(value: String): CustomProxyOutboundEndpoint? {
    val outbounds = value.customXrayConfigOutboundsOrNull()
    return outbounds?.customProxyOutboundEndpoint()
}

/**
 * Returns the primary proxy outbound from a raw Xray configuration when it
 * can be embedded in a generated SKIPI plan (for routing or a balancer).
 */
fun customXrayConfigPrimaryProxyOutbound(value: String): JsonObject? {
    val outbounds = value.customXrayConfigOutboundsOrNull() ?: return null
    val objects = outbounds.mapNotNull { element -> element as? JsonObject }
    return objects.firstOrNull { outbound -> outbound.stringValue("tag") == CustomProxyOutboundTag }
        ?: objects.firstOrNull { outbound -> outbound.stringValue("tag") !in CustomFixedOutboundTags }
}

fun Custom.canBeUsedInGeneratedProxyPlan(): Boolean {
    return customXrayConfigPrimaryProxyOutbound(configJson) != null
}

fun customXrayConfigProxyServerHosts(value: String): List<String> {
    val outbounds = value.customXrayConfigOutboundsOrNull()
    return outbounds?.customProxyServerHosts().orEmpty()
}

private fun String.customXrayConfigOutboundsOrNull(): JsonArray? {
    return runCatching {
        parseCustomXrayConfigJsonObject(this)["outbounds"] as? JsonArray
    }.getOrNull()
}

fun customXrayConfigProtocolDisplayName(value: String): String {
    val primary = customXrayConfigPrimaryProxyOutbound(value)
    val rawProtocol = primary?.stringValue("protocol")?.trim()?.lowercase()
    val typeName = when (rawProtocol) {
        "vless" -> "VLESS"
        "vmess" -> "VMess"
        "trojan" -> "Trojan"
        "hysteria2", "hy2" -> "Hysteria2"
        "shadowsocks", "ss" -> "Shadowsocks"
        "wireguard" -> "WireGuard"
        "socks" -> "SOCKS"
        "http" -> "HTTP"
        null, "" -> null
        else -> rawProtocol.uppercase()
    }
    return if (typeName != null) {
        "JSON ($typeName)"
    } else {
        "JSON"
    }
}

fun customXrayConfigTransportDisplay(value: String): String? {
    val primary = customXrayConfigPrimaryProxyOutbound(value) ?: return null
    val protocol = primary.stringValue("protocol")?.trim()?.lowercase()
    val streamSettings = primary.objectValue("streamSettings")
    val network = streamSettings?.stringValue("network")?.trim()?.lowercase()
    val security = streamSettings?.stringValue("security")?.trim()?.lowercase()

    val transportLabel = when (network) {
        "ws", "websocket" -> "WS"
        "grpc" -> "gRPC"
        "httpupgrade" -> "HTTPUpgrade"
        "xhttp", "splithttp" -> "xHTTP"
        "kcp", "mkcp" -> "mKCP"
        "quic" -> "QUIC"
        "domainsocket" -> "DomainSocket"
        "http", "h2" -> "HTTP/2"
        "tcp", "raw" -> {
            val tcpHeaderType = streamSettings.objectValue("tcpSettings")
                ?.objectValue("header")
                ?.stringValue("type")
                ?.trim()
                ?.lowercase()
            if (tcpHeaderType == "http") "HTTP-OBFS" else "TCP"
        }
        null, "" -> when (protocol) {
            "wireguard" -> "UDP"
            "hysteria2", "hy2", "tuic" -> "QUIC"
            "shadowsocks", "ss", "socks", "http", "vless", "vmess", "trojan" -> "TCP"
            else -> null
        }
        else -> network.uppercase()
    } ?: return null

    return when {
        security.equals("reality", ignoreCase = true) -> {
            if (transportLabel == "TCP") "Reality" else "$transportLabel • Reality"
        }
        security.equals("tls", ignoreCase = true) -> {
            if (transportLabel == "TCP") "TLS" else "$transportLabel • TLS"
        }
        else -> transportLabel
    }
}

private fun customXrayConfigSummary(value: String): String {
    val outbounds = runCatching {
        parseCustomXrayConfigJsonObject(value)["outbounds"] as? JsonArray
    }.getOrNull()
    outbounds?.customProxyOutboundEndpoint()?.let { return it.toDisplayAddress() }
    return when (val count = outbounds?.size ?: 0) {
        1 -> "1 outbound"
        else -> "$count outbounds"
    }
}

private fun JsonArray.customProxyOutboundEndpoint(): CustomProxyOutboundEndpoint? {
    val outbounds = mapNotNull { element -> element as? JsonObject }
    return outbounds.firstEndpoint { outbound -> outbound.stringValue("tag") == CustomProxyOutboundTag }
        ?: outbounds.firstEndpoint { outbound -> outbound.stringValue("tag") !in CustomFixedOutboundTags }
        ?: outbounds.firstOrNull()?.toProxyOutboundEndpoint()
}

private fun JsonArray.customProxyServerHosts(): List<String> {
    return mapNotNull { element -> element as? JsonObject }
        .filter { outbound -> outbound.stringValue("tag") !in CustomInfrastructureOutboundTags }
        .flatMap { outbound -> outbound.proxyServerHosts() }
        .map { host -> host.normalizedHostAddress() }
        .toTrimmedNonEmptyDistinctList()
}

private fun List<JsonObject>.firstEndpoint(predicate: (JsonObject) -> Boolean): CustomProxyOutboundEndpoint? {
    for (outbound in this) {
        if (predicate(outbound)) {
            outbound.toProxyOutboundEndpoint()?.let { return it }
        }
    }
    return null
}

private fun JsonObject.toProxyOutboundEndpoint(): CustomProxyOutboundEndpoint? {
    val settings = objectValue("settings") ?: return null
    return settings.addressPortEndpoint()
        ?: settings.arrayValue("vnext")?.firstEndpoint()
        ?: settings.arrayValue("servers")?.firstEndpoint()
        ?: settings.arrayValue("peers")?.firstObject()?.stringValue("endpoint")?.toProxyOutboundEndpoint()
}

private fun JsonObject.proxyServerHosts(): List<String> {
    val settings = objectValue("settings") ?: return emptyList()
    return buildList {
        settings.stringValue("address")?.let(::add)
        settings.arrayValue("vnext")?.hostAddresses()?.let(::addAll)
        settings.arrayValue("servers")?.hostAddresses()?.let(::addAll)
        settings.arrayValue("peers")?.endpointHosts()?.let(::addAll)
    }
}

private fun JsonArray.hostAddresses(): List<String> {
    return mapNotNull { element ->
        (element as? JsonObject)?.stringValue("address")
    }
}

private fun JsonArray.endpointHosts(): List<String> {
    return mapNotNull { element ->
        (element as? JsonObject)?.stringValue("endpoint")?.endpointHostOrNull()
    }
}

private fun JsonArray.firstEndpoint(): CustomProxyOutboundEndpoint? {
    for (element in this) {
        val endpoint = (element as? JsonObject)?.addressPortEndpoint()
        if (endpoint != null) return endpoint
    }
    return null
}

private fun JsonObject.addressPortEndpoint(): CustomProxyOutboundEndpoint? {
    val address = stringValue("address")
        ?.normalizedHostAddress()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return endpoint(address, stringValue("port") ?: return null)
}

private fun String.toProxyOutboundEndpoint(): CustomProxyOutboundEndpoint? {
    val value = trim()
    if (value.isEmpty()) return null
    if (value.startsWith("[")) {
        val hostEnd = value.indexOf(']')
        if (hostEnd <= 0 || value.getOrNull(hostEnd + 1) != ':') return null
        return endpoint(value.substring(1, hostEnd), value.substring(hostEnd + 2))
    }

    val portSeparator = value.lastIndexOf(':')
    if (portSeparator <= 0 || portSeparator == value.lastIndex) return null
    val host = value.substring(0, portSeparator)
    if (host.contains(':')) return null
    return endpoint(host, value.substring(portSeparator + 1))
}

private fun String.endpointHostOrNull(): String? {
    val value = trim().takeIf(String::isNotEmpty) ?: return null
    if (value.startsWith("[")) {
        return value.substringAfter('[').substringBefore(']').takeIf(String::isNotBlank)
    }
    return if (value.count { char -> char == ':' } == 1) {
        value.substringBeforeLast(':').takeIf(String::isNotBlank)
    } else {
        value
    }
}

private fun endpoint(host: String, port: String): CustomProxyOutboundEndpoint? {
    val normalizedHost = host.normalizedHostAddress().takeIf(String::isNotEmpty) ?: return null
    val parsedPort = port.toPortOrNull() ?: return null
    return CustomProxyOutboundEndpoint(normalizedHost, parsedPort)
}

private fun JsonObject.stringValue(name: String): String? {
    return (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}

private fun JsonObject.objectValue(name: String): JsonObject? {
    return this[name] as? JsonObject
}

private fun JsonObject.arrayValue(name: String): JsonArray? {
    return this[name] as? JsonArray
}

private fun JsonArray.firstObject(): JsonObject? {
    return firstOrNull() as? JsonObject
}

private fun CustomProxyOutboundEndpoint.toDisplayAddress(): String {
    return "${host.toHostPortAddress()}:$port"
}

private fun String.normalizedHostAddress(): String {
    return trim().removeSurrounding("[", "]")
}

private fun String.toHostPortAddress(): String {
    return if (contains(':') && !startsWith("[")) "[$this]" else this
}

private val CustomXrayConfigJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val CustomXrayConfigPrettyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

private const val CustomProxyOutboundTag = "proxy"
private val CustomFixedOutboundTags = setOf(
    CustomProxyOutboundTag,
    "direct",
    "block",
    "dns-out",
    "fragment",
)
private val CustomInfrastructureOutboundTags = CustomFixedOutboundTags - CustomProxyOutboundTag
