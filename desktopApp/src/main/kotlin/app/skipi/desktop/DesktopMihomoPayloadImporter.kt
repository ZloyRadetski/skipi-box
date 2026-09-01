// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.Trojan
import features.proxy.server.model.V2RayParameters
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard
import features.subscription.importSubscriptionServers
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A dependency-free Mihomo/Clash importer for the YAML subset normally returned by
 * subscription providers.
 *
 * It deliberately does not try to be a general YAML implementation.  It accepts mapping/list
 * documents, flow maps and lists, quotes, comments and the nested fields used by proxy nodes.
 * Full YAML features (anchors, tags and arbitrary block syntax) stay behind the optional
 * SnakeYAML-based Android importer until that dependency is shared with Desktop.
 *
 * External `proxy-providers` are never fetched here.  Callers pass already downloaded provider
 * texts through [providerPayloads], which keeps networking and timeout policy in the desktop
 * subscription adapter.  Missing provider bodies are returned as [DesktopMihomoProviderRequest].
 */
internal object DesktopMihomoPayloadImporter {
    const val DefaultMaxProviderDepth: Int = 2

    fun import(
        text: String,
        providerPayloads: Map<String, String> = emptyMap(),
        maxProviderDepth: Int = DefaultMaxProviderDepth,
    ): DesktopMihomoPayloadImportResult {
        require(maxProviderDepth >= 0) { "Provider depth must not be negative" }
        return importInternal(
            text = text,
            providerPayloads = providerPayloads,
            maxProviderDepth = maxProviderDepth,
            providerDepth = 0,
            fetchedProviderUrls = emptySet(),
        ).toPublicResult()
    }

    private fun importInternal(
        text: String,
        providerPayloads: Map<String, String>,
        maxProviderDepth: Int,
        providerDepth: Int,
        fetchedProviderUrls: Set<String>,
    ): InternalMihomoImportResult {
        val root = runCatching { DesktopMinimalYamlParser(text).parse() }.getOrNull()
        val document = root?.toMihomoDocument()
        if (document == null || !document.recognized) {
            return importDirectSubscriptionPayload(text)
        }

        val servers = mutableListOf<ProxyServer<*>>()
        val diagnostics = mutableListOf<String>()
        val pendingProviders = mutableListOf<DesktopMihomoProviderRequest>()
        var proxyEntryCount = document.proxyNodes.size
        var rejectedProxyCount = 0

        document.proxyNodes.forEachIndexed { index, node ->
            runCatching { node.toDesktopProxyServer() }
                .onSuccess(servers::add)
                .onFailure { error ->
                    rejectedProxyCount += 1
                    diagnostics += node.nodeDiagnostic(index, error.message.orEmpty())
                }
        }

        document.inlinePayloadTexts.forEach { inlinePayload ->
            val child = importInternal(
                text = inlinePayload,
                providerPayloads = providerPayloads,
                maxProviderDepth = maxProviderDepth,
                providerDepth = providerDepth,
                fetchedProviderUrls = fetchedProviderUrls,
            )
            proxyEntryCount += child.proxyEntryCount
            rejectedProxyCount += child.rejectedProxyCount
            servers += child.servers
            pendingProviders += child.pendingProviders
            diagnostics += child.diagnostics
        }

        document.providerRequests.forEach { request ->
            val providerText = providerPayloads[request.url]
            when {
                providerText == null -> pendingProviders += request
                providerDepth >= maxProviderDepth -> {
                    diagnostics += "Provider '${request.name}' skipped: maximum nesting depth reached."
                }

                request.url in fetchedProviderUrls -> {
                    diagnostics += "Provider '${request.name}' skipped: recursive provider URL."
                }

                else -> {
                    val child = importInternal(
                        text = providerText,
                        providerPayloads = providerPayloads,
                        maxProviderDepth = maxProviderDepth,
                        providerDepth = providerDepth + 1,
                        fetchedProviderUrls = fetchedProviderUrls + request.url,
                    )
                    proxyEntryCount += child.proxyEntryCount
                    rejectedProxyCount += child.rejectedProxyCount
                    servers += child.servers
                    pendingProviders += child.pendingProviders
                    diagnostics += child.diagnostics
                }
            }
        }

        return InternalMihomoImportResult(
            recognizedYaml = true,
            proxyEntryCount = proxyEntryCount,
            servers = servers.distinctBy(ProxyServer<*>::connectionFingerprint),
            rejectedProxyCount = rejectedProxyCount,
            pendingProviders = pendingProviders.distinctBy(DesktopMihomoProviderRequest::url),
            diagnostics = diagnostics,
        )
    }

