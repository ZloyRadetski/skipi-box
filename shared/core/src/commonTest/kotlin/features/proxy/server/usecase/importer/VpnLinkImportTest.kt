// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Wireguard
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import utils.encodeBase64

/** `vpn://` carries a whole AmneziaWG / WireGuard `.conf` as Base64. */
class VpnLinkImportTest {
    private val privateKey = "a+b/".repeat(10) + "c+8="
    private val publicKey = "d+e/".repeat(10) + "f+8="

    private val awgConf = """
        [Interface]
        PrivateKey = $privateKey
        Address = 10.8.1.3/32
        DNS = 8.8.8.8, 8.8.4.4
        MTU = 1406
        Jc = 3
        Jmin = 48
        Jmax = 243
        S1 = 77
        S2 = 34
        S3 = 16
        S4 = 14
        H1 = 66539627
        H2 = 1050991802
        H3 = 1172824555
        H4 = 1792740007
        I1 = <r 132>
        HeaderProtectionKey = $publicKey
        ContentPaddingAddition = 21-57
        RekeyAfterTime = 107-146
        RekeyTimeout = 4-8
        RejectAfterTime = 180-238
        KeepaliveTimeout = 10-16
        MaxHandshakeAttempts = 22-36
        RandomTrailers = on
        DisableCookies = on

        # my awg node
        [Peer]
        PublicKey = $publicKey
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = vpn.example.org:56039
        PersistentKeepalive = 25
    """.trimIndent()

    private fun link(conf: String) = "vpn://" + conf.encodeBase64()

    @Test
    fun anAmneziaWgConfInsideAVpnLinkBecomesAnAmneziaWgServer() {
        val result = importProxyServersFromUrls(link(awgConf))

        val server = assertIs<AmneziaWg>(result.servers.single())
        assertEquals("vpn.example.org", server.server)
        assertEquals("56039", server.port)
        assertEquals(privateKey, server.secretKey)
        assertEquals("my awg node", server.remarks)
        assertEquals("3", server.jc)
        assertEquals("<r 132>", server.i1)
        assertEquals("21-57", server.contentPaddingAddition)
        assertEquals("on", server.randomTrailers)
        assertEquals("25", server.persistentKeepalive)
    }

    @Test
    fun aPlainWireguardConfInsideAVpnLinkBecomesAWireguardServer() {
        val conf = awgConf.lines().filterNot { line ->
            listOf("Jc", "Jmin", "Jmax", "S1", "S2", "S3", "S4", "H1", "H2", "H3", "H4", "I1", "Header", "Content",
                "Rekey", "Reject", "Keepalive", "Max", "Random", "Disable").any { line.startsWith(it) }
        }.joinToString("\n")

        val server = importProxyServersFromUrls(link(conf)).servers.single()

        assertIs<Wireguard>(server)
    }

    @Test
    fun urlSafeBase64AndSurroundingTextAreAccepted() {
        val urlSafe = "vpn://" + awgConf.encodeBase64().replace('+', '-').replace('/', '_').trimEnd('=')

        val result = importProxyServersFromUrls("Your config:\n$urlSafe\nthanks")

        assertIs<AmneziaWg>(result.servers.single())
    }

    @Test
    fun aVpnLinkWithoutAConfIsReportedAsAFailure() {
        val failures = mutableListOf<ProxyServerUrlImportFailure>()

        val result = importProxyServersFromUrls("vpn://" + "not a config".encodeBase64(), failures::add)

        assertTrue(result.servers.isEmpty())
        assertEquals(1, failures.size)
    }

    @Test
    fun aVpnLinkInsideAJsonSubscriptionIsImported() {
        val payload = "[\"${link(awgConf)}\"]"

        val result = parseProxyUrlProxyServerPayload(
            payload,
            ProxyServerImportContext(ProxyServerImportSource.SubscriptionUrl),
        ) { _, _ -> }

        assertIs<AmneziaWg>(result.servers.single())
    }
}
