// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

/**
 * Platform-neutral shape of a decoded Mihomo/Clash document.
 *
 * YAML parsing, provider fetching and user-facing diagnostics belong to the
 * caller. This layer only extracts proxy nodes and provider declarations from
 * an already-decoded object tree, so Android and Desktop apply the same
 * document semantics.
 */
data class MihomoProxyDocument(
    val proxyNodes: List<MihomoYamlMap>,
    val inlinePayloadTexts: List<String>,
    val providers: List<MihomoProxyProvider>,
    val recognized: Boolean,
)

/** One `proxy-providers` entry, with its inline payload kept separate from I/O. */
data class MihomoProxyProvider(
    val name: String,
    val type: String,
    val url: String?,
    val proxyNodes: List<MihomoYamlMap>,
    val inlinePayloadTexts: List<String>,
)

/** Extracts a Mihomo document from the object tree emitted by a platform YAML parser. */
fun Any?.toMihomoProxyDocument(): MihomoProxyDocument {
    val rootMap = asStringMap()
    if (rootMap == null) {
        val nodes = asList().orEmpty().mapNotNull { item -> item.asStringMap() }
        return MihomoProxyDocument(
            proxyNodes = nodes,
            inlinePayloadTexts = emptyList(),
            providers = emptyList(),
            recognized = nodes.isNotEmpty(),
        )
    }

    val proxyNodes = mutableListOf<MihomoYamlMap>()
    val inlinePayloadTexts = mutableListOf<String>()
    val providers = mutableListOf<MihomoProxyProvider>()
    var recognized = false

    fun collectPayload(value: Any?) {
        proxyNodes += value.mihomoProxyNodes()
        inlinePayloadTexts += value.mihomoInlinePayloadTexts()
    }

    if (rootMap.containsKey("proxies")) {
        recognized = true
        collectPayload(rootMap["proxies"])
    }
    if (rootMap.containsKey("payload")) {
        recognized = true
        collectPayload(rootMap["payload"])
    }
    rootMap.map("proxy-providers")?.let { providerDefinitions ->
        recognized = true
        providerDefinitions.forEach { (name, rawProvider) ->
            val provider = rawProvider.asStringMap() ?: return@forEach
            val payload = provider["payload"]
            providers += MihomoProxyProvider(
                name = name,
                type = provider.string("type").orEmpty().lowercase(),
                url = provider.string("url"),
                proxyNodes = payload.mihomoProxyNodes(),
                inlinePayloadTexts = payload.mihomoInlinePayloadTexts(),
            )
        }
    }
    if (!recognized && !rootMap.string("type").isNullOrBlank()) {
        recognized = true
        proxyNodes += rootMap
    }

    return MihomoProxyDocument(
        proxyNodes = proxyNodes,
        inlinePayloadTexts = inlinePayloadTexts,
        providers = providers,
        recognized = recognized,
    )
}

private fun Any?.mihomoProxyNodes(): List<MihomoYamlMap> = when (this) {
    is Map<*, *> -> asStringMap()?.let { map ->
        if (!map.string("type").isNullOrBlank()) listOf(map)
        else map.values.mapNotNull { value -> value.asStringMap() }
    }.orEmpty()

    is Iterable<*> -> mapNotNull { item -> item.asStringMap() }
    else -> emptyList()
}

private fun Any?.mihomoInlinePayloadTexts(): List<String> = when (this) {
    is String -> takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
    is Iterable<*> -> mapNotNull { item -> item.scalarString() }
        .takeIf(List<String>::isNotEmpty)
        ?.let { values -> listOf(values.joinToString("\n")) }
        .orEmpty()

    else -> emptyList()
}
