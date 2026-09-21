// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionRefreshSchedulingTest {
    @Test
    fun refresh_plan_marks_due_targets_and_keeps_the_earliest_wakeup() {
        val nowMillis = 10 * HourMillis

        val plan = planSubscriptionRefreshes(
            targets = listOf(
                target(id = 1, updateInterval = "1", lastUpdatedAtMillis = nowMillis - HourMillis),
                target(id = 2, updateInterval = "6", lastUpdatedAtMillis = 0L),
                target(id = 3, updateInterval = "2", lastUpdatedAtMillis = nowMillis - HourMillis),
            ),
            nowMillis = nowMillis,
        )

        assertEquals(listOf(1, 2), plan.dueSubscriptionIds)
        assertEquals(nowMillis, plan.nextWakeupAtMillis)
        assertEquals(0L, plan.nextDelayMillis)
    }

    @Test
    fun refresh_plan_ignores_ineligible_and_invalid_targets() {
        val plan = planSubscriptionRefreshes(
            targets = listOf(
                target(id = 1, enabled = false, updateInterval = "1"),
                target(id = 2, url = " ", updateInterval = "1"),
                target(id = 3, updateInterval = "0.24"),
                target(id = 4, updateInterval = "not-a-number"),
            ),
            nowMillis = 10 * HourMillis,
        )

        assertEquals(emptyList(), plan.dueSubscriptionIds)
        assertNull(plan.nextWakeupAtMillis)
        assertNull(plan.nextDelayMillis)
    }

    @Test
    fun scheduler_cancels_stale_work_and_enqueues_only_eligible_targets() {
        val gateway = RecordingGateway(scheduledIds = setOf(1, 9))
        SubscriptionScheduler(gateway).reconcile(
            listOf(
                target(id = 1, updateInterval = "1"),
                target(id = 2, enabled = false, updateInterval = "1"),
                target(id = 3, url = "", updateInterval = "1"),
                target(id = 4, updateInterval = "0.24"),
            ),
        )

        assertEquals(listOf(9), gateway.cancelledIds)
        assertEquals(
            listOf(
                SubscriptionWorkSpec(
                    groupId = 1,
                    uniqueName = "subscription-update-1",
                    repeatIntervalMillis = HourMillis,
                    requiresConnectedNetwork = true,
                    policy = SubscriptionExistingWorkPolicy.UPDATE,
                    backoffMillis = 15 * MinuteMillis,
                ),
            ),
            gateway.enqueuedSpecs,
        )
        assertEquals(setOf(1), gateway.storedIds)
    }

    private fun target(
        id: Int,
        url: String = "https://example.test/$id",
        enabled: Boolean = true,
        updateInterval: String,
        lastUpdatedAtMillis: Long = 0L,
    ) = SubscriptionRefreshTarget(
        id = id,
        url = url,
        enabled = enabled,
        updateInterval = updateInterval,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    )

    private class RecordingGateway(
        private val scheduledIds: Set<Int>,
    ) : SubscriptionScheduleGateway {
        val cancelledIds = mutableListOf<Int>()
        val enqueuedSpecs = mutableListOf<SubscriptionWorkSpec>()
        var storedIds: Set<Int>? = null

        override fun scheduledGroupIds(): Set<Int> = scheduledIds

        override fun enqueue(spec: SubscriptionWorkSpec) {
            enqueuedSpecs += spec
        }

        override fun cancel(groupId: Int) {
            cancelledIds += groupId
        }

        override fun storeScheduledGroupIds(groupIds: Set<Int>) {
            storedIds = groupIds
        }
    }

    private companion object {
        const val MinuteMillis = 60_000L
        const val HourMillis = 60 * MinuteMillis
    }
}
