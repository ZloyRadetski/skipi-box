// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionFetchPolicyTest {
    @Test
    fun normalizes_timeout_seconds_to_android_fetcher_range() {
        assertEquals(3, SubscriptionFetchPolicy.timeoutSeconds(-1))
        assertEquals(3, SubscriptionFetchPolicy.timeoutSeconds(3))
        assertEquals(10, SubscriptionFetchPolicy.timeoutSeconds(10))
        assertEquals(600, SubscriptionFetchPolicy.timeoutSeconds(600))
        assertEquals(600, SubscriptionFetchPolicy.timeoutSeconds(601))
    }
}
