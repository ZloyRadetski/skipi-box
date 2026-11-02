// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.AmneziaWg
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A JSON subscription is an array of proxy links. JSON encoders escape characters inside the
 * strings (`&` as `\u0026`, `/` as `\/`), so the links must be read as decoded JSON strings,
 * not scanned out of the raw text.
 */
class JsonLinkSubscriptionImportTest {
    private val privateKey = "a+b/".repeat(10) + "c+8="
    private val publicKey = "d+e/".repeat(10) + "f+8="

    private fun enc(value: String) = value
        .replace("+", "%2B").replace("/", "%2F").replace("=", "%3D")

    private val context = ProxyServerImportContext(ProxyServerImportSource.SubscriptionUrl)

    private fun import(payload: String) = parseProxyUrlProxyServerPayload(payload, context) { _, _ -> }

    private fun assertAwg(server: Any?, name: String) {
        val awg = assertIs<AmneziaWg>(server)
        assertEquals("203.0.113.7", awg.server)
        assertEquals("51820", awg.port)
        assertEquals(privateKey, awg.secretKey)
        assertEquals(publicKey, awg.publicKey)
        assertEquals("5", awg.jc)
        assertEquals("30", awg.jmin)
        assertEquals("80", awg.jmax)
        assertEquals("20", awg.s1)
        assertEquals("40", awg.s2)
        assertEquals(name, awg.remarks)
    }

    private val query = "jc=5&jmin=30&jmax=80&s1=20&s2=40"

    @Test
    fun ampersandsEscapedAsUnicodeByGoStyleEncodersAreDecoded() {
        val link = "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}&$query#Node"
        val payload = "[\"${link.replace("&", "\\u0026")}\"]"

        val result = import(payload)

        assertEquals(1, result.servers.size)
        assertAwg(result.servers.single(), "Node")
    }

    @Test
    fun slashesEscapedByPhpStyleEncodersAreDecoded() {
        val link = "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=$publicKey&$query#Node"
        val payload = "[\"${link.replace("/", "\\/")}\"]"

        val result = import(payload)

        assertEquals(1, result.servers.size)
        assertAwg(result.servers.single(), "Node")
    }

    @Test
    fun severalLinksInOneJsonArrayAreAllImported() {
        val one = "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}&$query#One"
        val two = "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}&$query#Two"
        val payload = "[\n  \"${one.replace("&", "\\u0026")}\",\n  \"${two.replace("&", "\\u0026")}\"\n]"

        val result = import(payload)

        assertEquals(listOf("One", "Two"), result.servers.map { (it as AmneziaWg).remarks })
    }

    @Test
    fun plainTextLinesKeepWorking() {
        val link = "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}&$query#Node"

        val result = import(link)

        assertAwg(result.servers.single(), "Node")
    }
}
