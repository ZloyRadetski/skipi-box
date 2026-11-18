// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProtocolColorUtilsMasqueTest {

    private val others = listOf(
        "VLESS", "VMess", "Hysteria2", "Trojan", "Shadowsocks", "WireGuard", "AmneziaWG",
        "SOCKS", "HTTP",
    )

    @Test
    fun masqueHasItsOwnColorInBothThemes() {
        listOf(false, true).forEach { isDark ->
            val masque = ProtocolColorUtils.resolveProtocolColor("MASQUE", isDark)
            others.forEach { protocol ->
                assertNotEquals(
                    illegal = ProtocolColorUtils.resolveProtocolColor(protocol, isDark),
                    actual = masque,
                    message = "MASQUE shares its colour with $protocol (dark=$isDark)",
                )
            }
        }
    }

    @Test
    fun masqueColorIsCaseInsensitive() {
        assertEquals(
            ProtocolColorUtils.resolveProtocolColor("MASQUE", false),
            ProtocolColorUtils.resolveProtocolColor("masque", false),
        )
    }

    @Test
    fun masqueIgnoresOtherProtocolsCustomColors() {
        val custom = mapOf("hysteria2" to 0xFF123456L, "vless" to 0xFF654321L)

        assertEquals(
            ProtocolColorUtils.resolveProtocolColor("MASQUE", false),
            ProtocolColorUtils.resolveProtocolColor("MASQUE", false, custom),
        )
    }
}