    private fun importDirectSubscriptionPayload(text: String): InternalMihomoImportResult {
        val imported = text.importSubscriptionServers()
        return InternalMihomoImportResult(
            recognizedYaml = false,
            proxyEntryCount = imported.urlCount,
            servers = imported.servers,
            rejectedProxyCount = imported.rejectedUrlCount,
            pendingProviders = emptyList(),
            diagnostics = emptyList(),
        )
    }
}

internal data class DesktopMihomoProviderRequest(
    val name: String,
    val url: String,
)

internal data class DesktopMihomoPayloadImportResult(
    /** Whether this input was recognized as a Mihomo/Clash YAML document. */
    val recognizedYaml: Boolean,
    /** Number of proxy entries encountered across the root and supplied provider payloads. */
    val proxyEntryCount: Int,
    val servers: List<ProxyServer<*>>,
    val rejectedProxyCount: Int,
    /** HTTP(S) proxy-provider URLs whose body was not supplied through [DesktopMihomoPayloadImporter.import]. */
    val pendingProviders: List<DesktopMihomoProviderRequest>,
    /** Safe, user-facing diagnostics; server secrets are never included. */
    val diagnostics: List<String>,
)

private data class InternalMihomoImportResult(
    val recognizedYaml: Boolean,
    val proxyEntryCount: Int,
    val servers: List<ProxyServer<*>>,
    val rejectedProxyCount: Int,
    val pendingProviders: List<DesktopMihomoProviderRequest>,
    val diagnostics: List<String>,
) {
    fun toPublicResult(): DesktopMihomoPayloadImportResult = DesktopMihomoPayloadImportResult(
        recognizedYaml = recognizedYaml,
        proxyEntryCount = proxyEntryCount,
        servers = servers,
        rejectedProxyCount = rejectedProxyCount,
        pendingProviders = pendingProviders,
        diagnostics = diagnostics,
    )
}

private data class DesktopMihomoDocument(
    val proxyNodes: List<DesktopYamlMap>,
    val inlinePayloadTexts: List<String>,
    val providerRequests: List<DesktopMihomoProviderRequest>,
    val recognized: Boolean,
)

private fun DesktopYamlValue.toMihomoDocument(): DesktopMihomoDocument? {
    val rootMap = asMap()
    if (rootMap == null) {
        val listNodes = asList().orEmpty().mapNotNull(DesktopYamlValue::asMap)
        return DesktopMihomoDocument(
            proxyNodes = listNodes,
            inlinePayloadTexts = emptyList(),
            providerRequests = emptyList(),
            recognized = listNodes.isNotEmpty(),
        )
    }

    val proxyNodes = mutableListOf<DesktopYamlMap>()
    val inlinePayloadTexts = mutableListOf<String>()
    val providerRequests = mutableListOf<DesktopMihomoProviderRequest>()
    var recognized = false

    rootMap.value("proxies")?.let { value ->
        recognized = true
        proxyNodes += value.proxyNodes()
        inlinePayloadTexts += value.inlinePayloadTexts()
    }
    rootMap.value("payload")?.let { value ->
        recognized = true
        proxyNodes += value.proxyNodes()
        inlinePayloadTexts += value.inlinePayloadTexts()
    }
    rootMap.value("proxy-providers")?.asMap()?.let { providers ->
        recognized = true
        providers.forEach { (name, rawProvider) ->
            val provider = rawProvider.asMap() ?: return@forEach
            provider.value("payload")?.let { payload ->
                proxyNodes += payload.proxyNodes()
                inlinePayloadTexts += payload.inlinePayloadTexts()
            }
            val type = provider.string("type")?.lowercase().orEmpty()
            val url = provider.string("url")?.takeIf(::isHttpProviderUrl)
            if (url != null && (type.isBlank() || type == "http")) {
                providerRequests += DesktopMihomoProviderRequest(name = name, url = url)
            }
        }
    }

    return DesktopMihomoDocument(
        proxyNodes = proxyNodes,
        inlinePayloadTexts = inlinePayloadTexts,
        providerRequests = providerRequests,
        recognized = recognized,
    )
}

