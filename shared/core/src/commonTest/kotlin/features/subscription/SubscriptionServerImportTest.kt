// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import utils.encodeBase64

class SubscriptionServerImportTest {
    @Test
    fun imports_line_based_subscription_and_rejects_invalid_proxy_urls() {
        val result = """
            # Provider comment
            vless://123e4567-e89b-42d3-a456-426614174000@example.com:443?security=tls&type=ws#One
            vless://bad-link
            https://provider.example.com/help
        """.trimIndent().importSubscriptionServers()

        assertEquals(2, result.urlCount)
        assertEquals(1, result.servers.size)
        assertEquals(1, result.rejectedUrlCount)
        assertEquals("VLESS", result.servers.single().getInfo().protocol)
    }

    @Test
    fun imports_base64_encoded_subscription() {
        val source = "vless://123e4567-e89b-42d3-a456-426614174000@example.com:443?security=tls#One"
        val encoded = source.encodeToByteArray().encodeBase64()

        val result = encoded.importSubscriptionServers()

        assertEquals(1, result.urlCount)
        assertEquals(1, result.servers.size)
        assertEquals(0, result.rejectedUrlCount)
    }

    @Test
    fun accepts_servers_satisfying_basic_validation() {
        // Non-UUID user ID fails validateFull() but passes validateBasic()
        val source = "vless://custom-token@example.com:443?security=tls#CustomToken"
        val result = source.importSubscriptionServers()

        assertEquals(1, result.urlCount)
        assertEquals(1, result.servers.size)
        assertEquals(0, result.rejectedUrlCount)
    }

    @Test
    fun imports_base64_subscription_with_naive_links() {
        val source = """
            naive+https://alice:pw@naive.example.com:443?padding=true#One
            naive+quic://alice:pw@203.0.113.7:443#Two
        """.trimIndent()
        val encoded = source.encodeToByteArray().encodeBase64()

        val result = encoded.importSubscriptionServers()

        assertEquals(2, result.urlCount)
        assertEquals(listOf("Naive", "Naive"), result.servers.map { it.getInfo().protocol })
        assertEquals(0, result.rejectedUrlCount)
    }

    @Test
    fun imports_plain_subscription_with_naive_links() {
        val result = "naive+https://alice:pw@naive.example.com:443#One".importSubscriptionServers()

        assertEquals(1, result.servers.size)
    }
}
