// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import utils.toCsvValues
import utils.proxyUrlRemarks
import utils.userInfoOrNull

/**
 * Amnezia WireGuard (AWG) — обфусцированный протокол WireGuard с рандомизацией заголовков
 * и добавлением мусорных пакетов для обхода DPI-блокировок.
 *
 * Параметры обфускации:
 * - jc: количество мусорных пакетов (Junk Count), 0..128
 * - jmin/jmax: диапазон размеров мусорных пакетов в байтах (0..1280, jmin <= jmax)
 * - s1/s2: размер мусорного префикса для пакетов Init/Response (0..1280)
 * - h1..h4: кастомные 32-битные заголовки рукопожатия Init/Response/Cookie/Transport
 *
 * Если все параметры обфускации равны нулю или не заданы, ядро работает как стандартный WireGuard.
 */
@Serializable
data class AmneziaWg(
    var remarks: String = "",
    var server: String = "",
    var port: String = "51820",
    var secretKey: String = "",
    var publicKey: String = "",
    var preSharedKey: String = "",
    var reserved: String = "0,0,0",
    var address: String = "10.8.0.2/32",
    var mtu: String = "1420",
    var finalMask: String = "",
    // Параметры обфускации AmneziaWG
    var jc: String = "4",
    var jmin: String = "40",
    var jmax: String = "70",
    var s1: String = "15",
    var s2: String = "30",
    var s3: String = "",
    var s4: String = "",
    var h1: String = "",
    var h2: String = "",
    var h3: String = "",
    var h4: String = "",
    var i1: String = "",
    var i2: String = "",
    var i3: String = "",
    var i4: String = "",
    var i5: String = "",
    // AmneziaWG 3.0. Every value is empty (not set), `N` or an inclusive range `N-M`, 0..65535.
    var headerProtectionKey: String = "",
    var contentPaddingAddition: String = "",
    var rekeyAfterTime: String = "",
    var rekeyTimeout: String = "",
    var rejectAfterTime: String = "",
    var keepaliveTimeout: String = "",
    var maxHandshakeAttempts: String = "",
    var persistentKeepalive: String = "",
) : UrlProxyServer<AmneziaWg> {

    override fun getInfo(): ProxyServerInfo {
        return ProxyServerInfo(remarks, "$server:$port", "Amnezia WG")
    }

    override fun toXrayOutbound(tag: String): OutboundObject {
        return OutboundObject(
            tag = tag,
            protocol = ProxyServerConstants.PROTOCOL_WIREGUARD,
            settings = buildJsonObject {
                put("secretKey", secretKey)
                val addresses = address.toCsvValues()
                if (addresses.isNotEmpty()) {
                    putJsonArray("address") {
                        addresses.forEach { add(it) }
                    }
                }
                putJsonArray("peers") {
                    add(
                        buildJsonObject {
                            put("endpoint", toWireguardEndpoint())
                            put("publicKey", publicKey)
                            putIfNotBlank("preSharedKey", preSharedKey)
                            putAwgRange("keepAlive", persistentKeepalive)
                        },
                    )
                }
                mtu.toIntOrNull()?.let { put("mtu", it) }
                val reservedBytes = reserved.toCsvValues()
                    .mapNotNull(String::toIntOrNull)
                if (reservedBytes.isNotEmpty()) {
                    putJsonArray("reserved") {
                        reservedBytes.forEach { add(it) }
                    }
                }
                // Параметры обфускации — добавляем только ненулевые значения
                jc.toIntOrNull()?.takeIf { it > 0 }?.let { put("jc", it) }
                jmin.toIntOrNull()?.takeIf { it > 0 }?.let { put("jmin", it) }
                jmax.toIntOrNull()?.takeIf { it > 0 }?.let { put("jmax", it) }
                s1.toIntOrNull()?.takeIf { it > 0 }?.let { put("s1", it) }
                s2.toIntOrNull()?.takeIf { it > 0 }?.let { put("s2", it) }
                s3.toIntOrNull()?.takeIf { it > 0 }?.let { put("s3", it) }
                s4.toIntOrNull()?.takeIf { it > 0 }?.let { put("s4", it) }
                // The current native AWG runtime accepts either a single
                // uint32 header value or an inclusive uint32 range such as
                // "123-456". Preserve a plain number as JSON number for
                // Xray compatibility, and a range as its required string.
                listOf("h1" to h1, "h2" to h2, "h3" to h3, "h4" to h4).forEach { (name, value) ->
                    val header = value.trim()
                    if (header.isNotEmpty() && header != "0") {
                        val numericHeader = header.toLongOrNull()
                        if (numericHeader != null) {
                            put(name, numericHeader)
                        } else {
                            put(name, header)
                        }
                    }
                }
                listOf("i1" to i1, "i2" to i2, "i3" to i3, "i4" to i4, "i5" to i5).forEach { (name, value) ->
                    value.trim().takeIf(String::isNotEmpty)?.let { put(name, it) }
                }
                putIfNotBlank("headerProtectionKey", headerProtectionKey.trim())
                putAwgRange("contentPaddingAddition", contentPaddingAddition)
                putAwgRange("rekeyAfterTime", rekeyAfterTime)
                putAwgRange("rekeyTimeout", rekeyTimeout)
                putAwgRange("rejectAfterTime", rejectAfterTime)
                putAwgRange("keepaliveTimeout", keepaliveTimeout)
                putAwgRange("maxHandshakeAttempts", maxHandshakeAttempts)
            },
            streamSettings = finalMask.toXrayJsonObjectOrNull("FinalMask")?.let { parsedFinalMask ->
                buildJsonObject {
                    put("finalmask", parsedFinalMask)
                }
            },
        )
    }

    override fun parse(url: Url): AmneziaWg {
        this.remarks = url.proxyUrlRemarks()
        this.server = url.proxyUrlHost()
        this.port = url.specifiedPort.takeIf { it > 0 }?.toString() ?: "51820"
        this.secretKey = url.userInfoOrNull() ?: ""
        // A Base64 key never contains a space, so a '+' that the query decoder turned into
        // one (links with an unescaped key) is restored.
        this.publicKey = url.parameters["publickey"]?.replace(' ', '+') ?: ""
        this.preSharedKey = url.parameters["presharedkey"]?.replace(' ', '+') ?: ""
        this.reserved = url.parameters["reserved"] ?: "0,0,0"
        this.address = url.parameters["address"] ?: "10.8.0.2/32"
        this.mtu = url.parameters["mtu"] ?: "1420"
        // Параметры обфускации
        this.jc = url.parameters["jc"] ?: "4"
        this.jmin = url.parameters["jmin"] ?: "40"
        this.jmax = url.parameters["jmax"] ?: "70"
        this.s1 = url.parameters["s1"] ?: "15"
        this.s2 = url.parameters["s2"] ?: "30"
        this.s3 = url.parameters["s3"] ?: ""
        this.s4 = url.parameters["s4"] ?: ""
        this.h1 = url.parameters["h1"] ?: ""
        this.h2 = url.parameters["h2"] ?: ""
        this.h3 = url.parameters["h3"] ?: ""
        this.h4 = url.parameters["h4"] ?: ""
        this.i1 = url.parameters["i1"] ?: ""
        this.i2 = url.parameters["i2"] ?: ""
        this.i3 = url.parameters["i3"] ?: ""
        this.i4 = url.parameters["i4"] ?: ""
        this.i5 = url.parameters["i5"] ?: ""
        this.headerProtectionKey = url.parameters["headerprotectionkey"]?.replace(' ', '+') ?: ""
        this.contentPaddingAddition = url.parameters["contentpaddingaddition"] ?: ""
        this.rekeyAfterTime = url.parameters["rekeyaftertime"] ?: ""
        this.rekeyTimeout = url.parameters["rekeytimeout"] ?: ""
        this.rejectAfterTime = url.parameters["rejectaftertime"] ?: ""
        this.keepaliveTimeout = url.parameters["keepalivetimeout"] ?: ""
        this.maxHandshakeAttempts = url.parameters["maxhandshakeattempts"] ?: ""
        this.persistentKeepalive = url.parameters["persistentkeepalive"] ?: ""
        return this
    }

    override fun getUrl(): String {
        return URLBuilder().apply {
            protocol = URLProtocol.createOrDefault(ProxyServerConstants.PROTOCOL_AWG)
            setProxyUrlHost(this@AmneziaWg.server)
            this@AmneziaWg.port.toIntOrNull()?.let { port = it }
            user = this@AmneziaWg.secretKey

            if (this@AmneziaWg.publicKey.isNotBlank()) {
                parameters.append("publickey", this@AmneziaWg.publicKey)
            }
            if (this@AmneziaWg.preSharedKey.isNotBlank()) {
                parameters.append("presharedkey", this@AmneziaWg.preSharedKey)
            }
            if (this@AmneziaWg.reserved.isNotBlank()) {
                parameters.append("reserved", this@AmneziaWg.reserved)
            }
            if (this@AmneziaWg.address.isNotBlank()) {
                parameters.append("address", this@AmneziaWg.address)
            }
            parameters.append("mtu", this@AmneziaWg.mtu)
            // Обфускация
            if (this@AmneziaWg.jc.isNotBlank()) parameters.append("jc", this@AmneziaWg.jc)
            if (this@AmneziaWg.jmin.isNotBlank()) parameters.append("jmin", this@AmneziaWg.jmin)
            if (this@AmneziaWg.jmax.isNotBlank()) parameters.append("jmax", this@AmneziaWg.jmax)
            if (this@AmneziaWg.s1.isNotBlank()) parameters.append("s1", this@AmneziaWg.s1)
            if (this@AmneziaWg.s2.isNotBlank()) parameters.append("s2", this@AmneziaWg.s2)
            if (this@AmneziaWg.s3.isNotBlank()) parameters.append("s3", this@AmneziaWg.s3)
            if (this@AmneziaWg.s4.isNotBlank()) parameters.append("s4", this@AmneziaWg.s4)
            if (this@AmneziaWg.h1.isNotBlank()) parameters.append("h1", this@AmneziaWg.h1)
            if (this@AmneziaWg.h2.isNotBlank()) parameters.append("h2", this@AmneziaWg.h2)
            if (this@AmneziaWg.h3.isNotBlank()) parameters.append("h3", this@AmneziaWg.h3)
            if (this@AmneziaWg.h4.isNotBlank()) parameters.append("h4", this@AmneziaWg.h4)
            if (this@AmneziaWg.i1.isNotBlank()) parameters.append("i1", this@AmneziaWg.i1)
            if (this@AmneziaWg.i2.isNotBlank()) parameters.append("i2", this@AmneziaWg.i2)
            if (this@AmneziaWg.i3.isNotBlank()) parameters.append("i3", this@AmneziaWg.i3)
            if (this@AmneziaWg.i4.isNotBlank()) parameters.append("i4", this@AmneziaWg.i4)
            if (this@AmneziaWg.i5.isNotBlank()) parameters.append("i5", this@AmneziaWg.i5)
            listOf(
                "headerprotectionkey" to headerProtectionKey,
                "contentpaddingaddition" to contentPaddingAddition,
                "rekeyaftertime" to rekeyAfterTime,
                "rekeytimeout" to rekeyTimeout,
                "rejectaftertime" to rejectAfterTime,
                "keepalivetimeout" to keepaliveTimeout,
                "maxhandshakeattempts" to maxHandshakeAttempts,
                "persistentkeepalive" to persistentKeepalive,
            ).forEach { (name, value) -> if (value.isNotBlank()) parameters.append(name, value) }

            fragment = this@AmneziaWg.remarks
        }.buildString()
    }

    override fun update(other: ProxyServer<*>) {
        if (other !is AmneziaWg) {
            proxyServerTypeMismatch()
        }
        this.apply {
            remarks = other.remarks
            server = other.server
            port = other.port
            secretKey = other.secretKey
            publicKey = other.publicKey
            preSharedKey = other.preSharedKey
            reserved = other.reserved
            address = other.address
            mtu = other.mtu
            finalMask = other.finalMask
            jc = other.jc
            jmin = other.jmin
            jmax = other.jmax
            s1 = other.s1
            s2 = other.s2
            s3 = other.s3
            s4 = other.s4
            h1 = other.h1
            h2 = other.h2
            h3 = other.h3
            h4 = other.h4
            i1 = other.i1
            i2 = other.i2
            i3 = other.i3
            i4 = other.i4
            i5 = other.i5
            headerProtectionKey = other.headerProtectionKey
            contentPaddingAddition = other.contentPaddingAddition
            rekeyAfterTime = other.rekeyAfterTime
            rekeyTimeout = other.rekeyTimeout
            rejectAfterTime = other.rejectAfterTime
            keepaliveTimeout = other.keepaliveTimeout
            maxHandshakeAttempts = other.maxHandshakeAttempts
            persistentKeepalive = other.persistentKeepalive
        }
    }

    override fun validateBasic(): List<ProxyServerValidationIssue> = buildList {
        validateCommonServerFields(remarks, server, port)
        validateRequired(secretKey, "SecretKey")
        validateRequired(publicKey, "PublicKey")
    }

    override fun validateFull(): List<ProxyServerValidationIssue> = buildList {
        addAll(validateBasic())
        validateWireguardKey(secretKey, "SecretKey", required = false)
        validateWireguardKey(publicKey, "PublicKey", required = false)
        validateWireguardKey(preSharedKey, "PreSharedKey", required = false)
        validateWireguardReserved(reserved)
        validateWireguardAddresses(address)
        validateMtu(mtu)
        validateOptionalJsonObject(finalMask, "FinalMask")
        validateAmneziaWgObfuscation(jc, jmin, jmax, s1, s2, s3, s4, h1, h2, h3, h4)
        validateAmneziaWgChains(listOf("I1" to i1, "I2" to i2, "I3" to i3, "I4" to i4, "I5" to i5))
        validateWireguardKey(headerProtectionKey, "HeaderProtectionKey", required = false)
        if (headerProtectionKey.isNotBlank()) {
            // The runner encrypts headers with a 12-byte nonce carried in S1..S4, so each must be >= 12.
            listOf("S1" to s1, "S2" to s2, "S3" to s3, "S4" to s4).forEach { (name, value) ->
                if ((value.toIntOrNull() ?: 0) < AwgHeaderProtectionMinPadding) {
                    addIssue(ProxyServerValidationError.AmneziaWgHeaderProtectionPaddingTooSmall, name, AwgHeaderProtectionMinPadding, 1280)
                }
            }
        }
        validateAmneziaWgRanges(
            listOf(
                "ContentPaddingAddition" to contentPaddingAddition,
                "RekeyAfterTime" to rekeyAfterTime,
                "RekeyTimeout" to rekeyTimeout,
                "RejectAfterTime" to rejectAfterTime,
                "KeepaliveTimeout" to keepaliveTimeout,
                "MaxHandshakeAttempts" to maxHandshakeAttempts,
                "PersistentKeepalive" to persistentKeepalive.takeUnless { it.equals("off", ignoreCase = true) }.orEmpty(),
            ),
        )
    }

    override fun connectionFingerprint(): String {
        return "amneziawg|${server.trim().lowercase()}:${port.trim()}|$publicKey|$secretKey|$preSharedKey|$address|$mtu|$reserved|$jc|$jmin|$jmax|$s1|$s2|$s3|$s4|$h1|$h2|$h3|$h4|$i1|$i2|$i3|$i4|$i5|$finalMask|$headerProtectionKey|$contentPaddingAddition|$rekeyAfterTime|$rekeyTimeout|$rejectAfterTime|$keepaliveTimeout|$maxHandshakeAttempts|$persistentKeepalive"
    }

    /**
     * Native AmneziaWG has one process-wide runner. Only profiles with the
     * same effective tunnel parameters may share that runner; a label or a
     * different local presentation must not make unrelated peers look equal.
     */
    fun matchesRuntimeConfig(other: AmneziaWg): Boolean {
        return connectionFingerprint() == other.connectionFingerprint()
    }

    private fun toWireguardEndpoint(): String {
        val host = if (server.contains(':') && !server.startsWith('[')) {
            "[$server]"
        } else {
            server
        }
        return "$host:$port"
    }
}

private const val AwgHeaderProtectionMinPadding = 12

/**
 * An AmneziaWG 3.0 `N` / `N-M` value: a JSON number for a single value and a string for a range
 * (the native runner reads both); empty, `0` and `off` mean "not set".
 */
private fun kotlinx.serialization.json.JsonObjectBuilder.putAwgRange(name: String, value: String) {
    val text = value.trim()
    if (text.isEmpty() || text == "0" || text.equals("off", ignoreCase = true)) return
    val number = text.toIntOrNull()
    if (number != null) put(name, number) else put(name, text)
}