private fun DesktopYamlValue.proxyNodes(): List<DesktopYamlMap> = when (this) {
    is DesktopYamlValue.Mapping -> {
        if (values.value("type") != null) listOf(values)
        else values.values.mapNotNull(DesktopYamlValue::asMap)
    }

    is DesktopYamlValue.Sequence -> values.mapNotNull(DesktopYamlValue::asMap)
    is DesktopYamlValue.Scalar -> emptyList()
}

private fun DesktopYamlValue.inlinePayloadTexts(): List<String> = when (this) {
    is DesktopYamlValue.Scalar -> value.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
    is DesktopYamlValue.Sequence -> values.mapNotNull { value ->
        value.asScalar()?.takeIf(String::isNotBlank)
    }.takeIf(List<String>::isNotEmpty)?.let { values -> listOf(values.joinToString("\n")) }.orEmpty()

    is DesktopYamlValue.Mapping -> emptyList()
}

private fun DesktopYamlMap.toDesktopProxyServer(): ProxyServer<*> {
    val type = requiredString("type").lowercase()
    val server = when (type) {
        "http" -> toHttpProxy()
        "socks", "socks5" -> toSocksProxy()
        "ss", "shadowsocks" -> toShadowsocksProxy()
        "vmess" -> toVMessProxy()
        "vless" -> toVlessProxy()
        "trojan" -> toTrojanProxy()
        "hy2", "hysteria2" -> toHysteria2Proxy()
        "wg", "wireguard" -> toWireguardProxy()
        else -> throw IllegalArgumentException("unsupported proxy type '$type'")
    }
    require(server.validateBasic().isEmpty()) { "basic proxy validation failed" }
    return server
}

private fun DesktopYamlMap.toHttpProxy(): HTTP {
    ensureNoTlsOptions("HTTP proxy TLS is not supported")
    require(map("headers").isNullOrEmpty()) { "HTTP proxy custom headers are not supported" }
    return HTTP(
        remarks = requiredString("name"),
        server = requiredString("server"),
        port = requiredString("port"),
        user = string("username", "user"),
        password = string("password", "pass"),
    )
}

private fun DesktopYamlMap.toSocksProxy(): Socks {
    ensureNoTlsOptions("SOCKS proxy TLS is not supported")
    return Socks(
        remarks = requiredString("name"),
        server = requiredString("server"),
        port = requiredString("port"),
        user = string("username", "user"),
        password = string("password", "pass"),
    )
}

private fun DesktopYamlMap.toShadowsocksProxy(): Shadowsocks {
    require(string("plugin").isNullOrBlank() && map("plugin-opts").isNullOrEmpty()) {
        "Shadowsocks plugin options are not supported"
    }
    return Shadowsocks(
        remarks = requiredString("name"),
        server = requiredString("server"),
        port = requiredString("port"),
        method = requiredString("cipher", "method"),
        password = requiredString("password"),
    )
}

private fun DesktopYamlMap.toVMessProxy(): VMess {
    val alterId = string("alterId", "alter-id", "alterid")?.toIntOrNull() ?: 0
    require(alterId == 0) { "VMess legacy alterId is not supported" }
    return VMess(
        remarks = requiredString("name"),
        server = requiredString("server"),
        port = requiredString("port"),
        id = requiredString("uuid", "id"),
        encryption = string("cipher", "encryption") ?: "auto",
        parms = toV2RayParameters(defaultSecurity = "none"),
    )
}

private fun DesktopYamlMap.toVlessProxy(): VLESS = VLESS(
    remarks = requiredString("name"),
    server = requiredString("server"),
    port = requiredString("port"),
    id = requiredString("uuid", "id"),
    encryption = string("encryption").orEmpty(),
    flow = string("flow").orEmpty(),
    parms = toV2RayParameters(defaultSecurity = "none"),
)

