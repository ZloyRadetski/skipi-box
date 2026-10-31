// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.usecase.ProxyServerImportSource
import utils.encodeBase64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMihomoPayloadSourceTest {
    @Test
    fun defaultsToSubscriptionSourceAndDecodesBase64YamlBeforeRawParsing() {
        val encoded = socksYaml("Encoded subscription node").encodeToByteArray().encodeBase64()

        val defaultSource = DesktopMihomoPayloadImporter.import(encoded)
        val explicitSource = DesktopMihomoPayloadImporter.import(
            text = encoded,
            source = ProxyServerImportSource.SubscriptionUrl,
        )

        listOf(defaultSource, explicitSource).forEach { result ->
            assertTrue(result.recognizedYaml)
            assertEquals(1, result.proxyEntryCount)
            assertEquals(listOf("Encoded subscription node"), result.servers.map { it.getInfo().remarks })
        }
    }

    @Test
    fun fileSourceDecodesBase64YamlBeforeRawParsing() {
        val encoded = socksYaml("Encoded file node").encodeToByteArray().encodeBase64()

        val result = DesktopMihomoPayloadImporter.import(
            text = encoded,
            source = ProxyServerImportSource.File,
        )

        assertTrue(result.recognizedYaml)
        assertEquals(1, result.proxyEntryCount)
        assertEquals(listOf("Encoded file node"), result.servers.map { it.getInfo().remarks })
    }

    @Test
    fun clipboardSourceDoesNotDecodeBase64Yaml() {
        val encoded = socksYaml("Encoded clipboard node").encodeToByteArray().encodeBase64()

        val result = DesktopMihomoPayloadImporter.import(
            text = encoded,
            source = ProxyServerImportSource.Clipboard,
        )

        assertFalse(result.recognizedYaml)
        assertEquals(0, result.proxyEntryCount)
        assertTrue(result.servers.isEmpty())
    }
}

private fun socksYaml(name: String): String = """
    proxies:
      - name: $name
        type: socks
        server: source.example.com
        port: 1080
""".trimIndent()
