// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.notification

import app.SubscriptionGroupState
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpiryHistoryPruneTest {
    private fun group(id: Int, expire: Long) = SubscriptionGroupState(
        id = id,
        name = "g$id",
        url = "https://example.com/$id",
        userAgent = "ua",
        updateInterval = "1h",
        enabled = true,
        trafficExpireAtSeconds = expire,
    )

    @Test
    fun keepsKeysOfExistingGroupAndExpiry_dropsRemovedAndRenewed() {
        val stored = listOf("sent_1_100", "sent_1_50", "sent_2_200", "sent_9_5", "unrelated")
        val stale = staleExpiryHistoryKeys(stored, listOf(group(1, 100), group(2, 200)))
        assertEquals(setOf("sent_1_50", "sent_9_5"), stale.toSet())
    }

    @Test
    fun noGroups_dropsAllSentKeysButNotOthers() {
        val stale = staleExpiryHistoryKeys(listOf("sent_1_100", "other"), emptyList())
        assertEquals(listOf("sent_1_100"), stale)
    }
}
