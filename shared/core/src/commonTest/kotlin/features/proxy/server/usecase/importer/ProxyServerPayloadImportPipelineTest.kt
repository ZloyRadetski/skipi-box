// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerPayloadParser
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import utils.encodeBase64

class ProxyServerPayloadImportPipelineTest {
    @Test
    fun triesDecodedBase64PayloadBeforeTheOriginalFilePayload() {
        val decoded = "decoded payload"
        val encoded = decoded.encodeToByteArray().encodeBase64()
        val seenPayloads = mutableListOf<String>()
        val parser: ProxyServerPayloadParser = { payload, _ ->
            seenPayloads += payload
            if (payload == decoded) ProxyServerImportResult(urlCount = 1, servers = emptyList())
            else EmptyProxyServerImportResult
        }

        val result = runSynchronously {
            importProxyServerPayloadText(
                text = encoded,
                context = ProxyServerImportContext(ProxyServerImportSource.File),
                parsers = listOf(parser),
            )
        }

        assertEquals(1, result.urlCount)
        assertEquals(listOf(decoded), seenPayloads)
    }

    @Test
    fun keepsParserAndPayloadOrderUntilOneRecognizesInput() {
        val calls = mutableListOf<String>()
        val first: ProxyServerPayloadParser = { payload, _ ->
            calls += "first:$payload"
            EmptyProxyServerImportResult
        }
        val second: ProxyServerPayloadParser = { payload, _ ->
            calls += "second:$payload"
            if (payload == "second payload") ProxyServerImportResult(urlCount = 2, servers = emptyList())
            else EmptyProxyServerImportResult
        }

        val result = runSynchronously {
            parseProxyServerPayloads(
                payloads = listOf("first payload", "second payload"),
                context = ProxyServerImportContext(ProxyServerImportSource.Clipboard),
                parsers = listOf(first, second),
            )
        }

        assertEquals(2, result.urlCount)
        assertEquals(
            listOf(
                "first:first payload",
                "second:first payload",
                "first:second payload",
                "second:second payload",
            ),
            calls,
        )
    }

    @Test
    fun providerPayloadCarriesChildContextAndHonorsDepthLimit() {
        var childContext: ProxyServerImportContext? = null
        val parser: ProxyServerPayloadParser = { _, context ->
            childContext = context
            ProxyServerImportResult(urlCount = 1, servers = emptyList())
        }
        val parent = ProxyServerImportContext(ProxyServerImportSource.SubscriptionUrl)

        val result = runSynchronously {
            importProxyServersFromProviderPayload(
                text = "provider body",
                parentContext = parent,
                providerUrl = "https://provider.example/list",
                parsers = listOf(parser),
            )
        }

        assertEquals(1, result.urlCount)
        assertEquals(ProxyServerImportSource.MihomoProxyProviderUrl, childContext?.source)
        assertEquals(1, childContext?.providerDepth)
        assertEquals(setOf("https://provider.example/list"), childContext?.fetchedProviderUrls)

        val blocked = runSynchronously {
            importProxyServersFromProviderPayload(
                text = "unreachable",
                parentContext = parent.copy(providerDepth = DefaultMihomoProviderMaxDepth),
                providerUrl = "https://provider.example/nested",
                parsers = listOf(parser),
            )
        }
        assertEquals(0, blocked.urlCount)
        assertTrue(blocked.servers.isEmpty())
    }
}

private fun <T> runSynchronously(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        },
    )
    return checkNotNull(completed).getOrThrow()
}
