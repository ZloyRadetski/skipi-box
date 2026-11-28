// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProtocolColorUtilsNaiveTest {

    private val others = listOf(
        "VLESS", "VMess", "Hysteria2", "Trojan", "Shadowsocks", "WireGuard", "AmneziaWG",
        "SOCKS", "HTTP", "MASQUE", "Custom",
    )

    @Test
    fun naiveHasItsOwnColorInBothThemes() {
        listOf(false, true).forEach { isDark ->
            val naive = ProtocolColorUtils.resolveProtocolColor("Naive", isDark)
            others.forEach { protocol ->
                assertNotEquals(
                    illegal = ProtocolColorUtils.resolveProtocolColor(protocol, isDark),
                    actual = naive,
                    message = "Naive shares its colour with $protocol (dark=$isDark)",
                )
            }
        }
    }

    @Test
    fun naiveIsMatchedBeforeShadowsocksAndIgnoresCase() {
        listOf(false, true).forEach { isDark ->
            val naive = ProtocolColorUtils.resolveProtocolColor("Naive", isDark)
            assertEquals(naive, ProtocolColorUtils.resolveProtocolColor("naive", isDark))
            assertEquals(naive, ProtocolColorUtils.resolveProtocolColor("NaiveProxy", isDark))
            assertNotEquals(ProtocolColorUtils.resolveProtocolColor("Shadowsocks", isDark), naive)
        }
    }
}
