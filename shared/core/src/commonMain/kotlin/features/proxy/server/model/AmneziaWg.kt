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
    var h1: String = "",
    var h2: String = "",
    var h3: String = "",
    var h4: String = "",
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
                h1.toLongOrNull()?.takeIf { it > 0 }?.let { put("h1", it) }
                h2.toLongOrNull()?.takeIf { it > 0 }?.let { put("h2", it) }
                h3.toLongOrNull()?.takeIf { it > 0 }?.let { put("h3", it) }
                h4.toLongOrNull()?.takeIf { it > 0 }?.let { put("h4", it) }
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
        this.port = url.port.toString()
        this.secretKey = url.userInfoOrNull() ?: ""
        this.publicKey = url.parameters["publickey"] ?: ""
        this.preSharedKey = url.parameters["presharedkey"] ?: ""
        this.reserved = url.parameters["reserved"] ?: "0,0,0"
        this.address = url.parameters["address"] ?: "10.8.0.2/32"
        this.mtu = url.parameters["mtu"] ?: "1420"
        // Параметры обфускации
        this.jc = url.parameters["jc"] ?: "4"
        this.jmin = url.parameters["jmin"] ?: "40"
        this.jmax = url.parameters["jmax"] ?: "70"
        this.s1 = url.parameters["s1"] ?: "15"
        this.s2 = url.parameters["s2"] ?: "30"
        this.h1 = url.parameters["h1"] ?: ""
        this.h2 = url.parameters["h2"] ?: ""
        this.h3 = url.parameters["h3"] ?: ""
        this.h4 = url.parameters["h4"] ?: ""
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
            if (this@AmneziaWg.h1.isNotBlank()) parameters.append("h1", this@AmneziaWg.h1)
            if (this@AmneziaWg.h2.isNotBlank()) parameters.append("h2", this@AmneziaWg.h2)
            if (this@AmneziaWg.h3.isNotBlank()) parameters.append("h3", this@AmneziaWg.h3)
            if (this@AmneziaWg.h4.isNotBlank()) parameters.append("h4", this@AmneziaWg.h4)

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
            h1 = other.h1
            h2 = other.h2
            h3 = other.h3
            h4 = other.h4
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
        validateAmneziaWgObfuscation(jc, jmin, jmax, s1, s2, h1, h2, h3, h4)
    }

    override fun connectionFingerprint(): String {
        return "amneziawg|${server.trim().lowercase()}:${port.trim()}|$publicKey|$secretKey|$preSharedKey|$address"
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
