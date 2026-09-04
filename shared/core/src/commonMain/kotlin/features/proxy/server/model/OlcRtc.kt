// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import utils.proxyUrlRemarks

/**
 * OLCRTC (OpenLibreCommunity RTC) — протокол скрытия трафика через WebRTC-инфраструктуру
 * видеоконференций (Jitsi, Yandex Telemost, WB Stream и др.).
 *
 * Принцип работы:
 * - `skipi-core` запускает локальный SOCKS5-клиент OLCRTC на `127.0.0.1:<localSocksPort>`.
 * - Xray направляет трафик через этот локальный SOCKS5 с помощью стандартного socks outbound.
 * - Весь трафик маскируется под WebRTC видеозвонок на указанной платформе.
 *
 * Допустимые провайдеры: jitsi, telemost, wbstream, custom.
 * Допустимые транспорты: datachannel, vp8channel, seichannel, videochannel.
 *
 * URI формат: olcrtc://<Provider>?<Transport>[<payload>]@<RoomID>#<EncryptionKey>$<Remarks>
 */
@Serializable
data class OlcRtc(
    var remarks: String = "",
    var provider: String = "jitsi",
    var transport: String = "datachannel",
    var roomUrl: String = "",
    var encryptionKey: String = "",
    var payload: String = "",
    // Порт локального SOCKS5 — выделяется динамически при запуске,
    // но сохраняется в конфигурации как базовый preferred порт.
    var localSocksPort: String = "10808",
) : UrlProxyServer<OlcRtc> {

    companion object {
        val AllowedProviders = setOf("jitsi", "telemost", "wbstream", "custom")
        val AllowedTransports = setOf("datachannel", "vp8channel", "seichannel", "videochannel")
    }

    override fun getInfo(): ProxyServerInfo {
        val displayAddress = "$provider/${transport}"
        return ProxyServerInfo(remarks, displayAddress, "OLCRTC")
    }

    /**
     * Генерирует Xray SOCKS5 outbound на localhost, где слушает клиент OLCRTC.
     * Порт localSocksPort используется как предпочтительный, но при запуске
     * SkipiCoreRuntime выделяет свободный порт динамически.
     */
    override fun toXrayOutbound(tag: String): OutboundObject {
        return toXrayOutboundWithPort(tag, localSocksPort.toIntOrNull() ?: 10808)
    }

    /**
     * Вариант с явным указанием порта — вызывается из SkipiCoreRuntime
     * после того как был выделен реальный свободный порт.
     */
    fun toXrayOutboundWithPort(tag: String, port: Int): OutboundObject {
        return OutboundObject(
            tag = tag,
            protocol = ProxyServerConstants.PROTOCOL_SOCKS,
            settings = buildJsonObject {
                put("servers", buildJsonArray {
                    add(buildJsonObject {
                        put("address", "127.0.0.1")
                        put("port", port)
                    })
                })
            },
        )
    }

    /**
     * Генерирует YAML конфигурацию для клиента OLCRTC (cnc режим).
     * Передаётся в skipi-core метод startOlcRtc(yamlConfig, socksPort).
     */
    fun toOlcRtcYamlConfig(socksPort: Int): String {
        val payloadStr = if (payload.isNotBlank()) "[$payload]" else ""
        return buildString {
            appendLine("mode: cnc")
            appendLine("provider: $provider")
            appendLine("transport: $transport$payloadStr")
            appendLine("room: $roomUrl")
            appendLine("key: $encryptionKey")
            appendLine("socks5_listen: 127.0.0.1:$socksPort")
        }
    }

    override fun parse(url: Url): OlcRtc {
        val raw = url.toString()
        val withoutScheme = raw.substringAfter("://")
        val beforeHash = withoutScheme.substringBefore('#')
        val afterHash = if (withoutScheme.contains('#')) withoutScheme.substringAfter('#') else ""

        // Fragment contains "<EncryptionKey>$<Remarks>"
        val dollarIdx = afterHash.indexOf('$')
        if (dollarIdx >= 0) {
            this.encryptionKey = afterHash.substring(0, dollarIdx)
            this.remarks = afterHash.substring(dollarIdx + 1)
        } else {
            this.encryptionKey = afterHash
            this.remarks = url.proxyUrlRemarks()
        }

        // Before hash: <Provider>?<Transport>[<payload>]@<RoomID>
        if (beforeHash.contains('?') && beforeHash.contains('@')) {
            this.provider = beforeHash.substringBefore('?').lowercase().ifBlank { "jitsi" }
            val middleAndEnd = beforeHash.substringAfter('?')
            val transportWithPayload = middleAndEnd.substringBefore('@')
            val roomAndParams = middleAndEnd.substringAfter('@')

            val bracketIdx = transportWithPayload.indexOf('[')
            if (bracketIdx >= 0 && transportWithPayload.endsWith(']')) {
                this.transport = transportWithPayload.substring(0, bracketIdx)
                this.payload = transportWithPayload.substring(bracketIdx + 1, transportWithPayload.length - 1)
            } else {
                this.transport = transportWithPayload.ifBlank { "datachannel" }
                this.payload = ""
            }

            this.roomUrl = roomAndParams.substringBefore('?')
            val paramsPart = if (roomAndParams.contains('?')) roomAndParams.substringAfter('?') else ""
            if (paramsPart.contains("socksport=")) {
                this.localSocksPort = paramsPart.substringAfter("socksport=").substringBefore('&')
            } else {
                this.localSocksPort = url.parameters["socksport"] ?: "10808"
            }
        } else {
            // Fallback to standard URL parsing
            this.provider = url.host.lowercase().ifBlank { "jitsi" }
            val userInfo = url.encodedUser ?: ""
            val bracketIdx = userInfo.indexOf('[')
            if (bracketIdx >= 0 && userInfo.endsWith(']')) {
                this.transport = userInfo.substring(0, bracketIdx)
                this.payload = userInfo.substring(bracketIdx + 1, userInfo.length - 1)
            } else {
                this.transport = userInfo.ifBlank { "datachannel" }
                this.payload = ""
            }
            this.roomUrl = url.password ?: url.encodedPath.trimStart('/')
            this.localSocksPort = url.parameters["socksport"] ?: "10808"
        }
        return this
    }

    override fun getUrl(): String {
        val payloadStr = if (payload.isNotBlank()) "[$payload]" else ""
        val keyAndRemarks = if (remarks.isNotBlank()) "$encryptionKey$$remarks" else encryptionKey
        val socksParam = if (localSocksPort.isNotBlank() && localSocksPort != "10808") "?socksport=$localSocksPort" else ""
        return "olcrtc://$provider?$transport$payloadStr@$roomUrl$socksParam#$keyAndRemarks"
    }

    override fun update(other: ProxyServer<*>) {
        if (other !is OlcRtc) {
            proxyServerTypeMismatch()
        }
        this.apply {
            remarks = other.remarks
            provider = other.provider
            transport = other.transport
            roomUrl = other.roomUrl
            encryptionKey = other.encryptionKey
            payload = other.payload
            localSocksPort = other.localSocksPort
        }
    }

    override fun validateBasic(): List<ProxyServerValidationIssue> = buildList {
        validateRequired(remarks, "Remarks")
        validateRequired(roomUrl, "Room URL")
        validateRequired(encryptionKey, "Encryption Key")
    }

    override fun validateFull(): List<ProxyServerValidationIssue> = buildList {
        addAll(validateBasic())
        validateOlcRtcProvider(provider)
        validateOlcRtcTransport(transport)
        validateOlcRtcEncryptionKey(encryptionKey)
    }

    override fun connectionFingerprint(): String {
        return "olcrtc|$provider|$transport|${roomUrl.trim()}|${encryptionKey.take(8)}"
    }
}
