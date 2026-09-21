// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.Wireguard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WireguardConfParserTest {
    @Test
    fun parsesStandardWireguardConfWithoutPlatformContext() {
        val result = parseWireguardConf(
            """
                # Standard WG
                [Interface]
                PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
                Address = 10.0.0.2/32

                [Peer]
                PublicKey = YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
                Endpoint = 203.0.113.1:51820
            """.trimIndent(),
        )

        val server = assertIs<Wireguard>(assertIs<WireguardConfParseResult.Imported>(result).server)
        assertEquals("Standard WG", server.remarks)
        assertEquals("203.0.113.1", server.server)
        assertEquals("51820", server.port)
    }

    @Test
    fun identifiesInvalidAmneziaWgBeforeNativeCodeCanReceiveIt() {
        val result = parseWireguardConf(
            """
                [Interface]
                PrivateKey = aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=
                Address = 10.8.0.2/32
                Jc = 129
                Jmin = 40
                Jmax = 70

                [Peer]
                PublicKey = YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=
                Endpoint = 198.51.100.2:51820
            """.trimIndent(),
        )

        assertIs<WireguardConfParseResult.Invalid>(result)
    }

    @Test
    fun doesNotClaimUnrelatedText() {
        assertIs<WireguardConfParseResult.NotWireguardConf>(parseWireguardConf("vless://example"))
    }
}
