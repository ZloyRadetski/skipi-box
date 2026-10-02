// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionRefreshUseCaseTest {
    @Test
    fun refreshLoadsRequestsAndKeepsFailuresIsolated() = runTest {
        val result = refreshSubscriptions(
            requests = listOf("first", "broken", "last"),
            loader = SubscriptionRefreshLoader { request ->
                if (request == "broken") error("fetch failed")
                request.uppercase()
            },
            updatedAtMillis = { 123L },
        )

        assertEquals(listOf("FIRST", "LAST"), result.updates)
        assertEquals(listOf("broken"), result.failures.map { it.request })
        assertEquals("fetch failed", result.failures.single().error.message)
        assertEquals(123L, result.updatedAtMillis)
    }

    @Test
    fun commitPolicyRejectsProfileEditedDuringFetch() {
        val conflict = subscriptionRefreshConflict(
            baselineTarget = "subscription-v1",
            latestTarget = "subscription-v1",
            baselineProfile = "original profile",
            latestProfile = "manually edited profile",
        )

        assertEquals(SubscriptionRefreshConflict.EMBEDDED_PROFILE_CHANGED, conflict)
    }

    @Test
    fun commitPolicyAllowsUnchangedTargetAndProfile() {
        assertNull(
            subscriptionRefreshConflict(
                baselineTarget = "subscription",
                latestTarget = "subscription",
                baselineProfile = "profile",
                latestProfile = "profile",
            ),
        )
    }
}
