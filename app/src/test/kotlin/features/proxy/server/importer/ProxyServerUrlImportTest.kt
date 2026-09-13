// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.importer

import features.proxy.server.model.OlcRtc
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.importer.parseProxyServersFromUrls
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProxyServerUrlImportTest {

    private val context = ProxyServerImportContext(
        source = ProxyServerImportSource.Clipboard,
    )

    @Test
    fun importOlcRtcUrlWithAngleBracketsPayload() = runTest {
        val link = "olcrtc://telemost?vp8channel<vp8-fps=25&vp8-batch=1>@56026201482837#30330bd1da1c7ad6e7d518e423662b3bb2b53ca1bbb65612263a494610fa73e4\$olcRTC TELEMOST"
        val result = parseProxyServersFromUrls(link, context)

        assertEquals(1, result.servers.size)
        val server = assertIs<OlcRtc>(result.servers.first())
        assertEquals("telemost", server.provider)
        assertEquals("vp8channel", server.transport)
        assertEquals("56026201482837", server.roomUrl)
        assertEquals("30330bd1da1c7ad6e7d518e423662b3bb2b53ca1bbb65612263a494610fa73e4", server.encryptionKey)
        assertEquals("olcRTC TELEMOST", server.remarks)
        assertEquals(25, server.vp8Fps)
        assertEquals(1, server.vp8Batch)
    }

    @Test
    fun importOlcRtcUrlEmbeddedInText() = runTest {
        val text = """
            Here is your proxy link:
            olcrtc://telemost?vp8channel<vp8-fps=30&vp8-batch=64>@room123#30330bd1da1c7ad6e7d518e423662b3bb2b53ca1bbb65612263a494610fa73e4${'$'}EmbeddedTest
            Please connect soon!
        """.trimIndent()

        val result = parseProxyServersFromUrls(text, context)
        assertEquals(1, result.servers.size)
        val server = assertIs<OlcRtc>(result.servers.first())
        assertEquals("telemost", server.provider)
        assertEquals("vp8channel", server.transport)
        assertEquals("room123", server.roomUrl)
        assertEquals("EmbeddedTest", server.remarks)
    }

    @Test
    fun unsafeAmneziaWgUrlIsRejectedBeforeItCanReachTheNativeRunner() = runTest {
        val link = "awg://aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=@198.51.100.2:51820?publickey=YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=&jc=129&jmin=40&jmax=70&s1=15&s2=30"

        val result = parseProxyServersFromUrls(link, context)

        assertEquals(0, result.servers.size)
    }
}
