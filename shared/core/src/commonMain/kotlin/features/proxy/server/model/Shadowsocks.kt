// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.decodeFlexibleBase64OrNull
import utils.decodeFlexibleBase64ToStringOrRaw
import utils.encodeBase64
import utils.proxyUrlRemarks
import utils.userInfoOrNull

@Serializable
data class Shadowsocks(
    var remarks: String = "",
    var server: String = "",
    var port: String = "443",
    var method: String = "aes-256-gcm",
    var password: String = "",
    var parms: V2RayParameters = V2RayParameters(),
) : UrlProxyServer<Shadowsocks> {
    override fun getInfo(): ProxyServerInfo {
        return ProxyServerInfo(this.remarks, "${this.server}:${this.port}", "Shadowsocks")
    }

    override fun toXrayOutbound(tag: String): OutboundObject {
        return OutboundObject(
            tag = tag,
            protocol = "shadowsocks",
            settings = buildJsonObject {
                put("address", server)
                put("port", port.toXrayPort())
                put("method", method)
                put("password", method.toXrayShadowsocksPassword(password))
            },
            streamSettings = parms.toXrayStreamSettings(),
        )
    }

    override fun parse(url: Url): Shadowsocks {
        this.remarks = url.proxyUrlRemarks()
        this.server = url.proxyUrlHost()
        this.port = url.port.toString()
        if (url.port == 0) {
            val full =
                url.host.decodeProxyUrlBase64().decodeToString()
            parseLegacy(full, this.remarks)
        } else {
            val info = url.userInfoOrNull()?.decodeFlexibleBase64ToStringOrRaw().orEmpty()
            val pos = info.indexOfFirst { it == ':' }
            if (pos > -1) {
                this.method = info.substring(0, pos)
                this.password = info.substring(pos + 1)
            }
            applyXrayRawHttpObfs(url.parameters["plugin"])
        }
        return this
    }

    internal fun parseLegacy(value: String, remarks: String): Shadowsocks {
        this.remarks = remarks
        val infoAndServer = value.trimEnd('/').split('@', limit = 2)
        if (infoAndServer.size == 2) {
            val methodAndPassword = infoAndServer[0].split(':', limit = 2)
            if (methodAndPassword.size == 2) {
                this.method = methodAndPassword[0].lowercase()
                this.password = methodAndPassword[1]
            }
            parseLegacyEndpoint(infoAndServer[1])?.let { (server, port) ->
                this.server = server
                this.port = port
            }
        }
        return this
    }

    // Xray does not run SIP002 plugins; only obfs=http can be represented as RAW HTTP header.
    private fun applyXrayRawHttpObfs(plugin: String?) {
        if (plugin.isNullOrBlank()) return
        val queryPairs = plugin.split(";")
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                pair.substring(0, index).lowercase() to pair.substring(index + 1)
            }
            .toMap()
        if (!queryPairs["obfs"].equals("http", ignoreCase = true)) return
        this.parms = this.parms.copy(
            type = "raw",
            headerType = "http",
            host = queryPairs["obfs-host"],
            path = queryPairs["path"],
        )
    }

    override fun getUrl(): String {
        return URLBuilder().apply {
            protocol = URLProtocol.createOrDefault(ProxyServerConstants.PROTOCOL_SS)
            setProxyUrlHost(this@Shadowsocks.server)
            this@Shadowsocks.port.toIntOrNull()?.let { port = it }
            user = "${this@Shadowsocks.method}:${this@Shadowsocks.password}".encodeToByteArray().encodeProxyUrlBase64()
            this@Shadowsocks.parms.toSip002HttpObfsPluginOrNull()?.let { plugin ->
                parameters.append("plugin", plugin)
            }
            fragment = this@Shadowsocks.remarks
        }.build().toString()
    }

    override fun update(other: ProxyServer<*>) {
        if (other !is Shadowsocks) {
            proxyServerTypeMismatch()
        }
        this.apply {
            remarks = other.remarks
            server = other.server
            port = other.port
            method = other.method
            password = other.password
            parms = other.parms.copy()
        }
    }

    override fun validateBasic(): List<ProxyServerValidationIssue> = buildList {
        validateCommonServerFields(remarks, server, port)
        validateRequired(method, "encryption method")
        validateRequired(password, "password")
    }

    override fun validateFull(): List<ProxyServerValidationIssue> = buildList {
        addAll(validateBasic())
        validateAllowed(
            method,
            "encryption method",
            setOf(
                "aes-256-gcm",
                "aes-128-gcm",
                "chacha20-poly1305",
                "chacha20-ietf-poly1305",
                "xchacha20-poly1305",
                "xchacha20-ietf-poly1305",
                "2022-blake3-aes-128-gcm",
                "2022-blake3-aes-256-gcm",
                "2022-blake3-chacha20-poly1305",
            ),
        )
        validateShadowsocks2022Password(method, password)
        validateV2RayParameters(parms)
    }

    override fun connectionFingerprint(): String {
        return "ss|${server.trim().lowercase()}:${port.trim()}|$method|$password|${parms.fingerprint()}"
    }
}

