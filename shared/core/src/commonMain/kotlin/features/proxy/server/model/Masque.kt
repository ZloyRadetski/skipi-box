// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import engine.network.isIpAddress
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import utils.proxyUrlRemarks
import utils.toCsvValues

/**
 * MASQUE (CONNECT-IP over HTTP/3) outbound of the bundled Xray core.
 *
 * Authentication is either [user]/[pass] or a Cloudflare WARP identity ([warpPrivateKey], [warpPublicKey],
 * [warpAddress]); the core rejects both together. WARP keys are ECDSA P-256 DER blobs, not WireGuard keys.
 */
@Serializable
data class Masque(
    var remarks: String = "",
    var server: String = "",
    var port: String = "443",
    var user: String = "",
    var pass: String = "",
    var sni: String = "",
    var pinSHA256: String = "",
    var host: String = "",
    var path: String = "",
    var remoteDns: String = "",
    var warpPrivateKey: String = "",
    var warpPublicKey: String = "",
    var warpAddress: String = "",
) : UrlProxyServer<Masque> {
    private val usesWarp: Boolean
        get() = warpPrivateKey.isNotBlank() || warpPublicKey.isNotBlank() || warpAddress.isNotBlank()

    override fun getInfo(): ProxyServerInfo {
        return ProxyServerInfo(remarks, "$server:$port", "MASQUE")
    }

    override fun toXrayOutbound(tag: String): OutboundObject {
        return OutboundObject(
            tag = tag,
            protocol = "masque",
            settings = buildJsonObject {
                put("address", server)
                put("port", port.toXrayPort())
                val dns = remoteDns.toCsvValues()
                if (dns.isNotEmpty()) {
                    put("remoteDNS", buildJsonArray { dns.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                }
            },
            streamSettings = buildJsonObject {
                put("network", "masque")
                put("security", "tls")
                putJsonObject("tlsSettings") {
                    val effectiveSni = sni.ifBlank { if (server.contains(":")) "" else server }
                    putIfNotBlank("serverName", effectiveSni)
                    putIfNotBlank("pinnedPeerCertSha256", pinSHA256)
                }
                putJsonObject("masqueSettings") {
                    putIfNotBlank("host", host)
                    putIfNotBlank("path", path)
                    if (usesWarp) {
                        putJsonObject("warp") {
                            put("privateKey", warpPrivateKey.trim())
                            put("publicKey", warpPublicKey.trim())
                            put(
                                "address",
                                buildJsonArray {
                                    warpAddress.toCsvValues().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                                },
                            )
                        }
                    } else {
                        putIfNotBlank("user", user)
                        putIfNotBlank("pass", pass)
                    }
                }
            },
        )
    }

    override fun parse(url: Url): Masque {
        remarks = url.proxyUrlRemarks()
        server = url.proxyUrlHost()
        port = (if (url.specifiedPort > 0) url.specifiedPort else 443).toString()
        user = url.user.orEmpty()
        pass = url.password.orEmpty()
        sni = url.parameters["sni"].orEmpty()
        pinSHA256 = url.parameters["pinSHA256"].orEmpty()
        host = url.parameters["host"].orEmpty()
        path = url.parameters["path"].orEmpty()
        remoteDns = url.parameters["dns"].orEmpty()
        warpPrivateKey = url.parameters["warp-private-key"].orEmpty()
        warpPublicKey = url.parameters["warp-public-key"].orEmpty()
        warpAddress = url.parameters["warp-address"].orEmpty()
        return this
    }

    override fun getUrl(): String {
        return URLBuilder().apply {
            protocol = URLProtocol.createOrDefault(ProxyServerConstants.PROTOCOL_MASQUE)
            setProxyUrlHost(this@Masque.server)
            this@Masque.port.toIntOrNull()?.let { port = it }
            if (this@Masque.user.isNotBlank()) user = this@Masque.user
            if (this@Masque.pass.isNotBlank()) password = this@Masque.pass
            appendIfNotBlank("sni", this@Masque.sni)
            appendIfNotBlank("pinSHA256", this@Masque.pinSHA256)
            appendIfNotBlank("host", this@Masque.host)
            appendIfNotBlank("path", this@Masque.path)
            appendIfNotBlank("dns", this@Masque.remoteDns.toCsvValues().joinToString(","))
            appendIfNotBlank("warp-private-key", this@Masque.warpPrivateKey)
            appendIfNotBlank("warp-public-key", this@Masque.warpPublicKey)
            appendIfNotBlank("warp-address", this@Masque.warpAddress.toCsvValues().joinToString(","))
            fragment = this@Masque.remarks
        }.buildString()
    }

    override fun update(other: ProxyServer<*>) {
        if (other !is Masque) {
            proxyServerTypeMismatch()
        }
        remarks = other.remarks
        server = other.server
        port = other.port
        user = other.user
        pass = other.pass
        sni = other.sni
        pinSHA256 = other.pinSHA256
        host = other.host
        path = other.path
        remoteDns = other.remoteDns
        warpPrivateKey = other.warpPrivateKey
        warpPublicKey = other.warpPublicKey
        warpAddress = other.warpAddress
    }

    override fun validateBasic(): List<ProxyServerValidationIssue> = buildList {
        validateCommonServerFields(remarks, server, port)
    }

    override fun validateFull(): List<ProxyServerValidationIssue> = buildList {
        addAll(validateBasic())
        if (usesWarp) {
            if (user.isNotBlank() || pass.isNotBlank()) {
                addIssue(ProxyServerValidationError.UnsupportedValue, "user/pass with WARP")
            }
            validateWarpKey(warpPrivateKey, "WARP private key")
            validateWarpKey(warpPublicKey, "WARP public key")
            validateWarpAddresses(warpAddress)
        } else {
            validateOptionalUserPassword(user, pass)
            if (user.contains(':')) {
                addIssue(ProxyServerValidationError.UnsupportedValue, "user")
            }
        }
        validateMasquePath(path)
        if (host.any { it.isWhitespace() || it == '/' }) {
            addIssue(ProxyServerValidationError.UnsupportedValue, host)
        }
        remoteDns.toCsvValues().forEach { entry ->
            if (!isIpAddress(entry)) addIssue(ProxyServerValidationError.UnsupportedValue, entry)
        }
        validateOptionalSha256(pinSHA256, "certificate fingerprint")
    }

    override fun connectionFingerprint(): String {
        return "masque|${server.trim().lowercase()}:${port.trim()}|$user|$pass|sni=$sni|pin=$pinSHA256|" +
            "host=$host|path=$path|dns=$remoteDns|warp=$warpPrivateKey|$warpPublicKey|$warpAddress"
    }
}

private val MasquePathVariables = listOf(
    "{?target,ipproto}", "{?ipproto,target}", "{&target,ipproto}", "{&ipproto,target}", "{target}", "{ipproto}",
)

private fun URLBuilder.appendIfNotBlank(name: String, value: String) {
    if (value.isNotBlank()) parameters.append(name, value)
}

private fun MutableList<ProxyServerValidationIssue>.validateMasquePath(path: String) {
    if (path.isBlank()) return
    if (!path.startsWith("/")) {
        addIssue(ProxyServerValidationError.PathMustStartWithSlash)
        return
    }
    val stripped = MasquePathVariables.fold(path) { acc, variable -> acc.replace(variable, "") }
    if (stripped.any { it == '{' || it == '}' }) {
        addIssue(ProxyServerValidationError.UnsupportedValue, path)
    }
}

/** The core takes a PEM block or `base64.StdEncoding`: padded, standard alphabet, no URL-safe variant. */
private fun MutableList<ProxyServerValidationIssue>.validateWarpKey(value: String, fieldName: String) {
    if (!validateRequired(value, fieldName)) return
    val key = value.trim()
    val isPem = key.startsWith("-----BEGIN ") && key.contains("-----END ")
    if (!isPem && !StandardBase64Regex.matches(key.filterNot { it == '\r' || it == '\n' })) {
        addIssue(ProxyServerValidationError.UnsupportedValue, fieldName)
    }
}

private val StandardBase64Regex = Regex("^(?:[A-Za-z0-9+/]{4})+$|^(?:[A-Za-z0-9+/]{4})*[A-Za-z0-9+/]{2,3}={1,2}$")

private fun MutableList<ProxyServerValidationIssue>.validateWarpAddresses(value: String) {
    val entries = value.toCsvValues()
    if (entries.isEmpty()) {
        addIssue(ProxyServerValidationError.RequiredField, "WARP address")
        return
    }
    var ipv4 = 0
    var ipv6 = 0
    entries.forEach { entry ->
        val address = entry.substringBefore('/')
        val prefix = entry.substringAfter('/', missingDelimiterValue = "")
        val isV6 = address.contains(':')
        val maxPrefix = if (isV6) 128 else 32
        val prefixOk = !entry.contains('/') || prefix.toIntOrNull()?.let { it in 0..maxPrefix } == true
        if (!isIpAddress(address) || !prefixOk) {
            addIssue(ProxyServerValidationError.UnsupportedValue, entry)
            return@forEach
        }
        if (isV6) ipv6++ else ipv4++
    }
    if (ipv4 > 1 || ipv6 > 1) {
        addIssue(ProxyServerValidationError.UnsupportedValue, value)
    }
}
