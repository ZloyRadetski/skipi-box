// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import features.proxy.server.usecase.importer.importProxyServersFromUrls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Hysteria2PortListTest {
    private val tail = "sni=31.77.159.162&obfs=salamander&obfs-password=secret"

    @Test
    fun aPortListInTheAddressBecomesPortHopping() {
        val server = ProxyServer.parse("hysteria2://us@31.77.159.162:443,20000-29999/?$tail#Four")

        assertIs<Hysteria2>(server)
        assertEquals("31.77.159.162", server.server)
        assertEquals("443", server.port)
        assertEquals("20000-29999", server.mport)
        assertEquals("us", server.auth)
        assertEquals("Four", server.remarks)
        assertEquals("salamander", server.obfs)
        assertTrue(server.validateFull().isEmpty(), server.validateFull().toString())
    }

    @Test
    fun severalExtraPortsAndRangesAreKept() {
        val server = ProxyServer.parse("hy2://us@example.com:443,5000,6000-7000?$tail")

        assertIs<Hysteria2>(server)
        assertEquals("5000,6000-7000", server.mport)
        assertEquals("443", server.port)
    }

    @Test
    fun aPortListWithoutAPathOrQueryStillParses() {
        val server = ProxyServer.parse("hy2://us@example.com:443,20000-29999#Name")

        assertIs<Hysteria2>(server)
        assertEquals("20000-29999", server.mport)
        assertEquals("Name", server.remarks)
    }

    @Test
    fun anMportInTheQueryWinsOverTheAddress() {
        val server = ProxyServer.parse("hy2://us@example.com:443,20000-29999/?mport=30000-30100&$tail")

        assertIs<Hysteria2>(server)
        assertEquals("30000-30100", server.mport)
    }

    @Test
    fun theLinkWithoutAListIsUntouchedAndOtherSchemesToo() {
        val plain = "hysteria2://us@example.com:443/?$tail#One"
        assertEquals(plain, plain.moveHysteriaPortListToQuery())
        val vless = "vless://id@example.com:443,5?type=tcp"
        assertEquals(vless, vless.moveHysteriaPortListToQuery())
    }

    @Test
    fun theLinkReadsBackWithItsPortHopping() {
        val server = ProxyServer.parse("hysteria2://us@31.77.159.162:443,20000-29999/?$tail#Four") as Hysteria2

        val again = ProxyServer.parse(server.getUrl())

        assertIs<Hysteria2>(again)
        assertEquals("20000-29999", again.mport)
        assertEquals(server.connectionFingerprint(), again.connectionFingerprint())
    }

    @Test
    fun aSubscriptionWithBothHysteria2LinksImportsEveryServer() {
        val text = listOf(
            "vless://11111111-2222-3333-4444-555555555555@203.0.113.5:443?encryption=none&security=reality&sni=example.org&fp=firefox&pbk=KEY&sid=ab&type=tcp#One",
            "hysteria2://us@203.0.113.5:443/?$tail#Three",
            "hysteria2://us@203.0.113.5:443,20000-29999/?$tail#Four",
        ).joinToString("\n")
        val failures = mutableListOf<Throwable>()

        val result = importProxyServersFromUrls(text) { failures += it.error }

        assertEquals(emptyList(), failures)
        assertEquals(3, result.servers.size)
        assertEquals(3, result.servers.map { it.connectionFingerprint() }.toSet().size, "the port list keeps Three and Four apart")
    }
}
