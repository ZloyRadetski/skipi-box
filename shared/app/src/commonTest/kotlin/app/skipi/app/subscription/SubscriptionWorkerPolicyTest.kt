// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionWorkerPolicyTest {
    @Test
    fun scheduledRefreshSkipsMissingDisabledAndBlankGroupsButRunsEnabledUrl() {
        assertFalse(
            shouldFetchScheduledSubscription(
                groupFound = false,
                enabled = true,
                url = "https://example.test/list",
            ),
        )
        assertFalse(
            shouldFetchScheduledSubscription(
                groupFound = true,
                enabled = false,
                url = "https://example.test/list",
            ),
        )
        assertFalse(
            shouldFetchScheduledSubscription(
                groupFound = true,
                enabled = true,
                url = "  ",
            ),
        )
        assertTrue(
            shouldFetchScheduledSubscription(
                groupFound = true,
                enabled = true,
                url = "https://example.test/list",
            ),
        )
    }

    @Test
    fun scheduledRefreshResultKeepsAndroidSuccessRetryAndPermanentFailureRules() {
        assertEquals(SubscriptionWorkerOutcome.SUCCESS, subscriptionWorkerOutcome(emptyList()))
        assertEquals(SubscriptionWorkerOutcome.FAILURE, subscriptionWorkerOutcome(listOf(false)))
        assertEquals(SubscriptionWorkerOutcome.RETRY, subscriptionWorkerOutcome(listOf(true)))
        assertEquals(
            SubscriptionWorkerOutcome.RETRY,
            subscriptionWorkerOutcome(listOf(false, true)),
        )
    }
}
