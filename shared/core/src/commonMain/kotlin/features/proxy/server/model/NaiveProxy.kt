// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.proxyUrlRemarks

/**
 * NaiveProxy outbound of the bundled Xray core: HTTPS CONNECT over HTTP/2, or HTTP/3 when [quic] is set.
 *
 * [padding] only travels with the link; the core negotiates padding itself. [headers] is a JSON object of strings,
 * [concurrency] the insecure-concurrency count, blank when unset.
 */
@Serializable
data class NaiveProxy(
    var remarks: String = "",
    var server: String = "",
    var port: String = "443",
    var user: String = "",
    var pass: String = "",
    var quic: Boolean = false,
    var padding: Boolean = false,
    var sni: String = "",
    var headers: String = "",
    var concurrency: String = "",
) : UrlProxyServer<NaiveProxy> {
    override fun getInfo(): ProxyServerInfo {
        return ProxyServerInfo(remarks, "$server:$port", "Naive")
    }

    override fun toXrayOutbound(tag: String): OutboundObject {
        return OutboundObject(
            tag = tag,
            protocol = "naive",
            settings = buildJsonObject {
                put("address", server)
                put("port", port.toXrayPort())
                putIfNotBlank("user", user)
                putIfNotBlank("pass", pass)
                if (quic) put("quic", true)
                putIfNotBlank("serverName", sni)
                headers.toXrayJsonObjectOrNull("Naive headers")?.let { put("headers", it) }
                concurrency.trim().toIntOrNull()?.let { put("insecureConcurrency", it) }
            },
        )
    }

    override fun parse(url: Url): NaiveProxy {
        remarks = url.proxyUrlRemarks()
        server = url.proxyUrlHost()
        port = (if (url.specifiedPort > 0) url.specifiedPort else 443).toString()
        user = url.user.orEmpty()
        pass = url.password.orEmpty()
        quic = url.protocol.name == ProxyServerConstants.PROTOCOL_NAIVE_QUIC
        padding = url.parameters["padding"].equals("true", ignoreCase = true)
        sni = url.parameters["sni"].orEmpty()
        concurrency = url.parameters["insecure-concurrency"].orEmpty()
        headers = url.parameters["extra-headers"].orEmpty().toHeadersJson()
        return this
    }

    override fun getUrl(): String {
        return URLBuilder().apply {
            protocol = URLProtocol.createOrDefault(
                if (this@NaiveProxy.quic) ProxyServerConstants.PROTOCOL_NAIVE_QUIC else ProxyServerConstants.PROTOCOL_NAIVE_HTTPS,
            )
            setProxyUrlHost(this@NaiveProxy.server)
            this@NaiveProxy.port.toIntOrNull()?.let { port = it }
            if (this@NaiveProxy.user.isNotBlank()) user = this@NaiveProxy.user
            if (this@NaiveProxy.pass.isNotBlank()) password = this@NaiveProxy.pass
            if (this@NaiveProxy.padding) parameters.append("padding", "true")
            appendIfNotBlank("sni", this@NaiveProxy.sni)
            appendIfNotBlank("insecure-concurrency", this@NaiveProxy.concurrency)
            appendIfNotBlank("extra-headers", this@NaiveProxy.headers.toExtraHeaders())
            fragment = this@NaiveProxy.remarks
        }.buildString()
    }

    override fun update(other: ProxyServer<*>) {
        if (other !is NaiveProxy) {
            proxyServerTypeMismatch()
        }
        remarks = other.remarks
        server = other.server
        port = other.port
        user = other.user
        pass = other.pass
        quic = other.quic
        padding = other.padding
        sni = other.sni
        headers = other.headers
        concurrency = other.concurrency
    }

    override fun validateBasic(): List<ProxyServerValidationIssue> = buildList {
        validateCommonServerFields(remarks, server, port)
    }

    override fun validateFull(): List<ProxyServerValidationIssue> = buildList {
        addAll(validateBasic())
        validateOptionalUserPassword(user, pass)
        if (user.contains(':')) {
            addIssue(ProxyServerValidationError.UnsupportedValue, "user")
        }
        validateOptionalJsonObject(headers, "Naive headers")
        if (concurrency.isNotBlank() && concurrency.trim().toIntOrNull()?.let { it in 1..64 } != true) {
            addIssue(ProxyServerValidationError.UnsupportedValue, concurrency)
        }
    }

    override fun connectionFingerprint(): String {
        return "naive|${server.trim().lowercase()}:${port.trim()}|$user|$pass|quic=$quic|sni=$sni|" +
            "headers=$headers|concurrency=$concurrency"
    }
}

private fun URLBuilder.appendIfNotBlank(name: String, value: String) {
    if (value.isNotBlank()) parameters.append(name, value)
}

private fun String.toHeadersJson(): String {
    val pairs = split("\r\n", "\n").mapNotNull { line ->
        if (!line.contains(':')) return@mapNotNull null
        val name = line.substringBefore(':').trim()
        if (name.isEmpty()) null else name to line.substringAfter(':').trim()
    }
    if (pairs.isEmpty()) return ""
    return JsonObject(pairs.toMap().mapValues { JsonPrimitive(it.value) }).toString()
}

private fun String.toExtraHeaders(): String {
    val parsed = runCatching { Json.parseToJsonElement(trim()) }.getOrNull() as? JsonObject ?: return ""
    return parsed.entries.joinToString("\r\n") { (name, value) -> "$name: ${(value as? JsonPrimitive)?.content.orEmpty()}" }
}
