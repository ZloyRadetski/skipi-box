// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.usecase.ProxyServerProviderUrlFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import utils.encodeBase64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopProxyImportLiveProviderTest {
    @Test
    fun liveFileImportDecodesBase64RootAndProviderPayloads() = runBlocking {
        val providerUrl = "https://provider.example.com/file.yaml"
        val requestedUrls = mutableListOf<String>()
        val rootYaml = """
            proxies:
              - name: Root node
                type: socks
                server: root.example.com
                port: 1080
            proxy-providers:
              remote:
                type: http
                url: $providerUrl
        """.trimIndent()

        val plan = DesktopProxyImportPlanner.planWithProviderFetcher(
            input = DesktopProxyImportInput.File("servers.yaml", rootYaml.encodeBase64()),
            providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                requestedUrls += url
                assertEquals(providerUrl, url)
                socksProxyPayload("Remote node", "remote.example.com").encodeBase64()
            },
        )

        assertEquals(DesktopProxyImportSource.File, plan.source)
        assertEquals(listOf(providerUrl), requestedUrls)
        assertEquals(listOf("Root node", "Remote node"), plan.servers.map { it.getInfo().remarks })
        assertTrue(plan.diagnostics.none { it.code == DesktopProxyImportDiagnosticCode.PendingMihomoProvider })
    }

    @Test
    fun liveClipboardImportFetchesProvidersFromRawYaml() = runBlocking {
        val providerUrl = "https://provider.example.com/clipboard.yaml"
        val requestedUrls = mutableListOf<String>()
        val rootYaml = providerRootYaml(providerUrl)

        val plan = DesktopProxyImportPlanner.planWithProviderFetcher(
            input = DesktopProxyImportInput.Clipboard(rootYaml),
            providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                requestedUrls += url
                assertEquals(providerUrl, url)
                socksProxyPayload("Clipboard node", "clipboard.example.com")
            },
        )

        assertEquals(DesktopProxyImportSource.Clipboard, plan.source)
        assertEquals(listOf(providerUrl), requestedUrls)
        assertEquals(listOf("Clipboard node"), plan.servers.map { it.getInfo().remarks })
    }

    @Test
    fun livePlannerFetchesRepeatedProviderUrlsIndependentlyInOrder() = runBlocking {
        val providerUrl = "https://provider.example.com/repeated.yaml"
        val requestedUrls = mutableListOf<String>()
        val responses = listOf(
            socksProxyPayload("First response", "first.example.com"),
            socksProxyPayload("Second response", "second.example.com"),
        )
        var responseIndex = 0
        val rootYaml = """
            proxy-providers:
              first:
                type: http
                url: $providerUrl
              second:
                type: http
                url: $providerUrl
        """.trimIndent()

        val plan = DesktopProxyImportPlanner.planWithProviderFetcher(
            input = DesktopProxyImportInput.File("servers.yaml", rootYaml),
            providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                requestedUrls += url
                responses[responseIndex++]
            },
        )

        assertEquals(listOf(providerUrl, providerUrl), requestedUrls)
        assertEquals(listOf("First response", "Second response"), plan.servers.map { it.getInfo().remarks })
    }

    @Test
    fun liveProviderFetchFailureKeepsTheInlineFallback() = runBlocking {
        val providerUrl = "https://provider.example.com/unavailable.yaml"
        val requestedUrls = mutableListOf<String>()
        val rootYaml = """
            proxy-providers:
              remote:
                type: http
                url: $providerUrl
                payload:
                  - name: Inline fallback
                    type: socks
                    server: inline.example.com
                    port: 1080
        """.trimIndent()

        val plan = DesktopProxyImportPlanner.planWithProviderFetcher(
            input = DesktopProxyImportInput.File("servers.yaml", rootYaml),
            providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                requestedUrls += url
                error("Provider unavailable")
            },
        )

        assertEquals(listOf(providerUrl), requestedUrls)
        assertEquals(listOf("Inline fallback"), plan.servers.map { it.getInfo().remarks })
    }

    @Test
    fun cancellingLivePlannerDuringProviderFetchDoesNotReachCommit() = runBlocking {
        val providerUrl = "https://provider.example.com/waiting.yaml"
        val fetchStarted = CompletableDeferred<Unit>()
        val providerResponse = CompletableDeferred<String>()
        var reachedCommit = false
        val rootYaml = """
            proxy-providers:
              remote:
                type: http
                url: $providerUrl
                payload:
                  - name: Inline fallback after cancellation
                    type: socks
                    server: inline.example.com
                    port: 1080
        """.trimIndent()

        val plannerJob = launch {
            DesktopProxyImportPlanner.planWithProviderFetcher(
                input = DesktopProxyImportInput.File("servers.yaml", rootYaml),
                providerUrlFetcher = ProxyServerProviderUrlFetcher {
                    fetchStarted.complete(Unit)
                    providerResponse.await()
                },
            )
            reachedCommit = true
        }

        fetchStarted.await()
        plannerJob.cancelAndJoin()

        assertFalse(reachedCommit)
    }

    @Test
    fun livePlannerKeepsShadowrocketProfileWithProxyUrlWithoutFetchingProviders() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val raw = """
            [General]
            ipv6 = false

            [Proxy]
            # Example server: vless://123e4567-e89b-42d3-a456-426614174001@url.example.com:443?security=tls#URL

            [Rule]
            FINAL,PROXY
        """.trimIndent()

        val plan = DesktopProxyImportPlanner.planWithProviderFetcher(
            input = DesktopProxyImportInput.File("profile.conf", raw),
            providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                requestedUrls += url
                error("A Shadowrocket profile must not fetch providers")
            },
        )

        val config = assertIs<DesktopProxyImportAction.AddConfig>(plan.actions.single())
        assertEquals("$raw\n", config.content)
        assertTrue(plan.servers.isEmpty())
        assertTrue(requestedUrls.isEmpty())
    }
}

private fun providerRootYaml(providerUrl: String): String = """
    proxy-providers:
      remote:
        type: http
        url: $providerUrl
""".trimIndent()

private fun socksProxyPayload(name: String, host: String): String = """
    proxies:
      - name: $name
        type: socks
        server: $host
        port: 1080
""".trimIndent()
