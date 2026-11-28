// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import utils.encodeBase64

/** Pre-existing gap: the masque scheme is missing from the subscription scheme list. */
class SubscriptionMasqueImportTest {
    @Test
    fun imports_base64_subscription_with_masque_links() {
        val source = "masque://alice:pw@masque.example.com:443?sni=masque.example.com#Edge"
        val encoded = source.encodeToByteArray().encodeBase64()

        val result = encoded.importSubscriptionServers()

        assertEquals(1, result.urlCount)
        assertEquals(listOf("MASQUE"), result.servers.map { it.getInfo().protocol })
        assertEquals(0, result.rejectedUrlCount)
    }

    @Test
    fun imports_plain_subscription_with_masque_links() {
        val result = "masque://alice:pw@masque.example.com:443#Edge".importSubscriptionServers()

        assertEquals(listOf("MASQUE"), result.servers.map { it.getInfo().protocol })
    }
}