private fun DesktopYamlMap.toTrojanProxy(): Trojan {
    require(map("ss-opts")?.boolean("enabled") != true) { "Trojan ss-opts are not supported" }
    return Trojan(
        remarks = requiredString("name"),
        server = requiredString("server"),
        port = requiredString("port"),
        password = requiredString("password"),
        parms = toV2RayParameters(defaultSecurity = "tls"),
    )
}

private fun DesktopYamlMap.toHysteria2Proxy(): Hysteria2 {
    require(map("realm-opts")?.boolean("enable") != true) { "Hysteria2 realm-opts are not supported" }
    val multiPorts = string("ports").orEmpty()
    val hopInterval = string("hop-interval").orEmpty()
    require('-' !in hopInterval) { "Hysteria2 ranged hop-interval is not supported" }
    require(boolean("skip-cert-verify") != true) { "skip-cert-verify is not supported" }
    return Hysteria2(
        remarks = requiredString("name"),
        server = requiredString("server"),
        port = string("port") ?: multiPorts.firstPortInRange(),
        auth = requiredString("password", "auth", "auth-str"),
        obfs = string("obfs").orEmpty(),
        obfsPassword = string("obfs-password", "obfsPassword").orEmpty(),
        sni = string("sni", "servername").orEmpty(),
        pinSHA256 = string("pinSHA256", "pin-sha256", "fingerprint").orEmpty(),
        mport = multiPorts,
        mportHopInt = hopInterval,
        up = string("up").orEmpty(),
        down = string("down").orEmpty(),
        security = if (hasTlsOptions() || boolean("tls") == true) "tls" else "none",
    )
}

private fun DesktopYamlMap.toWireguardProxy(): Wireguard {
    val peers = list("peers").orEmpty().mapNotNull(DesktopYamlValue::asMap)
    require(peers.size <= 1) { "multiple WireGuard peers are not supported" }
    val endpoint = peers.firstOrNull() ?: this
    val address = buildList {
        string("ip")?.toWireguardAddress(ipv6 = false)?.let(::add)
        string("ipv6")?.toWireguardAddress(ipv6 = true)?.let(::add)
    }.takeIf(List<String>::isNotEmpty)?.joinToString(",") ?: throw IllegalArgumentException("WireGuard ip or ipv6 is required")
    return Wireguard(
        remarks = requiredString("name"),
        server = endpoint.requiredString("server"),
        port = endpoint.requiredString("port"),
        secretKey = requiredString("private-key", "privateKey"),
        publicKey = endpoint.requiredString("public-key", "publicKey"),
        preSharedKey = endpoint.string("pre-shared-key", "presharedkey", "preSharedKey").orEmpty(),
        reserved = endpoint.wireguardReserved(),
        address = address,
        mtu = string("mtu") ?: "1420",
    )
}

