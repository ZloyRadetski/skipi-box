// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.Socks
import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerPayloadParser
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MihomoYamlPayloadParserTest {
    @Test
    fun remoteProviderServersReplaceInlineNodesAndKeepAndroidCountsAndOrder() {
        val fetchedUrls = mutableListOf<String>()
        val childContexts = mutableListOf<ProxyServerImportContext>()
        val diagnostics = mutableListOf<MihomoYamlImportDiagnostic>()
        val providerUrl = "https://provider.example.test/a.yaml"
        val remoteServer = Socks("Remote node", "remote.example.test", "1080")
        val childParser: ProxyServerPayloadParser = { text, context ->
            childContexts += context
            assertEquals("provider body:", text)
            ProxyServerImportResult(urlCount = 1, servers = listOf(remoteServer))
        }
        val parser = parser(
            trees = mapOf(
                "root" to document(
                    proxies = listOf(socksNode("Root node", "root.example.test")),
                    providers = linkedMapOf(
                        "remote" to provider(
                            url = "  $providerUrl  ",
                            inlineNodes = listOf(socksNode("Inline fallback", "inline.example.test")),
                        ),
                    ),
                ),
            ),
            parseProviderPayload = { text, parent, url ->
                importProxyServersFromProviderPayload(text, parent, url, listOf(childParser))
            },
            diagnostics = diagnostics,
        )
        val context = ProxyServerImportContext(
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { url ->
                fetchedUrls += url
                "provider body:"
            },
        )

        val result = runSynchronously { parser("root", context) }

        assertEquals(listOf(providerUrl), fetchedUrls)
        assertEquals(4, result.urlCount)
        assertEquals(listOf("Root node", "Remote node"), result.servers.map { it.getInfo().remarks })
        assertEquals(1, childContexts.size)
        assertEquals(ProxyServerImportSource.MihomoProxyProviderUrl, childContexts.single().source)
        assertEquals(1, childContexts.single().providerDepth)
        assertEquals(setOf(providerUrl), childContexts.single().fetchedProviderUrls)
        assertTrue(diagnostics.isEmpty())
    }

    @Test
    fun emptyProviderResultFallsBackToInlineNodesAndDoesNotCountRemoteBody() {
        val providerUrl = "https://provider.example.test/empty.yaml"
        val parser = parser(
            trees = mapOf(
                "root" to document(
                    providers = linkedMapOf(
                        "remote" to provider(
                            url = providerUrl,
                            inlineNodes = listOf(socksNode("Inline fallback", "inline.example.test")),
                        ),
                    ),
                ),
            ),
            parseProviderPayload = { _, _, _ ->
                ProxyServerImportResult(urlCount = 3, servers = emptyList())
            },
        )
        val context = ProxyServerImportContext(
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { "empty provider body" },
        )

        val result = runSynchronously { parser("root", context) }

        assertEquals(2, result.urlCount)
        assertEquals(listOf("Inline fallback"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun invalidRecursiveAndUnavailableProviderUrlsUseInlineNodesInDeclarationOrder() {
        val diagnostics = mutableListOf<MihomoYamlImportDiagnostic>()
        val recursiveUrl = "https://provider.example.test/ancestor.yaml"
        val parser = parser(
            trees = mapOf(
                "root" to document(
                    providers = linkedMapOf(
                        "invalid" to provider(
                            url = "ftp://provider.example.test/nodes.yaml",
                            inlineNodes = listOf(socksNode("Invalid URL fallback", "invalid.example.test")),
                        ),
                        "recursive" to provider(
                            url = recursiveUrl,
                            inlineNodes = listOf(socksNode("Cycle fallback", "cycle.example.test")),
                        ),
                        "unavailable" to provider(
                            url = "https://provider.example.test/missing.yaml",
                            inlineNodes = listOf(socksNode("Missing fetcher fallback", "missing.example.test")),
                        ),
                    ),
                ),
            ),
            diagnostics = diagnostics,
        )
        val context = ProxyServerImportContext(
            source = ProxyServerImportSource.Clipboard,
            fetchedProviderUrls = setOf(recursiveUrl),
        )

        val result = runSynchronously { parser("root", context) }

        assertEquals(6, result.urlCount)
        assertEquals(
            listOf("Invalid URL fallback", "Cycle fallback", "Missing fetcher fallback"),
            result.servers.map { it.getInfo().remarks },
        )
        val providerDiagnostics = diagnostics.filterIsInstance<MihomoYamlImportDiagnostic.ProviderSkipped>()
        assertEquals(
            listOf(
                MihomoProviderSkipReason.InvalidUrl,
                MihomoProviderSkipReason.RecursiveUrl,
                MihomoProviderSkipReason.FetcherUnavailable,
            ),
            providerDiagnostics.map(MihomoYamlImportDiagnostic.ProviderSkipped::reason),
        )
        assertEquals(listOf("invalid", "recursive", "unavailable"), providerDiagnostics.map { it.providerName })
        assertTrue(providerDiagnostics.all { it.source == ProxyServerImportSource.Clipboard })
    }

    @Test
    fun failedAndCancelledProviderFetchesFallBackAndExposeSafeDiagnostics() {
        val providerA = "https://provider.example.test/a.yaml"
        val providerB = "https://provider.example.test/b.yaml"
        val diagnostics = mutableListOf<MihomoYamlImportDiagnostic>()
        val parser = parser(
            trees = mapOf(
                "root" to document(
                    providers = linkedMapOf(
                        "failed" to provider(
                            url = providerA,
                            inlineNodes = listOf(socksNode("Failed fallback", "failed.example.test")),
                        ),
                        "cancelled" to provider(
                            url = providerB,
                            inlineNodes = listOf(socksNode("Cancelled fallback", "cancelled.example.test")),
                        ),
                    ),
                ),
            ),
            providerLogHost = { "safe.provider.example.test" },
            diagnostics = diagnostics,
        )
        val failure = IllegalStateException("provider unavailable")
        val context = ProxyServerImportContext(
            source = ProxyServerImportSource.SubscriptionUrl,
            providerUrlFetcher = { url ->
                when (url) {
                    providerA -> throw failure
                    providerB -> throw CancellationException("cancelled")
                    else -> error("Unexpected provider URL")
                }
            },
        )

        val result = runSynchronously { parser("root", context) }

        assertEquals(4, result.urlCount)
        assertEquals(listOf("Failed fallback", "Cancelled fallback"), result.servers.map { it.getInfo().remarks })
        val providerDiagnostics = diagnostics.filterIsInstance<MihomoYamlImportDiagnostic.ProviderSkipped>()
        assertEquals(2, providerDiagnostics.size)
        assertTrue(providerDiagnostics.all { it.reason == MihomoProviderSkipReason.FetchFailed })
        assertTrue(providerDiagnostics.all { it.providerHost == "safe.provider.example.test" })
        assertSame(failure, providerDiagnostics.first().error)
        assertIs<CancellationException>(providerDiagnostics.last().error)
    }

    @Test
    fun siblingProvidersAreFetchedIndependentlyAndDuplicateServersArePreserved() {
        val providerUrl = "https://provider.example.test/shared.yaml"
        val fetchedUrls = mutableListOf<String>()
        val providerNode = Socks("Remote duplicate", "duplicate.example.test", "1080")
        val childParser: ProxyServerPayloadParser = { _, _ ->
            ProxyServerImportResult(urlCount = 1, servers = listOf(providerNode))
        }
        val parser = parser(
            trees = mapOf(
                "root" to document(
                    proxies = listOf(socksNode("Root duplicate", "duplicate.example.test")),
                    providers = linkedMapOf(
                        "first" to provider(url = providerUrl),
                        "second" to provider(url = providerUrl),
                    ),
                ),
            ),
            parseProviderPayload = { text, parent, url ->
                importProxyServersFromProviderPayload(text, parent, url, listOf(childParser))
            },
        )
        val context = ProxyServerImportContext(
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { url ->
                fetchedUrls += url
                "provider body:"
            },
        )

        val result = runSynchronously { parser("root", context) }

        assertEquals(listOf(providerUrl, providerUrl), fetchedUrls)
        assertEquals(7, result.urlCount)
        assertEquals(3, result.servers.size)
        assertEquals(
            listOf("Root duplicate", "Remote duplicate", "Remote duplicate"),
            result.servers.map { it.getInfo().remarks },
        )
    }

    @Test
    fun providerAtDepthLimitIsFetchedBeforeChildPipelineBlocksItsBody() {
        val providerUrl = "https://provider.example.test/too-deep.yaml"
        val fetchedUrls = mutableListOf<String>()
        var childParserCalls = 0
        val childParser: ProxyServerPayloadParser = { _, _ ->
            childParserCalls += 1
            ProxyServerImportResult(urlCount = 1, servers = listOf(Socks("Too deep", "deep.example.test", "1080")))
        }
        val parser = parser(
            trees = mapOf(
                "root" to document(
                    providers = linkedMapOf(
                        "deep" to provider(
                            url = providerUrl,
                            inlineNodes = listOf(socksNode("Depth fallback", "fallback.example.test")),
                        ),
                    ),
                ),
            ),
            parseProviderPayload = { text, parent, url ->
                importProxyServersFromProviderPayload(text, parent, url, listOf(childParser))
            },
        )
        val context = ProxyServerImportContext(
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { url ->
                fetchedUrls += url
                "too-deep body"
            },
            providerDepth = DefaultMihomoProviderMaxDepth,
        )

        val result = runSynchronously { parser("root", context) }

        assertEquals(listOf(providerUrl), fetchedUrls)
        assertEquals(0, childParserCalls)
        assertEquals(2, result.urlCount)
        assertEquals(listOf("Depth fallback"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun loadFailureReturnsEmptyAndForwardsTheOriginalError() {
        val error = IllegalArgumentException("malformed YAML")
        val diagnostics = mutableListOf<MihomoYamlImportDiagnostic>()
        val parser = parser(
            trees = emptyMap(),
            loadTree = { throw error },
            diagnostics = diagnostics,
        )

        val result = runSynchronously {
            parser("malformed", ProxyServerImportContext(ProxyServerImportSource.File))
        }

        assertEquals(EmptyProxyServerImportResult, result)
        val diagnostic = assertIs<MihomoYamlImportDiagnostic.YamlLoadFailed>(diagnostics.single())
        assertEquals(ProxyServerImportSource.File, diagnostic.source)
        assertSame(error, diagnostic.error)
    }

    @Test
    fun rejectedNodesAreReportedAndSummaryCountsOnlyTheCurrentDocument() {
        val diagnostics = mutableListOf<MihomoYamlImportDiagnostic>()
        val parser = parser(
            trees = mapOf(
                "root" to document(
                    proxies = listOf(
                        socksNode("Valid node", "valid.example.test"),
                        mapOf("type" to "tuic", "name" to "Unsupported node"),
                    ),
                ),
            ),
            diagnostics = diagnostics,
        )

        val result = runSynchronously {
            parser("root", ProxyServerImportContext(ProxyServerImportSource.QrCode))
        }

        assertEquals(2, result.urlCount)
        assertEquals(listOf("Valid node"), result.servers.map { it.getInfo().remarks })
        val nodeDiagnostic = assertIs<MihomoYamlImportDiagnostic.NodeRejected>(diagnostics[0])
        assertEquals(ProxyServerImportSource.QrCode, nodeDiagnostic.source)
        assertEquals("Unsupported node", nodeDiagnostic.failure.name)
        val summary = assertIs<MihomoYamlImportDiagnostic.Summary>(diagnostics[1])
        assertEquals(ProxyServerImportSource.QrCode, summary.source)
        assertEquals(1, summary.serverCount)
        assertEquals(1, summary.rejectedCount)
        assertEquals(2, summary.urlCount)
    }
}

private fun parser(
    trees: Map<String, Any?>,
    parseProviderPayload: suspend (
        text: String,
        parentContext: ProxyServerImportContext,
        providerUrl: String,
    ) -> ProxyServerImportResult = { _, _, _ -> EmptyProxyServerImportResult },
    providerLogHost: (String) -> String = { "<unknown>" },
    diagnostics: MutableList<MihomoYamlImportDiagnostic> = mutableListOf(),
    loadTree: (String) -> Any? = { text ->
        if (trees.containsKey(text)) trees[text] else error("Unexpected payload: $text")
    },
): ProxyServerPayloadParser = mihomoYamlPayloadParser(
    loadYamlTree = loadTree,
    normalizeProviderUrl = { rawUrl ->
        rawUrl?.trim()?.takeIf { value ->
            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)
        }
    },
    parseProviderPayload = parseProviderPayload,
    providerLogHost = providerLogHost,
    diagnosticSink = { diagnostic -> diagnostics += diagnostic },
)

private fun document(
    proxies: List<Map<String, Any?>> = emptyList(),
    providers: Map<String, Map<String, Any?>> = emptyMap(),
): Map<String, Any?> = buildMap {
    if (proxies.isNotEmpty()) put("proxies", proxies)
    if (providers.isNotEmpty()) put("proxy-providers", providers)
}

private fun provider(
    url: String,
    inlineNodes: List<Map<String, Any?>> = emptyList(),
): Map<String, Any?> = buildMap {
    put("type", "http")
    put("url", url)
    if (inlineNodes.isNotEmpty()) put("payload", inlineNodes)
}

private fun socksNode(name: String, server: String): Map<String, Any?> = mapOf(
    "name" to name,
    "type" to "socks",
    "server" to server,
    "port" to "1080",
)

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
    return checkNotNull(completed) { "Test operation suspended unexpectedly" }.getOrThrow()
}
