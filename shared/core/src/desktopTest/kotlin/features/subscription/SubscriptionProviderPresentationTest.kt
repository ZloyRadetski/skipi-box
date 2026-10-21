// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionProviderPresentationTest {
    @Test
    fun trafficSummaryKeepsAndroidUnknownFormattingAndSaturationRules() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)

            assertNull(subscriptionTrafficSummary(-1L, -1L, -1L))
            assertEquals("1.0 B / 2.0 B", subscriptionTrafficSummary(1L, 0L, 2L))
            assertEquals("1.50 KB / 2.00 KB", subscriptionTrafficSummary(512L, 1_024L, 2_048L))
            assertEquals("1.0 B / ∞", subscriptionTrafficSummary(1L, 0L, 0L))
            assertEquals(
                "8388608.00 TB / ∞",
                subscriptionTrafficSummary(Long.MAX_VALUE, 1L, Long.MAX_VALUE),
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun trafficProgressKeepsUnlimitedBoundaryAndClampsUsedRatio() {
        val unlimitedThreshold = Long.MAX_VALUE / 2L

        assertNull(subscriptionTrafficProgress(-1L, -1L, -1L))
        assertEquals(0f, subscriptionTrafficProgress(-1L, -2L, 100L))
        assertEquals(1f, subscriptionTrafficProgress(1_000L, 500L, 1_000L))
        assertTrue(subscriptionTrafficProgress(1L, 0L, unlimitedThreshold - 1L) != null)
        assertNull(subscriptionTrafficProgress(1L, 0L, unlimitedThreshold))
        assertNull(subscriptionTrafficProgress(1L, 0L, Long.MAX_VALUE))
    }

    @Test
    fun expiryDaysReturnsNullWithoutExpiryAndFloorsThenClampsAtExpiration() {
        val millisPerDay = 24L * 60L * 60L * 1_000L
        val expireAtSeconds = 10L * 24L * 60L * 60L
        val expireAtMillis = expireAtSeconds * 1_000L

        assertNull(subscriptionExpiryDays(-1L, 0L))
        assertNull(subscriptionExpiryDays(0L, 0L))
        assertEquals(1L, subscriptionExpiryDays(expireAtSeconds, expireAtMillis - millisPerDay - 1L))
        assertEquals(0L, subscriptionExpiryDays(expireAtSeconds, expireAtMillis - 1L))
        assertEquals(0L, subscriptionExpiryDays(expireAtSeconds, expireAtMillis))
        assertEquals(0L, subscriptionExpiryDays(expireAtSeconds, expireAtMillis + 1L))
    }
}
