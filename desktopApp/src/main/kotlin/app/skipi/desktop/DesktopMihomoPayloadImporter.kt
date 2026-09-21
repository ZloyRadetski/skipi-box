// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.usecase.importer.CustomXrayConfigImportResult
import features.proxy.server.usecase.importer.MihomoProxyProvider
import features.proxy.server.usecase.importer.MihomoYamlMap
import features.proxy.server.usecase.importer.isSupportedMihomoProxyType
import features.proxy.server.usecase.importer.parseCustomXrayConfigPayload
import features.proxy.server.usecase.importer.string
import features.proxy.server.usecase.importer.toMihomoProxyServer
import features.proxy.server.usecase.importer.toMihomoProxyDocument
import features.subscription.importSubscriptionServers
import utils.decodeFlexibleBase64OrNull

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
        val document = root?.toSharedMihomoYamlValue()?.toMihomoProxyDocument()
        if (document == null || !document.recognized) {
            val directResult = importDirectSubscriptionPayload(text)
            if (directResult.servers.isNotEmpty() || directResult.proxyEntryCount > 0) {
                return directResult
            }
            val decoded = text.filterNot(Char::isWhitespace).decodeFlexibleBase64OrNull()?.decodeToString()
            if (decoded != null && decoded != text) {
                val decodedRoot = runCatching { DesktopMinimalYamlParser(decoded).parse() }.getOrNull()
                val decodedDoc = decodedRoot?.toSharedMihomoYamlValue()?.toMihomoProxyDocument()
                if (decodedDoc != null && decodedDoc.recognized) {
                    return importInternal(
                        text = decoded,
                        providerPayloads = providerPayloads,
                        maxProviderDepth = maxProviderDepth,
                        providerDepth = providerDepth,
                        fetchedProviderUrls = fetchedProviderUrls,
                    )
                }
                val decodedDirect = importDirectSubscriptionPayload(decoded)
                if (decodedDirect.servers.isNotEmpty() || decodedDirect.proxyEntryCount > 0) {
                    return decodedDirect
                }
            }
            return directResult
        }

        val servers = mutableListOf<ProxyServer<*>>()
        val diagnostics = mutableListOf<String>()
        val pendingProviders = mutableListOf<DesktopMihomoProviderRequest>()
        val proxyNodes = document.proxyNodes + document.providers.flatMap(MihomoProxyProvider::proxyNodes)
        val inlinePayloadTexts = document.inlinePayloadTexts + document.providers.flatMap(MihomoProxyProvider::inlinePayloadTexts)
        var proxyEntryCount = proxyNodes.size
        var rejectedProxyCount = 0

        proxyNodes.forEachIndexed { index, node ->
            runCatching { node.toDesktopProxyServer() }
                .onSuccess(servers::add)
                .onFailure { error ->
                    rejectedProxyCount += 1
                    diagnostics += node.nodeDiagnostic(index, error.message.orEmpty())
                }
        }

        inlinePayloadTexts.forEach { inlinePayload ->
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

        document.providers.forEach { provider ->
            val request = provider.toDesktopProviderRequestOrNull() ?: return@forEach
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
        val jsonResult = parseSubscriptionJsonConfigs(text)
        if (jsonResult != null && (jsonResult.servers.isNotEmpty() || jsonResult.proxyEntryCount > 0)) {
            return jsonResult
        }
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

    private fun parseSubscriptionJsonConfigs(text: String): InternalMihomoImportResult? {
        val imported = parseCustomXrayConfigPayload(text) as? CustomXrayConfigImportResult.Imported
            ?: return null

        return InternalMihomoImportResult(
            recognizedYaml = false,
            proxyEntryCount = imported.configCount,
            servers = imported.servers,
            rejectedProxyCount = imported.rejectedConfigCount,
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

private fun MihomoYamlMap.toDesktopProxyServer(): ProxyServer<*> {
    val type = string("type").orEmpty()
    require(type.isDesktopSupportedMihomoProxyType()) { "unsupported proxy type '$type'" }
    return toMihomoProxyServer()
}

/** The current desktop Core ABI has no native AmneziaWG tunnel lifecycle. */
private fun String.isDesktopSupportedMihomoProxyType(): Boolean =
    isSupportedMihomoProxyType() &&
        !equals("amneziawg", ignoreCase = true) &&
        !equals("awg", ignoreCase = true)

/** Converts the desktop parser's private YAML tree into the shared import contract. */
private fun DesktopYamlMap.toSharedMihomoYamlMap(): MihomoYamlMap = entries.associate { (key, value) ->
    key to value.toSharedMihomoYamlValue()
}

private fun DesktopYamlValue.toSharedMihomoYamlValue(): Any? = when (this) {
    is DesktopYamlValue.Scalar -> value
    is DesktopYamlValue.Mapping -> values.toSharedMihomoYamlMap()
    is DesktopYamlValue.Sequence -> values.map(DesktopYamlValue::toSharedMihomoYamlValue)
}

private fun MihomoYamlMap.nodeDiagnostic(index: Int, reason: String): String {
    val type = string("type").orEmpty().ifBlank { "unknown" }
    val name = string("name").orEmpty().ifBlank { "#${index + 1}" }
    return "Proxy '$name' ($type) skipped: ${reason.ifBlank { "invalid configuration" }}"
}

private fun isHttpProviderUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

private fun MihomoProxyProvider.toDesktopProviderRequestOrNull(): DesktopMihomoProviderRequest? {
    val providerUrl = url?.takeIf(::isHttpProviderUrl) ?: return null
    return if (type.isBlank() || type == "http") {
        DesktopMihomoProviderRequest(name = name, url = providerUrl)
    } else {
        null
    }
}

private typealias DesktopYamlMap = Map<String, DesktopYamlValue>

private sealed interface DesktopYamlValue {
    data class Scalar(val value: String) : DesktopYamlValue
    data class Mapping(val values: LinkedHashMap<String, DesktopYamlValue>) : DesktopYamlValue
    data class Sequence(val values: MutableList<DesktopYamlValue>) : DesktopYamlValue
}

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
