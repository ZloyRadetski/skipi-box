// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

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