private fun DesktopYamlMap.toV2RayParameters(defaultSecurity: String): V2RayParameters {
    require(boolean("skip-cert-verify") != true) { "skip-cert-verify is not supported" }
    val wsOptions = map("ws-opts")
    val grpcOptions = map("grpc-opts")
    val xhttpOptions = map("xhttp-opts")
    val kcpOptions = map("kcp-opts")
    val realityOptions = map("reality-opts")
    val transport = when (string("network")?.lowercase().orEmpty()) {
        "", "tcp", "raw" -> "raw"
        "ws", "websocket" -> "websocket"
        "grpc" -> "grpc"
        "httpupgrade" -> "httpupgrade"
        "xhttp", "splithttp" -> "xhttp"
        "kcp", "mkcp" -> "mkcp"
        "http", "h2", "h3" -> throw IllegalArgumentException("unsupported V2Ray transport")
        else -> "raw"
    }
    val security = when {
        !realityOptions.isNullOrEmpty() -> "reality"
        boolean("tls") == true || hasTlsOptions() -> "tls"
        else -> defaultSecurity
    }
    val webOptions = wsOptions ?: emptyMap()
    val effectiveXhttpOptions = xhttpOptions ?: emptyMap()
    val effectiveKcpOptions = kcpOptions ?: emptyMap()
    return V2RayParameters(
        type = transport,
        security = security,
        path = when (transport) {
            "websocket", "httpupgrade" -> webOptions.websocketPath()
            "xhttp" -> effectiveXhttpOptions.string("path")
            else -> null
        },
        host = when (transport) {
            "websocket", "httpupgrade" -> webOptions.websocketHost()
            "xhttp" -> effectiveXhttpOptions.headersHost() ?: effectiveXhttpOptions.string("host")
            "mkcp" -> effectiveKcpOptions.map("header")?.string("host", "domain")
                ?: effectiveKcpOptions.string("host", "domain")
                ?: string("host", "domain")

            "raw" -> string("host")
            else -> null
        },
        headers = when (transport) {
            "websocket", "httpupgrade" -> webOptions.map("headers")?.headersJson()
            else -> null
        },
        mtu = if (transport == "mkcp") effectiveKcpOptions.string("mtu") ?: string("mtu") else null,
        tti = if (transport == "mkcp") effectiveKcpOptions.string("tti") ?: string("tti") else null,
        seed = if (transport == "mkcp") effectiveKcpOptions.string("seed") ?: string("seed") else null,
        serviceName = if (transport == "grpc") grpcOptions?.string("grpc-service-name", "service-name", "serviceName") else null,
        mode = when (transport) {
            "grpc" -> grpcOptions?.string("mode") ?: "gun"
            "xhttp" -> effectiveXhttpOptions.string("mode")
            else -> null
        },
        authority = if (transport == "grpc") grpcOptions?.string("authority") else null,
        fp = string("client-fingerprint")?.lowercase(),
        sni = string("servername", "sni"),
        alpn = value("alpn").scalarValues().joinToString(",").takeIf(String::isNotBlank),
        ech = map("ech-opts")?.takeIf { it.boolean("enable") == true }?.string("config"),
        pcs = string("fingerprint"),
        pbk = realityOptions?.string("public-key", "publicKey"),
        sid = realityOptions?.string("short-id", "shortId"),
        spx = realityOptions?.string("spider-x", "spiderX"),
        headerType = if (transport == "mkcp") {
            effectiveKcpOptions.map("header")?.string("type")
                ?: effectiveKcpOptions.string("headerType", "header-type")
                ?: "none"
        } else {
            "none"
        },
    )
}

private fun DesktopYamlMap.websocketPath(): String? {
    val path = string("path")
    val earlyData = value("max-early-data").asScalar()?.toIntOrNull() ?: return path
    if (earlyData <= 0) return path
    val headerName = string("early-data-header-name")
    require(headerName.isNullOrBlank() || headerName.equals("Sec-WebSocket-Protocol", ignoreCase = true)) {
        "unsupported WebSocket early-data header"
    }
    val base = path?.takeIf(String::isNotBlank) ?: "/"
    return base + if ('?' in base) "&ed=$earlyData" else "?ed=$earlyData"
}

private fun DesktopYamlMap.websocketHost(): String? = headersHost() ?: string("host")

private fun DesktopYamlMap.headersHost(): String? = map("headers")?.headerString("Host")

private fun DesktopYamlMap.headersJson(): String? {
    val json = buildJsonObject {
        forEach { (name, value) ->
            if (!name.equals("Host", ignoreCase = true)) {
                value.scalarValues().joinToString(",").takeIf(String::isNotBlank)?.let { put(name, it) }
            }
        }
    }
    return json.takeIf { it.isNotEmpty() }?.toString()
}

private fun DesktopYamlMap.hasTlsOptions(): Boolean = listOf(
    "sni",
    "servername",
    "fingerprint",
    "client-fingerprint",
    "certificate",
    "private-key",
).any { key -> !string(key).isNullOrBlank() } ||
    value("alpn").scalarValues().isNotEmpty() ||
    !map("ech-opts").isNullOrEmpty() ||
    !map("reality-opts").isNullOrEmpty()

private fun DesktopYamlMap.ensureNoTlsOptions(message: String) {
    require(boolean("tls") != true && !hasTlsOptions()) { message }
}

private fun DesktopYamlMap.wireguardReserved(): String {
    val values = value("reserved").scalarValues()
    if (values.isEmpty()) return "0,0,0"
    require(values.size == 3 && values.all { value -> value.toIntOrNull() != null }) {
        "WireGuard reserved must contain three numeric bytes"
    }
    return values.joinToString(",")
}

private fun String.firstPortInRange(): String = split(',').firstOrNull()
    ?.substringBefore('-')
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?: throw IllegalArgumentException("Hysteria2 port or ports is required")

