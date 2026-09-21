// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.OlcRtc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProxyServerUrlImportTest {
    @Test
    fun importsOlcRtcUrlWithAngleBracketsPayload() {
        val link = "olcrtc://telemost?vp8channel<vp8-fps=25&vp8-batch=1>@56026201482837#30330bd1da1c7ad6e7d518e423662b3bb2b53ca1bbb65612263a494610fa73e4\$olcRTC TELEMOST"

        val result = importProxyServersFromUrls(link)

        assertEquals(1, result.urlCount)
        assertEquals(1, result.servers.size)
        val server = assertIs<OlcRtc>(result.servers.single())
        assertEquals("telemost", server.provider)
        assertEquals("vp8channel", server.transport)
        assertEquals("56026201482837", server.roomUrl)
        assertEquals("olcRTC TELEMOST", server.remarks)
        assertEquals(25, server.vp8Fps)
        assertEquals(1, server.vp8Batch)
    }

    @Test
    fun importsUrlEmbeddedInTextAndRemovesOnlyTheClosingAngleBracket() {
        val text = """
            Here is your proxy link:
            Use this link: <olcrtc://telemost?vp8channel<vp8-fps=30&vp8-batch=64>@room123#30330bd1da1c7ad6e7d518e423662b3bb2b53ca1bbb65612263a494610fa73e4${'$'}EmbeddedTest>
            Please connect soon!
        """.trimIndent()

        val result = importProxyServersFromUrls(text)

        assertEquals(1, result.servers.size)
        val server = assertIs<OlcRtc>(result.servers.single())
        assertEquals("room123", server.roomUrl)
        assertEquals("EmbeddedTest", server.remarks)
    }

    @Test
    fun rejectsUnsafeAmneziaWgUrlAndReportsTheIndividualFailure() {
        val link = "awg://aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=@198.51.100.2:51820?publickey=YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=&jc=129&jmin=40&jmax=70&s1=15&s2=30"
        val failures = mutableListOf<ProxyServerUrlImportFailure>()

        val result = importProxyServersFromUrls(link, failures::add)

        assertEquals(1, result.urlCount)
        assertEquals(emptyList(), result.servers)
        assertEquals(listOf(link), failures.map(ProxyServerUrlImportFailure::url))
        assertEquals(listOf(0), failures.map(ProxyServerUrlImportFailure::index))
    }
}
