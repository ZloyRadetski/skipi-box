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
 * Допустимые провайдеры: jitsi, telemost, wbstream.
 * Допустимые транспорты: datachannel, vp8channel, seichannel, videochannel.
 *
 * URI формат: olcrtc://<Provider>?<Transport><payload>@<RoomID>#<EncryptionKey>$<Remarks> (также поддерживается устаревший [...])
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
        // These names are the providers exposed by the embedded olcrtc mobile
        // runtime. "custom" was previously offered by the UI but cannot be
        // started by that runtime, so it only produced a late connection error.
        val AllowedProviders = setOf("jitsi", "telemost", "wbstream")
        val AllowedTransports = setOf("datachannel", "vp8channel", "seichannel", "videochannel")

        val KNOWN_VP8_KEYS = setOf("vp8-fps", "fps", "vp8-batch", "batch")
        val KNOWN_SEI_KEYS = setOf("fps", "batch", "frag", "ack-ms", "ack_timeout_ms", "vp8-fps", "vp8-batch")
        val KNOWN_VIDEO_KEYS = setOf(
            "video-w", "width", "video-h", "height", "video-fps", "fps",
            "video-codec", "codec", "video-qr-size", "qr_size",
            "video-qr-recovery", "qr_recovery", "video-tile-module", "tile_module",
            "video-tile-rs", "tile_rs"
        )

        fun parsePayloadString(payloadStr: String): Map<String, String> {
            if (payloadStr.isBlank()) return emptyMap()
            val result = mutableMapOf<String, String>()
            for (part in payloadStr.split('&')) {
                val trimmed = part.trim()
                if (trimmed.isEmpty()) continue
                val equalsIdx = trimmed.indexOf('=')
                if (equalsIdx >= 0) {
                    val k = trimmed.substring(0, equalsIdx).trim().lowercase()
                    val v = trimmed.substring(equalsIdx + 1).trim()
                    result[k] = v
                } else {
                    result[trimmed.lowercase()] = ""
                }
            }
            return result
        }
    }

    override fun getInfo(): ProxyServerInfo {
        val displayAddress = "$provider/${transport}"
        return ProxyServerInfo(remarks, displayAddress, "OLCRTC")
    }

    /**
     * Извлекает хост и порт сигнального сервера или комнаты для проверки доступности (TCP ping).
     */
    fun signalingEndpoint(): Pair<String, Int>? {
        val clean = roomUrl.trim()
        if (clean.startsWith("https://", ignoreCase = true) || clean.startsWith("http://", ignoreCase = true)) {
            val parsed = runCatching { Url(clean) }.getOrNull()
            if (parsed != null && parsed.host.isNotBlank()) {
                return parsed.host.trim('[', ']') to parsed.port
            }
        } else if (clean.contains(':') && clean.indexOf(':') < (clean.indexOf('/').takeIf { it >= 0 } ?: clean.length)) {
            val host = clean.substringBefore(':').trim().trim('[', ']')
            val port = clean.substringAfter(':').substringBefore('/').substringBefore('?').toIntOrNull()
            if (host.isNotEmpty() && port != null && port in 1..65535) return host to port
        } else if (clean.contains('.') && !clean.contains('/')) {
            return clean.trim('[', ']') to 443
        }
        return when (provider.trim().lowercase()) {
            "jitsi" -> "meet.jit.si" to 443
            "telemost" -> "telemost.yandex.ru" to 443
            else -> null
        }
    }

    /**
     * Compares the fields that define an olcRTC runtime. There is one native
     * runtime per process, therefore equal room/provider alone is not enough:
     * a different transport, key or payload must never reuse another bridge.
     */
    fun matchesRuntimeConfig(other: OlcRtc): Boolean {
        return runtimeIdentity() == other.runtimeIdentity()
    }

    @Deprecated("Use matchesRuntimeConfig: endpoint equality is not enough for an olcRTC bridge")
    fun matchesEndpoint(other: OlcRtc): Boolean = matchesRuntimeConfig(other)

    private fun runtimeIdentity(): String {
        val normalizedPayload = payloadParameters()
            .toSortedMap()
            .entries
            .joinToString("&") { (key, value) -> "$key=${value.trim()}" }
        return listOf(
            provider.trim().lowercase(),
            transport.trim().lowercase(),
            roomUrl.trim(),
            encryptionKey.trim().lowercase(),
            normalizedPayload,
        ).joinToString("|")
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
        return toXrayOutboundWithPortAndAuth(tag, port)
    }

    /**
     * Вариант с явным указанием порта и учетных данных авторизации SOCKS5.
     */
    fun toXrayOutboundWithPortAndAuth(
        tag: String,
        port: Int,
        user: String = "",
        pass: String = "",
    ): OutboundObject {
        return OutboundObject(
            tag = tag,
            protocol = ProxyServerConstants.PROTOCOL_SOCKS,
            settings = buildJsonObject {
                put("servers", buildJsonArray {
                    add(buildJsonObject {
                        put("address", "127.0.0.1")
                        put("port", port)
                        if (user.isNotBlank() || pass.isNotBlank()) {
                            put("users", buildJsonArray {
                                add(buildJsonObject {
                                    put("user", user)
                                    put("pass", pass)
                                    put("level", 0)
                                })
                            })
                        }
                    })
                })
            },
        )
    }

    /**
     * Разбирает строку payload на пары ключ-значение.
     */
    fun payloadParameters(): Map<String, String> = parsePayloadString(payload)

    // VP8 параметры
    val vp8Fps: Int? get() = payloadParameters()["vp8-fps"]?.toIntOrNull()
    val vp8Batch: Int? get() = (payloadParameters()["vp8-batch"] ?: payloadParameters()["batch"])?.toIntOrNull()

    // SEI параметры
    val seiFps: Int? get() = payloadParameters()["fps"]?.toIntOrNull()
    val seiBatch: Int? get() = payloadParameters()["batch"]?.toIntOrNull()
    val seiFragmentSize: Int? get() = payloadParameters()["frag"]?.toIntOrNull()
    val seiAckTimeoutMs: Int? get() = (payloadParameters()["ack-ms"] ?: payloadParameters()["ack_timeout_ms"])?.toIntOrNull()

    // Videochannel параметры
    val videoWidth: Int? get() = (payloadParameters()["video-w"] ?: payloadParameters()["width"])?.toIntOrNull()
    val videoHeight: Int? get() = (payloadParameters()["video-h"] ?: payloadParameters()["height"])?.toIntOrNull()
    val videoFps: Int? get() = (payloadParameters()["video-fps"] ?: payloadParameters()["fps"])?.toIntOrNull()
    val videoCodec: String? get() = payloadParameters()["video-codec"] ?: payloadParameters()["codec"]
    val videoQrSize: Int? get() = (payloadParameters()["video-qr-size"] ?: payloadParameters()["qr_size"])?.toIntOrNull()
    val videoQrRecovery: String? get() = payloadParameters()["video-qr-recovery"] ?: payloadParameters()["qr_recovery"]
    val videoTileModule: Int? get() = (payloadParameters()["video-tile-module"] ?: payloadParameters()["tile_module"])?.toIntOrNull()
    val videoTileRs: Int? get() = (payloadParameters()["video-tile-rs"] ?: payloadParameters()["tile_rs"])?.toIntOrNull()

    fun setPayloadParameter(key: String, value: String?, vararg aliasesToRemove: String) {
        val params = payloadParameters().toMutableMap()
        val normalizedKey = key.trim().lowercase()
        for (alias in aliasesToRemove) {
            params.remove(alias.trim().lowercase())
        }
        if (value.isNullOrBlank()) {
            params.remove(normalizedKey)
        } else {
            params[normalizedKey] = value.trim()
        }
        payload = params.entries.joinToString("&") { (k, v) ->
            if (v.isEmpty()) k else "$k=$v"
        }
    }

    fun getPayloadParameter(vararg keys: String): String {
        val params = payloadParameters()
        for (k in keys) {
            val v = params[k.trim().lowercase()]
            if (!v.isNullOrEmpty()) return v
        }
        return ""
    }

    fun getCustomPayload(knownKeys: Set<String>): String {
        val params = payloadParameters()
        val custom = params.filterKeys { it !in knownKeys }
        return custom.entries.joinToString("&") { (k, v) ->
            if (v.isEmpty()) k else "$k=$v"
        }
    }

    fun setCustomPayload(knownKeys: Set<String>, value: String?) {
        val currentParams = payloadParameters().filterKeys { it in knownKeys }.toMutableMap()
        if (!value.isNullOrBlank()) {
            val customParams = parsePayloadString(value)
            currentParams.putAll(customParams)
        }
        payload = currentParams.entries.joinToString("&") { (k, v) ->
            if (v.isEmpty()) k else "$k=$v"
        }
    }

    /**
     * Генерирует YAML конфигурацию для клиента OLCRTC (cnc режим).
     * Передаётся в skipi-core метод startOlcRtc(yamlConfig, socksPort).
     */
    fun toOlcRtcYamlConfig(
        socksPort: Int,
        socksUser: String = "",
        socksPass: String = "",
        dnsServer: String,
    ): String {
        require(dnsServer.isNotBlank()) { "olcRTC requires a raw DNS server" }
        val cleanTransport = transport.trim().lowercase().ifBlank { "datachannel" }
        val params = payloadParameters()
        return buildString {
            appendLine("mode: cnc")
            appendLine("provider: $provider")
            appendLine("transport: $cleanTransport")
            appendLine("room: $roomUrl")
            appendLine("key: $encryptionKey")
            appendLine("dns: ${dnsServer.trim()}")
            appendLine("socks5_listen: 127.0.0.1:$socksPort")
            if (socksUser.isNotBlank()) {
                appendLine("socks5_user: $socksUser")
            }
            if (socksPass.isNotBlank()) {
                appendLine("socks5_pass: $socksPass")
            }
            when (cleanTransport) {
                "vp8channel" -> {
                    val fps = params["vp8-fps"]?.toIntOrNull()
                    val batch = (params["vp8-batch"] ?: params["batch"])?.toIntOrNull()
                    if (fps != null || batch != null) {
                        appendLine("vp8:")
                        if (fps != null) appendLine("  fps: $fps")
                        if (batch != null) appendLine("  batch_size: $batch")
                    }
                }
                "seichannel" -> {
                    val fps = params["fps"]?.toIntOrNull()
                    val batch = params["batch"]?.toIntOrNull()
                    val frag = params["frag"]?.toIntOrNull()
                    val ackMs = (params["ack-ms"] ?: params["ack_timeout_ms"])?.toIntOrNull()
                    if (fps != null || batch != null || frag != null || ackMs != null) {
                        appendLine("sei:")
                        if (fps != null) appendLine("  fps: $fps")
                        if (batch != null) appendLine("  batch_size: $batch")
                        if (frag != null) appendLine("  fragment_size: $frag")
                        if (ackMs != null) appendLine("  ack_timeout_ms: $ackMs")
                    }
                }
                "videochannel" -> {
                    val w = (params["video-w"] ?: params["width"])?.toIntOrNull()
                    val h = (params["video-h"] ?: params["height"])?.toIntOrNull()
                    val fps = (params["video-fps"] ?: params["fps"])?.toIntOrNull()
                    val codec = params["video-codec"] ?: params["codec"]
                    val qrSize = (params["video-qr-size"] ?: params["qr_size"])?.toIntOrNull()
                    val qrRecovery = params["video-qr-recovery"] ?: params["qr_recovery"]
                    val tileModule = (params["video-tile-module"] ?: params["tile_module"])?.toIntOrNull()
                    val tileRs = (params["video-tile-rs"] ?: params["tile_rs"])?.toIntOrNull()
                    if (w != null || h != null || fps != null || codec != null || qrSize != null || qrRecovery != null || tileModule != null || tileRs != null) {
                        appendLine("video:")
                        if (w != null) appendLine("  width: $w")
                        if (h != null) appendLine("  height: $h")
                        if (fps != null) appendLine("  fps: $fps")
                        if (codec != null) appendLine("  codec: $codec")
                        if (qrSize != null) appendLine("  qr_size: $qrSize")
                        if (qrRecovery != null) appendLine("  qr_recovery: $qrRecovery")
                        if (tileModule != null) appendLine("  tile_module: $tileModule")
                        if (tileRs != null) appendLine("  tile_rs: $tileRs")
                    }
                }
            }
        }
    }

    private fun extractTransportAndPayload(raw: String): Pair<String, String> {
        val clean = raw.trim()
        val angleOpen = clean.indexOf('<')
        if (angleOpen >= 0 && clean.endsWith('>')) {
            val transport = clean.substring(0, angleOpen).ifBlank { "datachannel" }
            val payload = clean.substring(angleOpen + 1, clean.length - 1)
            return transport to payload
        }
        val squareOpen = clean.indexOf('[')
        if (squareOpen >= 0 && clean.endsWith(']')) {
            val transport = clean.substring(0, squareOpen).ifBlank { "datachannel" }
            val payload = clean.substring(squareOpen + 1, clean.length - 1)
            return transport to payload
        }
        return clean.ifBlank { "datachannel" } to ""
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

        // Before hash: <Provider>?<Transport><payload>@<RoomID>
        if (beforeHash.contains('?') && beforeHash.contains('@')) {
            this.provider = beforeHash.substringBefore('?').lowercase().ifBlank { "jitsi" }
            val middleAndEnd = beforeHash.substringAfter('?')
            val transportWithPayload = middleAndEnd.substringBefore('@')
            val roomAndParams = middleAndEnd.substringAfter('@')

            val (parsedTransport, parsedPayload) = extractTransportAndPayload(transportWithPayload)
            this.transport = parsedTransport
            this.payload = parsedPayload

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
            val (parsedTransport, parsedPayload) = extractTransportAndPayload(userInfo)
            this.transport = parsedTransport
            this.payload = parsedPayload

            this.roomUrl = url.password ?: url.encodedPath.trimStart('/')
            this.localSocksPort = url.parameters["socksport"] ?: "10808"
        }
        return this
    }

    override fun getUrl(): String {
        val payloadStr = if (payload.isNotBlank()) "<$payload>" else ""
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