private fun String.toWireguardAddress(ipv6: Boolean): String {
    val value = trim()
    require(value.isNotBlank()) { "WireGuard address is empty" }
    return if ('/' in value) value else if (ipv6) "$value/128" else "$value/32"
}

private fun DesktopYamlMap.nodeDiagnostic(index: Int, reason: String): String {
    val type = string("type").orEmpty().ifBlank { "unknown" }
    val name = string("name").orEmpty().ifBlank { "#${index + 1}" }
    return "Proxy '$name' ($type) skipped: ${reason.ifBlank { "invalid configuration" }}"
}

private fun isHttpProviderUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

private typealias DesktopYamlMap = Map<String, DesktopYamlValue>

private sealed interface DesktopYamlValue {
    data class Scalar(val value: String) : DesktopYamlValue
    data class Mapping(val values: LinkedHashMap<String, DesktopYamlValue>) : DesktopYamlValue
    data class Sequence(val values: MutableList<DesktopYamlValue>) : DesktopYamlValue
}

private fun DesktopYamlValue?.asMap(): DesktopYamlMap? = (this as? DesktopYamlValue.Mapping)?.values

private fun DesktopYamlValue?.asList(): List<DesktopYamlValue>? = (this as? DesktopYamlValue.Sequence)?.values

private fun DesktopYamlValue?.asScalar(): String? = (this as? DesktopYamlValue.Scalar)?.value

private fun DesktopYamlValue?.scalarValues(): List<String> = when (this) {
    is DesktopYamlValue.Scalar -> listOf(value).filter(String::isNotBlank)
    is DesktopYamlValue.Sequence -> values.flatMap(DesktopYamlValue::scalarValues)
    else -> emptyList()
}

private fun DesktopYamlMap.value(vararg names: String): DesktopYamlValue? = names.firstNotNullOfOrNull { name ->
    entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value
}

private fun DesktopYamlMap.string(vararg names: String): String? = value(*names).asScalar()?.trim()?.takeIf(String::isNotBlank)

private fun DesktopYamlMap.requiredString(vararg names: String): String =
    string(*names) ?: throw IllegalArgumentException("${names.first()} is required")

private fun DesktopYamlMap.map(name: String): DesktopYamlMap? = value(name).asMap()

private fun DesktopYamlMap.list(name: String): List<DesktopYamlValue>? = value(name).asList()

private fun DesktopYamlMap.boolean(name: String): Boolean? = when (value(name).asScalar()?.trim()?.lowercase()) {
    "1", "true", "yes", "on" -> true
    "0", "false", "no", "off" -> false
    else -> null
}

private fun DesktopYamlMap.headerString(name: String): String? = entries
    .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
    ?.value
    .scalarValues()
    .joinToString(",")
    .takeIf(String::isNotBlank)

/** A deliberately small YAML parser used only by [DesktopMihomoPayloadImporter]. */
private class DesktopMinimalYamlParser(source: String) {
    private val lines: List<DesktopYamlLine> = source
        .lineSequence()
        .mapIndexedNotNull { index, raw -> DesktopYamlLine.from(index + 1, raw) }
        .toList()
    private var index: Int = 0

    fun parse(): DesktopYamlValue? {
        if (lines.isEmpty()) return null
        return parseNode(lines.first().indent)
    }

    private fun parseNode(indent: Int): DesktopYamlValue {
        val line = lines.getOrNull(index) ?: throw IllegalArgumentException("Expected YAML value")
        return if (line.indent != indent) {
            throw IllegalArgumentException("Unexpected YAML indentation at line ${line.number}")
        } else if (line.isListItem) {
            parseSequence(indent)
        } else {
            parseMapping(indent)
        }
    }

    private fun parseMapping(indent: Int): DesktopYamlValue.Mapping {
        val values = linkedMapOf<String, DesktopYamlValue>()
        while (true) {
            val line = lines.getOrNull(index) ?: break
            if (line.indent != indent || line.isListItem) break
            val pair = splitYamlKeyValue(line.content)
                ?: throw IllegalArgumentException("Expected YAML key at line ${line.number}")
            val key = unquoteYaml(pair.first.trim())
            require(key.isNotBlank()) { "Empty YAML key at line ${line.number}" }
            index += 1
            values[key] = parseValueAfterKey(
                rawValue = pair.second.trim(),
                parentIndent = indent,
                lineNumber = line.number,
            )
        }
        return DesktopYamlValue.Mapping(values)
    }

