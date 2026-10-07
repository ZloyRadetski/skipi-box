// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.importer

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Socks
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.importProxyServersFromText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import utils.encodeBase64
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProxyServerMihomoProviderImportTest {
    @Test
    fun remoteProviderServersReplaceItsInlineNodes() = runTest {
        val fetchedUrls = mutableListOf<String>()
        val result = importProxyServersFromText(
            text = httpProviderDocument(
                providerUrl = ProviderA,
                inlineName = "Inline fallback",
                inlineServer = "inline.example.test",
            ),
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { url ->
                fetchedUrls += url
                socksProxy("Remote node", "remote.example.test")
            },
        )

        assertEquals(listOf(ProviderA), fetchedUrls)
        assertEquals(3, result.urlCount)
        assertEquals(listOf("Remote node"), result.servers.map { it.getInfo().remarks })
        assertEquals("remote.example.test", assertIs<Socks>(result.servers.single()).server)
    }

    @Test
    fun failedProviderFetchFallsBackToInlineNodes() = runTest {
        val result = importProxyServersFromText(
            text = httpProviderDocument(
                providerUrl = ProviderA,
                inlineName = "Inline after failure",
                inlineServer = "inline-failure.example.test",
            ),
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { throw IllegalStateException("provider unavailable") },
        )

        assertEquals(2, result.urlCount)
        assertEquals(listOf("Inline after failure"), result.servers.map { it.getInfo().remarks })
        assertEquals("inline-failure.example.test", assertIs<Socks>(result.servers.single()).server)
    }

    @Test
    fun emptyProviderDocumentFallsBackToInlineNodes() = runTest {
        val result = importProxyServersFromText(
            text = httpProviderDocument(
                providerUrl = ProviderA,
                inlineName = "Inline after empty response",
                inlineServer = "inline-empty.example.test",
            ),
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { "proxies: []" },
        )

        assertEquals(2, result.urlCount)
        assertEquals(listOf("Inline after empty response"), result.servers.map { it.getInfo().remarks })
        assertEquals("inline-empty.example.test", assertIs<Socks>(result.servers.single()).server)
    }

    @Test
    fun literalAncestorUrlCycleUsesNestedInlineNodesWithoutRefetching() = runTest {
        val fetchedUrls = mutableListOf<String>()
        val result = importProxyServersFromText(
            text = httpProviderDocument(
                providerUrl = ProviderA,
                inlineName = "Outer inline fallback",
                inlineServer = "outer-inline.example.test",
            ),
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { url ->
                fetchedUrls += url
                httpProviderDocument(
                    providerUrl = ProviderA,
                    inlineName = "Cycle inline fallback",
                    inlineServer = "cycle-inline.example.test",
                )
            },
        )

        assertEquals(listOf(ProviderA), fetchedUrls)
        assertEquals(4, result.urlCount)
        assertEquals(listOf("Cycle inline fallback"), result.servers.map { it.getInfo().remarks })
        assertEquals("cycle-inline.example.test", assertIs<Socks>(result.servers.single()).server)
    }

    @Test
    fun duplicateNodesFromRootAndRemoteProviderArePreserved() = runTest {
        val result = importProxyServersFromText(
            text = """
                proxies:
                  - name: Root duplicate one
                    type: socks
                    server: duplicate.example.test
                    port: 1080
                  - name: Root duplicate two
                    type: socks
                    server: duplicate.example.test
                    port: 1080
                proxy-providers:
                  remote:
                    type: http
                    url: $ProviderA
                    payload:
                      - name: Provider inline fallback
                        type: socks
                        server: inline.example.test
                        port: 1080
            """.trimIndent(),
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = {
                socksProxy("Remote duplicate", "duplicate.example.test")
            },
        )

        assertEquals(5, result.urlCount)
        assertEquals(
            listOf("Root duplicate one", "Root duplicate two", "Remote duplicate"),
            result.servers.map { it.getInfo().remarks },
        )
        assertTrue(result.servers.all { it is Socks })
    }

    @Test
    fun providerAtDepthLimitIsFetchedBeforeItsBodyIsBlocked() = runTest {
        val fetchedUrls = mutableListOf<String>()
        val result = importProxyServersFromText(
            text = httpProviderDocument(
                providerUrl = ProviderA,
                inlineName = "Root inline fallback",
                inlineServer = "root-inline.example.test",
            ),
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { url ->
                fetchedUrls += url
                when (url) {
                    ProviderA -> httpProviderDocument(
                        providerUrl = ProviderB,
                        inlineName = "A inline fallback",
                        inlineServer = "a-inline.example.test",
                    )

                    ProviderB -> httpProviderDocument(
                        providerUrl = ProviderC,
                        inlineName = "B inline fallback",
                        inlineServer = "b-inline.example.test",
                    )

                    ProviderC -> socksProxy("Too deep to parse", "too-deep.example.test")
                    else -> error("Unexpected provider URL")
                }
            },
        )

        assertEquals(listOf(ProviderA, ProviderB, ProviderC), fetchedUrls)
        assertEquals(6, result.urlCount)
        assertEquals(listOf("B inline fallback"), result.servers.map { it.getInfo().remarks })
        assertEquals("b-inline.example.test", assertIs<Socks>(result.servers.single()).server)
    }

    @Test
    fun cancellationExceptionFromProviderFetcherFallsBackToInlineNodes() = runTest {
        val result = importProxyServersFromText(
            text = httpProviderDocument(
                providerUrl = ProviderA,
                inlineName = "Inline after cancellation",
                inlineServer = "inline-cancel.example.test",
            ),
            source = ProxyServerImportSource.Clipboard,
            providerUrlFetcher = { throw CancellationException("provider fetch cancelled") },
        )

        assertEquals(2, result.urlCount)
        assertEquals(listOf("Inline after cancellation"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun onlySubscriptionSourcesDecodeBase64Payloads() = runTest {
        val yaml = socksProxy("Encoded node", "encoded.example.test")
        val encoded = yaml.encodeToByteArray().encodeBase64()

        val subscriptionResult = importProxyServersFromText(encoded, ProxyServerImportSource.SubscriptionUrl)
        val clipboardResult = importProxyServersFromText(encoded, ProxyServerImportSource.Clipboard)

        assertEquals(1, subscriptionResult.urlCount)
        assertEquals("Encoded node", assertIs<Socks>(subscriptionResult.servers.single()).getInfo().remarks)
        assertEquals(0, clipboardResult.urlCount)
        assertTrue(clipboardResult.servers.isEmpty())
    }

    @Test
    fun importsAmneziaWgYamlToPortableServerModel() = runTest {
        val yaml = """
            proxies:
              - name: Portable AWG node
                type: awg
                server: 192.0.2.10
                port: 51820
                private-key: aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
                public-key: YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
                ip: 10.0.0.2
        """.trimIndent()

        val result = importProxyServersFromText(yaml, ProxyServerImportSource.Clipboard)

        assertEquals(1, result.urlCount)
        val awg = assertIs<AmneziaWg>(result.servers.single())
        assertEquals("Portable AWG node", awg.getInfo().remarks)
        assertEquals("192.0.2.10", awg.server)
    }
}

private const val ProviderA = "https://provider.example.test/a.yaml"
private const val ProviderB = "https://provider.example.test/b.yaml"
private const val ProviderC = "https://provider.example.test/c.yaml"

private fun httpProviderDocument(
    providerUrl: String,
    inlineName: String,
    inlineServer: String,
): String = """
    proxy-providers:
      remote:
        type: http
        url: $providerUrl
        payload:
          - name: $inlineName
            type: socks
            server: $inlineServer
            port: 1080
""".trimIndent()

private fun socksProxy(name: String, server: String): String = """
    proxies:
      - name: $name
        type: socks
        server: $server
        port: 1080
""".trimIndent()
