// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MihomoProxyDocumentTest {
    @Test
    fun extractsRootAndProviderPayloadsWithoutPlatformYamlDependencies() {
        val document = mapOf(
            "proxies" to listOf(
                mapOf("type" to "ss", "name" to "root"),
            ),
            "payload" to "vless://example",
            "proxy-providers" to mapOf(
                "inline" to mapOf(
                    "type" to "inline",
                    "payload" to listOf(mapOf("type" to "trojan", "name" to "inline node")),
                ),
                "remote" to mapOf(
                    "type" to "http",
                    "url" to "https://provider.example.com/nodes.yaml",
                ),
            ),
        ).toMihomoProxyDocument()

        assertTrue(document.recognized)
        assertEquals(listOf("root"), document.proxyNodes.map { node -> node.string("name") })
        assertEquals(listOf("vless://example"), document.inlinePayloadTexts)
        assertEquals(listOf("inline", "remote"), document.providers.map(MihomoProxyProvider::name))
        assertEquals(listOf("inline node"), document.providers.first().proxyNodes.map { node -> node.string("name") })
        assertEquals("https://provider.example.com/nodes.yaml", document.providers.last().url)
    }

    @Test
    fun recognizesAStandaloneProxyMap() {
        val document = mapOf(
            "type" to "socks5",
            "name" to "standalone",
            "server" to "proxy.example.com",
            "port" to 1080,
        ).toMihomoProxyDocument()

        assertTrue(document.recognized)
        assertEquals(1, document.proxyNodes.size)
        assertEquals("standalone", document.proxyNodes.single().string("name"))
    }
}
