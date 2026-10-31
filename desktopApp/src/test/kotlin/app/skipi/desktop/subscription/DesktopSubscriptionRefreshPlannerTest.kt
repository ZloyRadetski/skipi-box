package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSubscriptionRefreshPlannerTest {
    @Test fun reports_due_providers_when_their_interval_has_elapsed_or_they_never_refreshed() {
        val nowMillis = 10 * HourMillis
        val dueByInterval = subscription(
            id = 1,
            updateInterval = "1",
            lastUpdatedAtMillis = nowMillis - HourMillis,
        )
        val neverRefreshed = subscription(
            id = 2,
            updateInterval = "6",
            lastUpdatedAtMillis = 0,
        )
        val notDue = subscription(
            id = 3,
            updateInterval = "2",
            lastUpdatedAtMillis = nowMillis - HourMillis,
        )

        val plan = DesktopSubscriptionRefreshPlanner.plan(
            DesktopSubscriptionLibrary(listOf(dueByInterval, neverRefreshed, notDue)),
            nowMillis,
        )

        assertEquals(listOf(1, 2), plan.dueSubscriptions.map { it.id })
        assertEquals(nowMillis, plan.nextWakeupAtMillis)
        assertEquals(0, plan.nextDelayMillis)
    }

    @Test fun omits_disabled_blank_url_and_unscheduled_providers() {
        val nowMillis = 10 * HourMillis
        val disabled = subscription(id = 1, enabled = false, updateInterval = "1", lastUpdatedAtMillis = 0)
        val blankUrl = subscription(id = 2, url = "  ", updateInterval = "1", lastUpdatedAtMillis = 0)
        val blankInterval = subscription(id = 3, updateInterval = "", lastUpdatedAtMillis = 0)
        val zeroInterval = subscription(id = 4, updateInterval = "0", lastUpdatedAtMillis = 0)

        val plan = DesktopSubscriptionRefreshPlanner.plan(
            DesktopSubscriptionLibrary(listOf(disabled, blankUrl, blankInterval, zeroInterval)),
            nowMillis,
        )

        assertEquals(emptyList(), plan.dueSubscriptions)
        assertNull(plan.nextWakeupAtMillis)
        assertNull(plan.nextDelayMillis)
    }

    @Test fun omits_invalid_intervals_instead_of_scheduling_them() {
        val nowMillis = 10 * HourMillis
        val tooShort = subscription(id = 1, updateInterval = "0.24", lastUpdatedAtMillis = 0)
        val malformed = subscription(id = 2, updateInterval = "tomorrow", lastUpdatedAtMillis = 0)
        val overflowing = subscription(id = 3, updateInterval = "999999999999999999999999999999", lastUpdatedAtMillis = 0)

        val plan = DesktopSubscriptionRefreshPlanner.plan(
            DesktopSubscriptionLibrary(listOf(tooShort, malformed, overflowing)),
            nowMillis,
        )

        assertEquals(emptyList(), plan.dueSubscriptions)
        assertNull(plan.nextWakeupAtMillis)
        assertNull(plan.nextDelayMillis)
    }

    @Test fun chooses_the_earliest_future_wakeup_and_reports_its_delay() {
        val nowMillis = 10 * HourMillis
        val sooner = subscription(
            id = 1,
            updateInterval = "1",
            lastUpdatedAtMillis = nowMillis - 20 * MinuteMillis,
        )
        val later = subscription(
            id = 2,
            updateInterval = "2",
            lastUpdatedAtMillis = nowMillis - 30 * MinuteMillis,
        )

        val plan = DesktopSubscriptionRefreshPlanner.plan(
            DesktopSubscriptionLibrary(listOf(later, sooner)),
            nowMillis,
        )

        assertEquals(emptyList(), plan.dueSubscriptions)
        assertEquals(nowMillis + 40 * MinuteMillis, plan.nextWakeupAtMillis)
        assertEquals(40 * MinuteMillis, plan.nextDelayMillis)
    }

    private fun subscription(
        id: Int,
        url: String = "https://example.com/$id",
        enabled: Boolean = true,
        updateInterval: String,
        lastUpdatedAtMillis: Long,
    ) = DesktopStoredSubscription(
        id = id,
        url = url,
        enabled = enabled,
        updateInterval = updateInterval,
        metadata = DesktopStoredSubscriptionMetadata(lastUpdatedAtMillis = lastUpdatedAtMillis),
    )

    private companion object {
        const val MinuteMillis = 60_000L
        const val HourMillis = 60 * MinuteMillis
    }
}