private fun String.toXrayShadowsocksPassword(password: String): String {
    if (!isShadowsocks2022Method()) {
        return password
    }
    return password
        .split(':')
        .joinToString(":") { key -> normalizeShadowsocks2022Key(key, shadowsocks2022KeyLengths()) }
}

private fun String.isShadowsocks2022Method(): Boolean {
    return this in Shadowsocks2022KeyLengths
}

private fun String.shadowsocks2022KeyLengths(): Set<Int> {
    return Shadowsocks2022KeyLengths[this].orEmpty()
}

private fun normalizeShadowsocks2022Key(key: String, validLengths: Set<Int>): String {
    val trimmed = key.trim()
    val decodedKey = decodeShadowsocks2022Key(trimmed)
        ?: error("Invalid Shadowsocks 2022 key base64")
    if (decodedKey.size !in validLengths) {
        error("Invalid Shadowsocks 2022 key length")
    }
    return decodedKey.encodeBase64()
}

private fun MutableList<ProxyServerValidationIssue>.validateShadowsocks2022Password(method: String, password: String) {
    if (!method.isShadowsocks2022Method() || password.isBlank()) return
    password.split(':').forEach { key ->
        val decodedKey = decodeShadowsocks2022Key(key.trim())
        if (decodedKey == null) {
            addIssue(ProxyServerValidationError.Shadowsocks2022KeyBase64Invalid)
            return@forEach
        }
        val validLengths = method.shadowsocks2022KeyLengths()
        if (decodedKey.size !in validLengths) {
            addIssue(
                ProxyServerValidationError.Shadowsocks2022KeyLengthInvalid,
                validLengths.joinToString(" or "),
            )
        }
    }
}

private fun decodeShadowsocks2022Key(key: String): ByteArray? {
    if (key.isBlank()) {
        return null
    }
    return key.decodeFlexibleBase64OrNull()
}

private fun parseLegacyEndpoint(value: String): Pair<String, String>? {
    val endpoint = value.trimEnd('/')
    return if (endpoint.startsWith('[')) {
        val end = endpoint.indexOf("]:")
        if (end <= 0) return null
        endpoint.substring(1, end) to endpoint.substring(end + 2)
    } else {
        val separator = endpoint.lastIndexOf(':')
        if (separator <= 0 || separator == endpoint.lastIndex) return null
        endpoint.substring(0, separator) to endpoint.substring(separator + 1)
    }
}

private fun V2RayParameters.toSip002HttpObfsPluginOrNull(): String? {
    val transport = type.ifBlank { "raw" }
    if (transport != "raw" && transport != "tcp") return null
    if (!headerType.equals("http", ignoreCase = true)) return null
    return listOfNotNull(
        "obfs-local",
        "obfs=http",
        host?.takeIf(String::isNotBlank)?.let { "obfs-host=$it" },
        path?.takeIf(String::isNotBlank)?.let { "path=$it" },
    ).joinToString(";")
}

private val Shadowsocks2022KeyLengths = mapOf(
    "2022-blake3-aes-128-gcm" to setOf(16, 32),
    "2022-blake3-aes-256-gcm" to setOf(32),
    "2022-blake3-chacha20-poly1305" to setOf(32),
)

