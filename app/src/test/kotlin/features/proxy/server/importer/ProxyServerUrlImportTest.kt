// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.importer

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
    fun anOlcRtcLinkIsNoLongerImported() = runTest {
        val link = "olcrtc://telemost?vp8channel<vp8-fps=25&vp8-batch=1>@56026201482837#30330bd1da1c7ad6e7d518e423662b3bb2b53ca1bbb65612263a494610fa73e4\$olcRTC TELEMOST"

        val result = parseProxyServersFromUrls(link, context)

        assertEquals(emptyList(), result.servers)
    }

    @Test
    fun unsafeAmneziaWgUrlIsRejectedBeforeItCanReachTheNativeRunner() = runTest {
        val link = "awg://aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=@198.51.100.2:51820?publickey=YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=&jc=129&jmin=40&jmax=70&s1=15&s2=30"

        val result = parseProxyServersFromUrls(link, context)

        assertEquals(0, result.servers.size)
    }
}
