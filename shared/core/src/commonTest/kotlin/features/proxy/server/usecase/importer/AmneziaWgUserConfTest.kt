// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ProxyServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A real AmneziaWG 3.0 config from a user: it must import as it is and survive the link round trip. */
class AmneziaWgUserConfTest {
    private val conf = """
        [Interface]
        PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
        Address = 10.8.1.4/32
        DNS = 8.8.8.8, 8.8.4.4
        MTU = 1396
        Jc = 5
        Jmin = 81
        Jmax = 238
        S1 = 112
        S2 = 110
        S3 = 52
        S4 = 24
        H1 = 481387977
        H2 = 1013893224
        H3 = 1422207966
        H4 = 1809616248
        I1 = <r 170>
        HeaderProtectionKey = c29tZSBoZWFkZXIgcHJvdGVjdGlvbiBrZXkgb2shISE=
        ContentPaddingAddition = 12-24
        RekeyAfterTime = 109-147
        RekeyTimeout = 4-6
        RejectAfterTime = 187-247
        KeepaliveTimeout = 12-16
        MaxHandshakeAttempts = 16-38
        RandomTrailers = on
        DisableCookies = on

        # awg - skipi_test
        [Peer]
        PublicKey = YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = awg.example.invalid:54636
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun theConfImportsWithEveryFieldValidAndReachesTheNativeRunnerConfig() {
        val server = assertIs<AmneziaWg>(assertIs<WireguardConfParseResult.Imported>(parseWireguardConf(conf)).server)

        assertTrue(server.validateFull().isEmpty())
        assertEquals("awg - skipi_test", server.remarks)
        assertEquals("awg.example.invalid", server.server)
        assertEquals("<r 170>", server.i1)
        assertEquals("12-24", server.contentPaddingAddition)
        assertEquals("on", server.randomTrailers)
        assertEquals("on", server.disableCookies)
        assertEquals("25", server.persistentKeepalive)
        val out = server.toXrayOutbound("t").settings.toString()
        listOf("\"headerProtectionKey\"", "\"rekeyAfterTime\":\"109-147\"", "\"randomTrailers\":true", "\"disableCookies\":true", "\"keepAlive\":25")
            .forEach { assertTrue(out.contains(it), it) }
    }

    @Test
    fun theLinkOfTheConfParsesBackIdentically() {
        val server = assertIs<AmneziaWg>(assertIs<WireguardConfParseResult.Imported>(parseWireguardConf(conf)).server)
        val back = assertIs<AmneziaWg>(ProxyServer.parse(server.getUrl()))
        assertEquals(server.connectionFingerprint(), back.connectionFingerprint())
    }
}
