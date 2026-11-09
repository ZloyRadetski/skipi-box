// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SkipiDeepLinkTest {
    @Test
    fun parses_subscription_and_manual_server_links_from_encoded_payloads() {
        assertEquals(
            SkipiDeepLink.Subscription("https://example.test/subscription"),
            parseSkipiDeepLinkOrNull(
                "SKIPI://ADD/https%3A%2F%2Fexample.test%2Fsubscription",
            ),
        )
        assertEquals(
            SkipiDeepLink.ManualServer("vless://uuid@example.test:443?security=tls"),
            parseSkipiDeepLinkOrNull(
                "skipi://import/vless%3A%2F%2Fuuid%40example.test%3A443%3Fsecurity%3Dtls",
            ),
        )
    }

    @Test
    fun parses_config_links_with_activation_and_source_url() {
        assertEquals(
            SkipiDeepLink.TrafficConfig(
                content = "[Rule]\nFINAL,PROXY",
                activate = true,
            ),
            parseSkipiDeepLinkOrNull(
                "skipi://routing/onadd/W1J1bGVdCkZJTkFMLFBST1hZ",
            ),
        )
        assertEquals(
            SkipiDeepLink.TrafficConfig(
                content = "https://example.test/routing.conf",
                activate = false,
                sourceUrl = "https://example.test/routing.conf",
            ),
            parseSkipiDeepLinkOrNull(
                "skipi://conf/add/https%3A%2F%2Fexample.test%2Frouting.conf",
            ),
        )
    }

    @Test
    fun parses_service_commands_and_ignores_unsupported_or_malformed_links() {
        assertEquals(SkipiDeepLink.Connect, parseSkipiDeepLinkOrNull("skipi://connect/now"))
        assertEquals(SkipiDeepLink.Toggle, parseSkipiDeepLinkOrNull("SKIPI://TOGGLE"))
        assertNull(parseSkipiDeepLinkOrNull("https://example.test/subscription"))
        assertNull(parseSkipiDeepLinkOrNull("skipi://add"))
        assertNull(parseSkipiDeepLinkOrNull("skipi://routing/add/"))
        assertNull(parseSkipiDeepLinkOrNull("skipi:/connect"))
    }

    @Test
    fun decodeSkipiPayload_accepts_url_safe_base64_and_rejects_plain_text() {
        assertEquals("[Rule]\nFINAL,PROXY", "W1J1bGVdCkZJTkFMLFBST1hZ".decodeSkipiPayload())
        assertNull("https://example.test/not-base64".decodeSkipiPayload())
    }

    @Test
    fun parses_a_theme_link_into_the_shared_theme() {
        val theme = CustomThemeShare(
            name = "Night harbour",
            dark = true,
            accent = 0xFF3B82F6,
            background = 0xFF081427,
            surface = 0xFF102443,
            surfaceVariant = 0xFF18345E,
            text = 0xFFE7F1FF,
            textSecondary = 0xFFB2C8EA,
        )

        assertEquals(SkipiDeepLink.Theme(theme), parseSkipiDeepLinkOrNull(theme.toLink()))
        assertEquals(SkipiDeepLink.Theme(theme), parseSkipiDeepLinkOrNull("SKIPI://THEME/${theme.toCode()}"))
    }

    @Test
    fun a_theme_link_with_a_damaged_payload_is_ignored() {
        assertNull(parseSkipiDeepLinkOrNull("skipi://theme/"))
        assertNull(parseSkipiDeepLinkOrNull("skipi://theme/not-a-theme"))
    }
}
