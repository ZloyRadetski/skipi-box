// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.Masque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Mihomo's `type: masque` (adapter/outbound/masque.go): a WARP-style CONNECT-IP profile. */
class MihomoMasqueImportTest {
    private val privateKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
    private val publicKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=="

    private fun node(vararg extra: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "name" to "Warp",
        "type" to "masque",
        "server" to "engage.cloudflareclient.com",
        "port" to 443,
        "private-key" to privateKey,
        "public-key" to publicKey,
        "ip" to "172.16.0.2",
    ) + extra

    @Test
    fun convertsAWarpStyleProfile() {
        val server = node(
            "ipv6" to "2606:4700:110::1",
            "sni" to "front.example.com",
        ).toMihomoMasqueProxyServer()

        assertEquals("Warp", server.remarks)
        assertEquals("engage.cloudflareclient.com", server.server)
        assertEquals("443", server.port)
        assertEquals(privateKey, server.warpPrivateKey)
        assertEquals(publicKey, server.warpPublicKey)
        // Mihomo appends /32 and /128 itself; the core also accepts a plain address, so it is kept as written
        assertEquals("172.16.0.2,2606:4700:110::1", server.warpAddress)
        assertEquals("front.example.com", server.sni)
        assertEquals("", server.user)
        assertEquals("", server.pass)
    }

    @Test
    fun theSniDefaultsToTheOneMihomoUses() {
        assertEquals("consumer-masque.cloudflareclient.com", node().toMihomoMasqueProxyServer().sni)
    }

    @Test
    fun theUriBecomesHostAndPath() {
        val custom = node("uri" to "https://edge.example.com:8443/masque/").toMihomoMasqueProxyServer()
        val default = node("uri" to "https://cloudflareaccess.com").toMihomoMasqueProxyServer()
        val none = node().toMihomoMasqueProxyServer()

        assertEquals("edge.example.com:8443", custom.host)
        assertEquals("/masque/", custom.path)
        // Mihomo's default; the core uses the same host and the path "/" for WARP when they are blank
        assertEquals("cloudflareaccess.com", default.host)
        assertEquals("", default.path)
        assertEquals("", none.host)
        assertEquals("", none.path)
    }

    @Test
    fun remoteDnsIsTakenOnlyWhenMihomoResolvesRemotely() {
        val off = node("dns" to listOf("1.1.1.1", "8.8.8.8")).toMihomoMasqueProxyServer()
        val on = node("dns" to listOf("1.1.1.1", "8.8.8.8"), "remote-dns-resolve" to true).toMihomoMasqueProxyServer()

        assertEquals("", off.remoteDns)
        assertEquals("1.1.1.1,8.8.8.8", on.remoteDns)
    }

    @Test
    fun theSettingsOfMihomoThatHaveNoXrayCounterpartAreIgnored() {
        val server = node(
            "udp" to true, "mtu" to 1280, "congestion-controller" to "bbr", "cwnd" to 32,
            "handshake-timeout" to 5, "ip-stack" to "system", "network" to "h3",
        ).toMihomoMasqueProxyServer()

        assertEquals("engage.cloudflareclient.com", server.server)
    }

    @Test
    fun certificateVerificationCannotBeSwitchedOff() {
        assertFailsWith<UnsupportedMihomoProxyException> {
            node("skip-cert-verify" to true).toMihomoMasqueProxyServer()
        }
        assertEquals("engage.cloudflareclient.com", node("skip-cert-verify" to false).toMihomoMasqueProxyServer().server)
    }

    @Test
    fun otherTransportsOfMihomoAreRefused() {
        listOf("h2", "h3-l4proxy").forEach { network ->
            assertFailsWith<UnsupportedMihomoProxyException>("network $network") {
                node("network" to network).toMihomoMasqueProxyServer()
            }
        }
    }

    @Test
    fun theRequiredFieldsAreChecked() {
        listOf("server", "port", "private-key", "public-key").forEach { missing ->
            assertFailsWith<UnsupportedMihomoProxyException>("missing $missing") {
                (node() - missing).toMihomoMasqueProxyServer()
            }
        }
        // Mihomo itself refuses a profile without a local address
        assertFailsWith<UnsupportedMihomoProxyException> { (node() - "ip").toMihomoMasqueProxyServer() }
        assertEquals("2606:4700:110::1", (node("ipv6" to "2606:4700:110::1") - "ip").toMihomoMasqueProxyServer().warpAddress)
    }

    // --- through the same entry point the Android and Desktop importers use ---

    @Test
    fun theTypeIsSupportedAndGoesThroughTheSharedConverter() {
        assertTrue("masque".isSupportedMihomoProxyType())
        assertTrue("MASQUE".isSupportedMihomoProxyType())

        val server = node().toMihomoProxyServer()

        assertIs<Masque>(server)
    }

    @Test
    fun aProfileTheCoreWouldRefuseIsRejectedWithItsReason() {
        // a DoH URL is not an IP address: the core's remoteDNS takes IPs only
        assertFailsWith<UnsupportedMihomoProxyException> {
            node("dns" to listOf("https://dns.example/dns-query"), "remote-dns-resolve" to true).toMihomoProxyServer()
        }
        // the core's WARP keys are padded standard base64 or PEM
        assertFailsWith<UnsupportedMihomoProxyException> {
            node("private-key" to "not base64 !!").toMihomoProxyServer()
        }
        // two IPv4 addresses
        assertFailsWith<UnsupportedMihomoProxyException> {
            node("ip" to "172.16.0.2", "ipv6" to "10.0.0.2").toMihomoProxyServer()
        }
    }

    @Test
    fun xdriveHasNoMihomoCounterpart() {
        assertFalse("xdrive".isSupportedMihomoProxyType())
        assertFailsWith<UnsupportedMihomoProxyException> {
            mapOf("type" to "xdrive", "name" to "x", "server" to "s", "port" to 443).toMihomoProxyServer()
        }
    }
}
