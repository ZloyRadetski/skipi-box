// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ShadowsocksLegacyAtSignTest {
    @Test
    fun passwordMayContainAtSignsBecauseTheHostFollowsTheLastOne() {
        val server = Shadowsocks().parseLegacy("aes-256-gcm:pa@ss@1.2.3.4:8388", "n")

        assertEquals("aes-256-gcm", server.method)
        assertEquals("pa@ss", server.password)
        assertEquals("1.2.3.4", server.server)
        assertEquals("8388", server.port)
    }

    @Test
    fun ordinaryLegacyUriStillParses() {
        val server = Shadowsocks().parseLegacy("chacha20-ietf-poly1305:secret@example.org:443", "n")

        assertEquals("secret", server.password)
        assertEquals("example.org", server.server)
        assertEquals("443", server.port)
    }
}