    private fun parseSequence(indent: Int): DesktopYamlValue.Sequence {
        val values = mutableListOf<DesktopYamlValue>()
        while (true) {
            val line = lines.getOrNull(index) ?: break
            if (line.indent != indent || !line.isListItem) break
            val remainder = line.content.removePrefix("-").trimStart()
            index += 1
            val pair = splitYamlKeyValue(remainder)?.takeUnless { remainder.looksLikeUrl() }
            val value = when {
                remainder.isBlank() -> parseNestedValue(indent, line.number)
                pair != null -> {
                    val mapping = linkedMapOf<String, DesktopYamlValue>()
                    val key = unquoteYaml(pair.first.trim())
                    require(key.isNotBlank()) { "Empty YAML key at line ${line.number}" }
                    mapping[key] = parseValueAfterKey(pair.second.trim(), indent, line.number)
                    val next = lines.getOrNull(index)
                    if (next != null && next.indent > indent) {
                        val continuation = parseNode(next.indent)
                        if (continuation is DesktopYamlValue.Mapping) {
                            mapping.putAll(continuation.values)
                        } else {
                            throw IllegalArgumentException("Expected mapping continuation at line ${next.number}")
                        }
                    }
                    DesktopYamlValue.Mapping(mapping)
                }

                else -> parseInlineYamlValue(remainder)
            }
            values += value
        }
        return DesktopYamlValue.Sequence(values)
    }

    private fun parseValueAfterKey(rawValue: String, parentIndent: Int, lineNumber: Int): DesktopYamlValue = when {
        rawValue == "|" || rawValue == ">" -> parseBlockScalar(parentIndent, fold = rawValue == ">")
        rawValue.isBlank() -> parseNestedValue(parentIndent, lineNumber)
        else -> parseInlineYamlValue(rawValue)
    }

    private fun parseNestedValue(parentIndent: Int, lineNumber: Int): DesktopYamlValue {
        val next = lines.getOrNull(index) ?: return DesktopYamlValue.Scalar("")
        if (next.indent <= parentIndent) return DesktopYamlValue.Scalar("")
        return parseNode(next.indent)
    }

    private fun parseBlockScalar(parentIndent: Int, fold: Boolean): DesktopYamlValue {
        val start = lines.getOrNull(index) ?: return DesktopYamlValue.Scalar("")
        if (start.indent <= parentIndent) return DesktopYamlValue.Scalar("")
        val contentIndent = start.indent
        val values = mutableListOf<String>()
        while (true) {
            val line = lines.getOrNull(index) ?: break
            if (line.indent <= parentIndent) break
            val indentation = (line.indent - contentIndent).coerceAtLeast(0)
            values += " ".repeat(indentation) + line.content
            index += 1
        }
        return DesktopYamlValue.Scalar(values.joinToString(if (fold) " " else "\n"))
    }
}

private data class DesktopYamlLine(
    val number: Int,
    val indent: Int,
    val content: String,
) {
    val isListItem: Boolean get() = content == "-" || content.startsWith("- ")

    companion object {
        fun from(number: Int, raw: String): DesktopYamlLine? {
            val withoutBom = raw.removePrefix("\uFEFF")
            val commentFree = stripYamlComment(withoutBom).trimEnd()
            if (commentFree.isBlank()) return null
            val leading = commentFree.takeWhile { character -> character == ' ' || character == '\t' }
            require('\t' !in leading) { "Tabs are not supported in YAML indentation at line $number" }
            return DesktopYamlLine(number, leading.length, commentFree.drop(leading.length))
        }
    }
}

private fun String.looksLikeUrl(): Boolean = Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(this)

private fun stripYamlComment(value: String): String {
    var quote: Char? = null
    var escaped = false
    value.forEachIndexed { index, character ->
        when {
            quote != null && quote == '"' && escaped -> escaped = false
            quote != null && quote == '"' && character == '\\' -> escaped = true
            quote != null && character == quote -> quote = null
            quote == null && (character == '\'' || character == '"') -> quote = character
            quote == null && character == '#' && (index == 0 || value[index - 1].isWhitespace()) -> return value.substring(0, index)
        }
    }
    return value
}

