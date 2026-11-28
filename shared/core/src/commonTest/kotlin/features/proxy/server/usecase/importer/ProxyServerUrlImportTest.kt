// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.getTransportDisplay
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyServerUrlImportTest {
    @Test
    fun anOlcRtcLinkIsNoLongerImported() {
        val link = "olcrtc://telemost?vp8channel<vp8-fps=25&vp8-batch=1>@56026201482837#30330bd1da1c7ad6e7d518e423662b3bb2b53ca1bbb65612263a494610fa73e4\$olcRTC TELEMOST"

        val result = importProxyServersFromUrls(link)

        assertEquals(emptyList(), result.servers)
    }

    @Test
    fun aNaiveHttpsLineIsImported() {
        val result = importProxyServersFromUrls("naive+https://alice:pw@naive.example.com:443?padding=true#Naive")

        assertEquals(listOf("Naive"), result.servers.map { it.getInfo().remarks })
        assertEquals(listOf("Naive"), result.servers.map { it.getInfo().protocol })
    }

    @Test
    fun aNaiveQuicLineIsImportedAsQuic() {
        val result = importProxyServersFromUrls("naive+quic://alice:pw@naive.example.com:443#NaiveQ")

        assertEquals("HTTP/3", result.servers.single().getTransportDisplay())
    }

    @Test
    fun aNaiveLinkInsideProseIsImported() {
        val result = importProxyServersFromUrls("my server: naive+https://alice:pw@naive.example.com:443#Naive, thanks")

        assertEquals(listOf("Naive"), result.servers.map { it.getInfo().protocol })
    }

    @Test
    fun naiveLinksMixWithOtherLinksOnSeveralLines() {
        val result = importProxyServersFromUrls(
            """
            vless://123e4567-e89b-42d3-a456-426614174000@example.com:443?security=tls#One
            naive+https://alice:pw@naive.example.com:443#Two
            naive+quic://alice:pw@203.0.113.7:443#Three
            """.trimIndent(),
        )

        assertEquals(listOf("VLESS", "Naive", "Naive"), result.servers.map { it.getInfo().protocol })
        assertEquals(3, result.urlCount)
    }

    @Test
    fun aMasqueLineIsImported() {
        val result = importProxyServersFromUrls("masque://alice:pw@masque.example.com:443?sni=masque.example.com#Edge")

        assertEquals(listOf("Edge"), result.servers.map { it.getInfo().remarks })
        assertEquals(listOf("MASQUE"), result.servers.map { it.getInfo().protocol })
    }

    @Test
    fun aMasqueLinkInsideProseIsImported() {
        val result = importProxyServersFromUrls("my server: masque://alice:pw@masque.example.com:443#Edge, thanks")

        assertEquals(listOf("MASQUE"), result.servers.map { it.getInfo().protocol })
    }

    @Test
    fun aMasqueLinkWithAnInvalidPortIsReportedAsAFailure() {
        val failures = mutableListOf<ProxyServerUrlImportFailure>()

        val result = importProxyServersFromUrls("masque://alice:pw@masque.example.com:70000#Bad", failures::add)

        assertEquals(emptyList(), result.servers)
        assertEquals(1, failures.size)
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
