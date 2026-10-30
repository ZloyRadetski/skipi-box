// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionOverflowTest {
    @Test
    fun farFutureExpiryIsNotShownAsExpiredNow() {
        val days = subscriptionExpiryDays(expireAtSeconds = 9_999_999_999_999_999L, nowMillis = 1_700_000_000_000L)
        assertTrue((days ?: 0L) > 1_000_000L)
    }

    @Test
    fun hugeWeekReminderDoesNotWrapToNegativeSeconds() {
        val reminder = SubscriptionExpiryReminder(value = 307_000_000, unit = ExpiryReminderUnit.Weeks)
        assertEquals(307_000_000L * 7L * 86_400L, reminder.totalSeconds)
    }
}