private fun splitYamlKeyValue(value: String): Pair<String, String>? {
    val colon = value.indexOfTopLevel(':')
    return if (colon > 0) value.substring(0, colon) to value.substring(colon + 1) else null
}

private fun String.indexOfTopLevel(target: Char): Int {
    var quote: Char? = null
    var escaped = false
    var squareDepth = 0
    var curlyDepth = 0
    forEachIndexed { index, character ->
        when {
            quote != null && quote == '"' && escaped -> escaped = false
            quote != null && quote == '"' && character == '\\' -> escaped = true
            quote != null && character == quote -> quote = null
            quote == null && (character == '\'' || character == '"') -> quote = character
            quote == null && character == '[' -> squareDepth += 1
            quote == null && character == ']' -> squareDepth = (squareDepth - 1).coerceAtLeast(0)
            quote == null && character == '{' -> curlyDepth += 1
            quote == null && character == '}' -> curlyDepth = (curlyDepth - 1).coerceAtLeast(0)
            quote == null && squareDepth == 0 && curlyDepth == 0 && character == target -> return index
        }
    }
    return -1
}

private fun parseInlineYamlValue(raw: String): DesktopYamlValue {
    val value = raw.trim()
    return when {
        value.startsWith('{') && value.endsWith('}') -> parseFlowMapping(value.substring(1, value.length - 1))
        value.startsWith('[') && value.endsWith(']') -> DesktopYamlValue.Sequence(
            splitTopLevelYaml(value.substring(1, value.length - 1), ',')
                .filter(String::isNotBlank)
                .mapTo(mutableListOf()) { item -> parseInlineYamlValue(item) },
        )

        else -> DesktopYamlValue.Scalar(unquoteYaml(value))
    }
}

private fun parseFlowMapping(body: String): DesktopYamlValue.Mapping {
    val values = linkedMapOf<String, DesktopYamlValue>()
    splitTopLevelYaml(body, ',').filter(String::isNotBlank).forEach { item ->
        val pair = splitYamlKeyValue(item) ?: throw IllegalArgumentException("Invalid YAML flow mapping")
        val key = unquoteYaml(pair.first.trim())
        require(key.isNotBlank()) { "Empty YAML flow mapping key" }
        values[key] = parseInlineYamlValue(pair.second)
    }
    return DesktopYamlValue.Mapping(values)
}

private fun splitTopLevelYaml(value: String, separator: Char): List<String> {
    val results = mutableListOf<String>()
    var start = 0
    var quote: Char? = null
    var escaped = false
    var squareDepth = 0
    var curlyDepth = 0
    value.forEachIndexed { index, character ->
        when {
            quote != null && quote == '"' && escaped -> escaped = false
            quote != null && quote == '"' && character == '\\' -> escaped = true
            quote != null && character == quote -> quote = null
            quote == null && (character == '\'' || character == '"') -> quote = character
            quote == null && character == '[' -> squareDepth += 1
            quote == null && character == ']' -> squareDepth = (squareDepth - 1).coerceAtLeast(0)
            quote == null && character == '{' -> curlyDepth += 1
            quote == null && character == '}' -> curlyDepth = (curlyDepth - 1).coerceAtLeast(0)
            quote == null && squareDepth == 0 && curlyDepth == 0 && character == separator -> {
                results += value.substring(start, index).trim()
                start = index + 1
            }
        }
    }
    results += value.substring(start).trim()
    return results
}

private fun unquoteYaml(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length < 2) return trimmed
    return when {
        trimmed.startsWith('\'') && trimmed.endsWith('\'') -> trimmed.substring(1, trimmed.length - 1).replace("''", "'")
        trimmed.startsWith('"') && trimmed.endsWith('"') -> buildString {
            var escaped = false
            trimmed.substring(1, trimmed.length - 1).forEach { character ->
                when {
                    escaped -> {
                        append(
                            when (character) {
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                else -> character
                            },
                        )
                        escaped = false
                    }

                    character == '\\' -> escaped = true
                    else -> append(character)
                }
            }
            if (escaped) append('\\')
        }

        else -> trimmed
    }
}
