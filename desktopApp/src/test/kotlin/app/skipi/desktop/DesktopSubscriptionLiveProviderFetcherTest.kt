// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSubscriptionLiveProviderFetcherTest {
    @Test
    fun fetchAndImportFetchesRepeatedSiblingUrlsIndependently() {
        val providerUrl = "$PublicProviderHost/shared.yaml"
        val providerBodies = listOf(
            socksProxyPayload("First live response", "first.example.com"),
            socksProxyPayload("Second live response", "second.example.com"),
        )
        var providerBodyIndex = 0
        val harness = FakeSubscriptionTransport { url ->
            when (url.path) {
                "/subscription.yaml" -> StubHttpResponse(
                    body = """
                        proxy-providers:
                          first:
                            type: http
                            url: $providerUrl
                          second:
                            type: http
                            url: $providerUrl
                    """.trimIndent(),
                )

                "/shared.yaml" -> StubHttpResponse(body = providerBodies[providerBodyIndex++])
                else -> error("Unexpected URL ${url.path}")
            }
        }

        val update = harness.fetchAndImport()

        assertEquals(
            listOf("/subscription.yaml", "/shared.yaml", "/shared.yaml"),
            harness.requestedUrls.map { it.path },
        )
        assertEquals(
            listOf("First live response", "Second live response"),
            update.importResult.servers.map { it.getInfo().remarks },
        )
        assertEquals(6, update.importResult.urlCount)
        harness.assertAllRequestsUsedSocksProxy()
    }

    @Test
    fun fetchAndImportPreservesRootAndProviderDeclarationOrderAndEntryCount() {
        val firstProviderUrl = "$PublicProviderHost/first.yaml"
        val secondProviderUrl = "$PublicProviderHost/second.yaml"
        val harness = FakeSubscriptionTransport { url ->
            when (url.path) {
                "/subscription.yaml" -> StubHttpResponse(
                    body = """
                        proxies:
                          - name: Root node
                            type: socks
                            server: root.example.com
                            port: 1080
                        proxy-providers:
                          first:
                            type: http
                            url: $firstProviderUrl
                          second:
                            type: http
                            url: $secondProviderUrl
                    """.trimIndent(),
                )

                "/first.yaml" -> StubHttpResponse(
                    body = socksProxyPayload("First provider node", "first.example.com"),
                )

                "/second.yaml" -> StubHttpResponse(
                    body = """
                        proxies:
                          - name: Second provider node A
                            type: socks
                            server: second-a.example.com
                            port: 1080
                          - name: Second provider node B
                            type: socks
                            server: second-b.example.com
                            port: 1080
                    """.trimIndent(),
                )

                else -> error("Unexpected URL ${url.path}")
            }
        }

        val update = harness.fetchAndImport()

        assertEquals(
            listOf("/subscription.yaml", "/first.yaml", "/second.yaml"),
            harness.requestedUrls.map { it.path },
        )
        assertEquals(
            listOf("Root node", "First provider node", "Second provider node A", "Second provider node B"),
            update.importResult.servers.map { it.getInfo().remarks },
        )
        assertEquals(8, update.importResult.urlCount)
        harness.assertAllRequestsUsedSocksProxy()
    }

    @Test
    fun failedRemoteProviderKeepsItsInlineFallback() {
        val providerUrl = "$PublicProviderHost/unavailable.yaml"
        val harness = FakeSubscriptionTransport { url ->
            when (url.path) {
                "/subscription.yaml" -> StubHttpResponse(
                    body = """
                        proxy-providers:
                          remote:
                            type: http
                            url: $providerUrl
                            payload:
                              - name: Inline fallback after HTTP failure
                                type: socks
                                server: inline.example.com
                                port: 1080
                    """.trimIndent(),
                )

                "/unavailable.yaml" -> StubHttpResponse(statusCode = 503, body = "temporarily unavailable")
                else -> error("Unexpected URL ${url.path}")
            }
        }

        val update = harness.fetchAndImport()

        assertEquals(listOf("/subscription.yaml", "/unavailable.yaml"), harness.requestedUrls.map { it.path })
        assertEquals(
            listOf("Inline fallback after HTTP failure"),
            update.importResult.servers.map { it.getInfo().remarks },
        )
        assertTrue(update.importDiagnostics.any { "remote" in it })
        harness.assertAllRequestsUsedSocksProxy()
    }

    @Test
    fun fetchesBoundaryProviderThenUsesItsInlineFallbackWhenBodyIsBeyondDepthLimit() {
        val levelOneUrl = "$PublicProviderHost/level-one.yaml"
        val levelTwoUrl = "$PublicProviderHost/level-two.yaml"
        val boundaryUrl = "$PublicProviderHost/boundary.yaml"
        val harness = FakeSubscriptionTransport { url ->
            when (url.path) {
                "/subscription.yaml" -> StubHttpResponse(
                    body = """
                        proxy-providers:
                          level-one:
                            type: http
                            url: $levelOneUrl
                            payload:
                              - name: Root inline fallback
                                type: socks
                                server: root-inline.example.com
                                port: 1080
                    """.trimIndent(),
                )

                "/level-one.yaml" -> StubHttpResponse(
                    body = """
                        proxy-providers:
                          level-two:
                            type: http
                            url: $levelTwoUrl
                            payload:
                              - name: Level one inline fallback
                                type: socks
                                server: level-one-inline.example.com
                                port: 1080
                    """.trimIndent(),
                )

                "/level-two.yaml" -> StubHttpResponse(
                    body = """
                        proxy-providers:
                          boundary:
                            type: http
                            url: $boundaryUrl
                            payload:
                              - name: Boundary inline fallback
                                type: socks
                                server: boundary-inline.example.com
                                port: 1080
                    """.trimIndent(),
                )

                "/boundary.yaml" -> StubHttpResponse(
                    body = socksProxyPayload("Depth-limited remote node", "ignored.example.com"),
                )

                else -> error("Unexpected URL ${url.path}")
            }
        }

        val update = harness.fetchAndImport()

        assertEquals(
            listOf("/subscription.yaml", "/level-one.yaml", "/level-two.yaml", "/boundary.yaml"),
            harness.requestedUrls.map { it.path },
        )
        assertEquals(
            listOf("Boundary inline fallback"),
            update.importResult.servers.map { it.getInfo().remarks },
        )
        harness.assertAllRequestsUsedSocksProxy()
    }
}

