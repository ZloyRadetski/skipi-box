// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerProviderUrlFetcher
import features.proxy.server.usecase.importer.importProxyServerPayloadText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshParserTest {
    @Test
    fun subscriptionParserFetchesMihomoProviderThroughSharedImportContext() = runBlocking {
        val providerUrl = "https://provider.example.com/list.yaml"
        val requestedUrls = mutableListOf<String>()

        val imported = importProxyServerPayloadText(
            text = """
                proxy-providers:
                  remote:
                    type: http
                    url: $providerUrl
                    payload:
                      - name: Inline fallback
                        type: socks
                        server: inline.example.com
                        port: 1080
            """.trimIndent(),
            context = ProxyServerImportContext(
                source = ProxyServerImportSource.SubscriptionUrl,
                providerUrlFetcher = ProxyServerProviderUrlFetcher { url ->
                    requestedUrls += url
                    """
                        proxies:
                          - name: Remote provider
                            type: socks
                            server: provider.example.com
                            port: 1080
                    """.trimIndent()
                },
            ),
            parsers = listOf(DesktopMihomoPayloadImporter.subscriptionPayloadParser()),
        )

        assertEquals(listOf(providerUrl), requestedUrls)
        assertEquals(1, imported.servers.size)
        assertEquals("Remote provider", imported.servers.single().getInfo().remarks)
        assertTrue(imported.urlCount > 0)
    }
}
