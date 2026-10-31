// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerProviderUrlFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import utils.encodeBase64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMihomoLiveProviderTest {
    @Test
    fun fetchesRepeatedSiblingUrlsIndependentlyAndUsesEachResponseInOrder() {
        val providerUrl = "https://provider.example.com/shared.yaml"
        val requestedUrls = mutableListOf<String>()
        val responses = listOf(
            socksProxyPayload("First response", "first.example.com"),
            socksProxyPayload("Second response", "second.example.com"),
        )
        var responseIndex = 0

        val result: DesktopMihomoPayloadImportResult = runBlocking {
            DesktopMihomoPayloadImporter.importWithProviderFetcher(
                text = """
                    proxy-providers:
                      first:
                        type: http
                        url: $providerUrl
                      second:
                        type: http
                        url: $providerUrl
                """.trimIndent(),
                providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                    requestedUrls += url
                    responses[responseIndex++]
                },
            )
        }

        assertEquals(listOf(providerUrl, providerUrl), requestedUrls)
        assertEquals(listOf("First response", "Second response"), result.servers.map { it.getInfo().remarks })
        assertTrue(result.pendingProviders.isEmpty())
    }

    @Test
    fun nonemptyRemoteBodyReplacesTheProviderInlineFallback() {
        val providerUrl = "https://provider.example.com/remote.yaml"
        val requestedUrls = mutableListOf<String>()

        val result: DesktopMihomoPayloadImportResult = runBlocking {
            DesktopMihomoPayloadImporter.importWithProviderFetcher(
                text = providerWithInlineFallback(
                    name = "remote",
                    url = providerUrl,
                    inlineName = "Inline fallback",
                    inlineHost = "inline.example.com",
                ),
                providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                    requestedUrls += url
                    socksProxyPayload("Remote node", "remote.example.com")
                },
            )
        }

        assertEquals(listOf(providerUrl), requestedUrls)
        assertEquals(listOf("Remote node"), result.servers.map { it.getInfo().remarks })
        assertTrue(result.pendingProviders.isEmpty())
    }

    @Test
    fun fetchFailureAndCancellationBothFallBackToInlineProviderPayloads() {
        val failedUrl = "https://provider.example.com/fails.yaml"
        val cancelledUrl = "https://provider.example.com/cancelled.yaml"
        val requestedUrls = mutableListOf<String>()

        val result: DesktopMihomoPayloadImportResult = runBlocking {
            DesktopMihomoPayloadImporter.importWithProviderFetcher(
                text = """
                    proxy-providers:
                      failed:
                        type: http
                        url: $failedUrl
                        payload:
                          - name: Failure fallback
                            type: socks
                            server: failure-inline.example.com
                            port: 1080
                      cancelled:
                        type: http
                        url: $cancelledUrl
                        payload:
                          - name: Cancellation fallback
                            type: socks
                            server: cancellation-inline.example.com
                            port: 1080
                """.trimIndent(),
                providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                    requestedUrls += url
                    when (url) {
                        failedUrl -> error("provider unavailable")
                        cancelledUrl -> throw CancellationException("provider fetch cancelled")
                        else -> error("Unexpected provider URL")
                    }
                },
            )
        }

        assertEquals(listOf(failedUrl, cancelledUrl), requestedUrls)
        assertEquals(
            listOf("Failure fallback", "Cancellation fallback"),
            result.servers.map { it.getInfo().remarks },
        )
        assertTrue(result.pendingProviders.isEmpty())
    }

    @Test
    fun literalAncestorUrlUsesTheNestedInlineFallbackWithoutRefetching() {
        val providerUrl = "https://provider.example.com/ancestor.yaml"
        val requestedUrls = mutableListOf<String>()

        val result: DesktopMihomoPayloadImportResult = runBlocking {
            DesktopMihomoPayloadImporter.importWithProviderFetcher(
                text = providerWithInlineFallback(
                    name = "outer",
                    url = providerUrl,
                    inlineName = "Outer inline fallback",
                    inlineHost = "outer-inline.example.com",
                ),
                providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                    requestedUrls += url
                    """
                        proxy-providers:
                          recursive:
                            type: http
                            url: $providerUrl
                            payload:
                              - name: Cycle inline fallback
                                type: socks
                                server: cycle-inline.example.com
                                port: 1080
                    """.trimIndent()
                },
            )
        }

        assertEquals(listOf(providerUrl), requestedUrls)
        assertEquals(listOf("Cycle inline fallback"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun fetchesBoundaryProviderBeforeIgnoringItsBodyAtTheDepthLimit() {
        val rootProviderUrl = "https://provider.example.com/level-one.yaml"
        val boundaryProviderUrl = "https://provider.example.com/level-two.yaml"
        val requestedUrls = mutableListOf<String>()

        val result: DesktopMihomoPayloadImportResult = runBlocking {
            DesktopMihomoPayloadImporter.importWithProviderFetcher(
                text = providerWithInlineFallback(
                    name = "outer",
                    url = rootProviderUrl,
                    inlineName = "Outer fallback",
                    inlineHost = "outer-inline.example.com",
                ),
                maxProviderDepth = 1,
                providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                    requestedUrls += url
                    when (url) {
                        rootProviderUrl -> """
                            proxy-providers:
                              boundary:
                                type: http
                                url: $boundaryProviderUrl
                                payload:
                                  - name: Boundary inline fallback
                                    type: socks
                                    server: boundary-inline.example.com
                                    port: 1080
                        """.trimIndent()

                        boundaryProviderUrl -> socksProxyPayload("Depth-limited remote node", "ignored.example.com")
                        else -> error("Unexpected provider URL")
                    }
                },
            )
        }

        assertEquals(listOf(rootProviderUrl, boundaryProviderUrl), requestedUrls)
        assertEquals(listOf("Boundary inline fallback"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun preservesRootThenProviderOrderAndCountsAllEntries() {
        val firstUrl = "https://provider.example.com/first.yaml"
        val secondUrl = "https://provider.example.com/second.yaml"
        val requestedUrls = mutableListOf<String>()

        val result: DesktopMihomoPayloadImportResult = runBlocking {
            DesktopMihomoPayloadImporter.importWithProviderFetcher(
                text = """
                    proxies:
                      - name: Root node
                        type: socks
                        server: root.example.com
                        port: 1080
                    proxy-providers:
                      first:
                        type: http
                        url: $firstUrl
                      second:
                        type: http
                        url: $secondUrl
                """.trimIndent(),
                providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                    requestedUrls += url
                    when (url) {
                        firstUrl -> socksProxyPayload("First provider node", "first.example.com")
                        secondUrl -> """
                            proxies:
                              - name: Second provider node A
                                type: socks
                                server: second-a.example.com
                                port: 1080
                              - name: Second provider node B
                                type: socks
                                server: second-b.example.com
                                port: 1080
                        """.trimIndent()

                        else -> error("Unexpected provider URL")
                    }
                },
            )
        }

        assertEquals(listOf(firstUrl, secondUrl), requestedUrls)
        assertEquals(
            listOf("Root node", "First provider node", "Second provider node A", "Second provider node B"),
            result.servers.map { it.getInfo().remarks },
        )
        assertEquals(8, result.proxyEntryCount)
    }

    @Test
    fun decodesRootAndProviderBodiesUsingSharedImportSources() {
        val providerUrl = "https://provider.example.com/source.yaml"
        val rootYaml = """
            proxy-providers:
              remote:
                type: http
                url: $providerUrl
        """.trimIndent()

        val result: DesktopMihomoPayloadImportResult = runBlocking {
            DesktopMihomoPayloadImporter.importWithProviderFetcher(
                text = rootYaml.encodeBase64(),
                providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                    assertEquals(providerUrl, url)
                    socksProxyPayload("Source test node", "source.example.com").encodeBase64()
                },
                source = ProxyServerImportSource.File,
            )
        }

        assertEquals(listOf("Source test node"), result.servers.map { it.getInfo().remarks })
    }
}

private fun providerWithInlineFallback(
    name: String,
    url: String,
    inlineName: String,
    inlineHost: String,
): String = """
    proxy-providers:
      $name:
        type: http
        url: $url
        payload:
          - name: $inlineName
            type: socks
            server: $inlineHost
            port: 1080
""".trimIndent()

private fun socksProxyPayload(name: String, host: String): String = """
    proxies:
      - name: $name
        type: socks
        server: $host
        port: 1080
""".trimIndent()