private class FakeSubscriptionTransport(
    private val respond: (URL) -> StubHttpResponse,
) {
    val requestedUrls = mutableListOf<URL>()
    private val usedProxies = mutableListOf<Proxy?>()

    private val fetcher = DesktopSubscriptionFetcher(
        urlConnectionFactory = { url, proxy ->
            requestedUrls += url
            usedProxies += proxy
            StubHttpURLConnection(url, respond(url))
        },
    )

    fun fetchAndImport(): DesktopSubscriptionUpdate = fetcher.fetchAndImport(
        url = RootSubscriptionUrl,
        proxy = DesktopSubscriptionSocksProxy(host = "127.0.0.1", port = 10_808),
    )

    fun assertAllRequestsUsedSocksProxy() {
        assertEquals(requestedUrls.size, usedProxies.size)
        assertTrue(usedProxies.all { it?.type() == Proxy.Type.SOCKS })
        assertTrue(usedProxies.all { (it?.address() as? InetSocketAddress)?.port == 10_808 })
    }
}

private data class StubHttpResponse(
    val body: String,
    val statusCode: Int = 200,
)

private class StubHttpURLConnection(
    url: URL,
    private val response: StubHttpResponse,
) : HttpURLConnection(url) {
    private val bodyBytes = response.body.encodeToByteArray()

    override fun connect() = Unit

    override fun disconnect() = Unit

    override fun usingProxy(): Boolean = true

    override fun getResponseCode(): Int = response.statusCode

    override fun getInputStream(): InputStream = ByteArrayInputStream(bodyBytes)

    override fun getErrorStream(): InputStream? =
        if (response.statusCode in 200..299) null else ByteArrayInputStream(bodyBytes)

    override fun getContentLengthLong(): Long = bodyBytes.size.toLong()
}

private const val PublicProviderHost = "http://8.8.8.8"
private const val RootSubscriptionUrl = "$PublicProviderHost/subscription.yaml"

private fun socksProxyPayload(name: String, host: String): String = """
    proxies:
      - name: $name
        type: socks
        server: $host
        port: 1080
""".trimIndent()
