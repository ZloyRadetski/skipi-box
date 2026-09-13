// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.importer

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Wireguard
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.importer.parseProxyServersFromWireguardConf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConfImportTest {

    private val context = ProxyServerImportContext(
        source = ProxyServerImportSource.Clipboard,
    )

    @Test
    fun standardWireguardConfImportsAsWireguard() = runTest {
        val conf = """
            # Standard WG
            [Interface]
            PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
            Address = 10.0.0.2/32
            DNS = 1.1.1.1
            MTU = 1420

            [Peer]
            PublicKey = YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
            Endpoint = 203.0.113.1:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val result = parseProxyServersFromWireguardConf(conf, context)
        assertEquals(1, result.servers.size)

        val server = assertIs<Wireguard>(result.servers.first())
        assertEquals("Standard WG", server.remarks)
        assertEquals("203.0.113.1", server.server)
        assertEquals("51820", server.port)
        assertEquals("aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=", server.secretKey)
        assertEquals("YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=", server.publicKey)
        assertEquals("10.0.0.2/32", server.address)
        assertEquals("1420", server.mtu)
    }

    @Test
    fun amneziaWireguardConfImportsAsAmneziaWg() = runTest {
        val conf = """
            # Amnezia Tunnel
            [Interface]
            PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
            Address = 10.8.0.2/32
            MTU = 1360
            Jc = 4
            Jmin = 40
            Jmax = 70
            S1 = 15
            S2 = 30
            S3 = 45
            S4 = 60
            H1 = 12345678-22345678
            H2 = 87654321-97654321
            H3 = 11223344-21223344
            H4 = 44332211-54332211
            I1 = <b 0x0003><r 2><b 0x2112A442><r 12><r 20>
            I2 = <b 0x0103><r 2><b 0x2112A442><r 12><r 24>
            I3 = <b 0x0008><r 2><b 0x2112A442><r 12><r 16>

            [Peer]
            PublicKey = YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
            Endpoint = 198.51.100.2:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val result = parseProxyServersFromWireguardConf(conf, context)
        assertEquals(1, result.servers.size)

        val server = assertIs<AmneziaWg>(result.servers.first())
        assertEquals("Amnezia Tunnel", server.remarks)
        assertEquals("198.51.100.2", server.server)
        assertEquals("51820", server.port)
        assertEquals("4", server.jc)
        assertEquals("40", server.jmin)
        assertEquals("70", server.jmax)
        assertEquals("15", server.s1)
        assertEquals("30", server.s2)
        assertEquals("45", server.s3)
        assertEquals("60", server.s4)
        assertEquals("12345678-22345678", server.h1)
        assertEquals("87654321-97654321", server.h2)
        assertEquals("11223344-21223344", server.h3)
        assertEquals("44332211-54332211", server.h4)
        assertEquals("<b 0x0003><r 2><b 0x2112A442><r 12><r 20>", server.i1)
        assertEquals("<b 0x0103><r 2><b 0x2112A442><r 12><r 24>", server.i2)
        assertEquals("<b 0x0008><r 2><b 0x2112A442><r 12><r 16>", server.i3)
    }

    @Test
    fun unsafeAmneziaWireguardConfIsRejectedBeforeItCanReachTheNativeRunner() = runTest {
        val conf = """
            [Interface]
            PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
            Address = 10.8.0.2/32
            Jc = 129
            Jmin = 40
            Jmax = 70

            [Peer]
            PublicKey = YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
            Endpoint = 198.51.100.2:51820
        """.trimIndent()

        val result = parseProxyServersFromWireguardConf(conf, context)

        assertEquals(0, result.servers.size)
    }
}
