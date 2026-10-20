// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.runtime.ActiveGeoResourceRefreshTrigger
import features.config.runtime.TrafficConfigRefreshTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopResourceAutoUpdaterTest {
    @Test
    fun new_periodic_profiles_run_immediately_through_the_direct_callback() = runBlocking {
        var nowMillis = 1_000L
        var targets = listOf(target(id = 7, resourceAutoUpdate = true))
        var connected = true
        val updates = mutableListOf<Int>()
        val updater = updater(
            readTargets = { targets },
            isNetworkConnected = { connected },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId -> updates += profileId },
        )

        updater.runDue()

        assertEquals(listOf(7), updates)
        assertEquals(nowMillis + 15L * 60L * 1_000L, updater.nextWakeupAtMillis())
    }

    @Test
    fun a_due_profile_waits_offline_and_latest_profile_guard_is_checked_on_wakeup() = runBlocking {
        var nowMillis = 10L
        var targets = listOf(target(id = 8, resourceAutoUpdate = true))
        var connected = false
        val updates = mutableListOf<Int>()
        val updater = updater(
            readTargets = { targets },
            isNetworkConnected = { connected },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId -> updates += profileId },
        )

        updater.runDue()
        assertEquals(emptyList(), updates)

        connected = true
        updater.runDue()
        assertEquals(listOf(8), updates)

        nowMillis += 60L * 60L * 1_000L
        targets = listOf(target(id = 8, resourceAutoUpdate = false))
        updater.runDue()
        assertEquals(listOf(8), updates)
    }

    @Test
    fun periodic_failure_retries_after_source_backoff_and_success_reanchors_the_interval() = runBlocking {
        var nowMillis = 2_000L
        val intervalMillis = 60L * 60L * 1_000L
        val targets = listOf(target(id = 9, resourceAutoUpdate = true, interval = "1"))
        val updates = mutableListOf<Int>()
        var failNext = true
        val updater = updater(
            readTargets = { targets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId ->
                updates += profileId
                if (failNext) {
                    failNext = false
                    nowMillis = 2_100L
                    error("temporary failure")
                }
                nowMillis += 1_000L
            },
        )

        updater.runDue()
        val retryAtMillis = 2_100L + 15L * 60L * 1_000L
        assertEquals(retryAtMillis, updater.nextWakeupAtMillis())

        nowMillis = retryAtMillis - 1
        updater.runDue()
        assertEquals(listOf(9), updates)

        nowMillis = retryAtMillis
        updater.runDue()
        assertEquals(listOf(9, 9), updates)
        assertEquals(retryAtMillis + 1_000L + intervalMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun active_source_change_queues_one_time_work_separately_from_periodic_work() = runBlocking {
        var nowMillis = 100L
        val targets = listOf(target(id = 10, resourceAutoUpdate = true))
        val updates = mutableListOf<Int>()
        val updater = updater(
            readTargets = { targets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId -> updates += profileId },
        )
        val initial = trigger(id = 10)

        updater.observeActiveProfile(initial)
        updater.runDue()
        assertEquals(listOf(10), updates)

        nowMillis += 1
        updater.observeActiveProfile(initial.copy(customGeoSiteUrl = "https://custom.example/geo-site.dat"))
        updater.runDue()
        updater.observeActiveProfile(initial.copy(customGeoSiteUrl = "https://custom.example/geo-site.dat"))
        updater.runDue()

        assertEquals(listOf(10, 10), updates)
    }

    @Test
    fun changed_active_source_replaces_a_pending_one_time_retry() = runBlocking {
        var nowMillis = 50L
        val targets = listOf(target(id = 11, resourceAutoUpdate = true, interval = ""))
        val updates = mutableListOf<Int>()
        var failNext = true
        val updater = updater(
            readTargets = { targets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId ->
                updates += profileId
                if (failNext) {
                    failNext = false
                    error("temporary failure")
                }
            },
        )
        val baseline = trigger(id = 11)

        updater.observeActiveProfile(baseline)
        updater.observeActiveProfile(baseline.copy(customGeoIpUrl = "https://custom.example/first.dat"))
        updater.runDue()
        assertEquals(listOf(11), updates)
        assertEquals(nowMillis + 30_000L, updater.nextWakeupAtMillis())

        nowMillis += 1
        updater.observeActiveProfile(baseline.copy(customGeoIpUrl = "https://custom.example/second.dat"))
        updater.runDue()
        assertEquals(listOf(11, 11), updates)
        assertNull(updater.nextWakeupAtMillis())
    }

    @Test
    fun changed_active_source_cancels_running_one_time_work_without_overwriting_replacement() = runBlocking {
        var nowMillis = 80L
        val started = CompletableDeferred<Unit>()
        val callbackCancelled = CompletableDeferred<Unit>()
        val blocker = CompletableDeferred<Unit>()
        val updates = mutableListOf<Int>()
        var attempts = 0
        val updater = updater(
            readTargets = { listOf(target(id = 14, resourceAutoUpdate = true, interval = "")) },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId ->
                attempts += 1
                updates += profileId
                if (attempts == 1) {
                    started.complete(Unit)
                    try {
                        blocker.await()
                    } catch (cancelled: CancellationException) {
                        callbackCancelled.complete(Unit)
                        throw cancelled
                    }
                }
                nowMillis += 1
            },
        )
        val baseline = trigger(id = 14)

        updater.observeActiveProfile(baseline)
        updater.observeActiveProfile(baseline.copy(customGeoSiteUrl = "https://custom.example/first.dat"))
        val firstGeneration = launch { updater.runDue() }
        started.await()

        // Android's ExistingWorkPolicy.REPLACE cancels a running unique one-time request too.
        updater.observeActiveProfile(baseline.copy(customGeoSiteUrl = "https://custom.example/second.dat"))
        callbackCancelled.await()
        firstGeneration.join()

        updater.runDue()

        assertEquals(listOf(14, 14), updates)
        assertEquals(2, attempts)
        assertNull(updater.nextWakeupAtMillis())
    }

    @Test
    fun removing_periodic_eligibility_cancels_running_work_and_reenable_runs_immediately() = runBlocking {
        var nowMillis = 90L
        var targets = listOf(target(id = 15, resourceAutoUpdate = true))
        val started = CompletableDeferred<Unit>()
        val callbackCancelled = CompletableDeferred<Unit>()
        val blocker = CompletableDeferred<Unit>()
        var attempts = 0
        val updater = updater(
            readTargets = { targets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                if (attempts == 1) {
                    started.complete(Unit)
                    try {
                        blocker.await()
                    } catch (cancelled: CancellationException) {
                        callbackCancelled.complete(Unit)
                        throw cancelled
                    }
                }
                nowMillis += 1
            },
        )

        val firstRun = launch { updater.runDue() }
        started.await()

        // Android cancelGeo() cancels the unique periodic request when eligibility disappears.
        targets = listOf(target(id = 15, resourceAutoUpdate = false))
        updater.observeTargets(targets)
        callbackCancelled.await()
        firstRun.join()

        assertNull(updater.nextWakeupAtMillis(), "removed periodic work must not schedule a retry")
        assertEquals(1, attempts)

        targets = listOf(target(id = 15, resourceAutoUpdate = true))
        updater.observeTargets(targets)
        updater.runDue()

        assertEquals(2, attempts, "restored eligibility starts a fresh immediate periodic request")
        assertEquals(nowMillis + 15L * 60L * 1_000L, updater.nextWakeupAtMillis())
    }

    @Test
    fun removing_periodic_eligibility_does_not_cancel_distinct_running_one_time_work() = runBlocking {
        val periodicStarted = CompletableDeferred<Unit>()
        val periodicCancelled = CompletableDeferred<Unit>()
        val periodicBlocker = CompletableDeferred<Unit>()
        val oneTimeStarted = CompletableDeferred<Unit>()
        val oneTimeCancelled = CompletableDeferred<Unit>()
        val oneTimeBlocker = CompletableDeferred<Unit>()
        var targets = listOf(target(id = 16, resourceAutoUpdate = true))
        var attempts = 0
        val updater = updater(
            readTargets = { targets },
            isNetworkConnected = { true },
            nowMillis = { 100L },
            updateAutomatically = {
                when (attempts++) {
                    0 -> {
                        periodicStarted.complete(Unit)
                        try {
                            periodicBlocker.await()
                        } catch (cancelled: CancellationException) {
                            periodicCancelled.complete(Unit)
                            throw cancelled
                        }
                    }
                    1 -> {
                        oneTimeStarted.complete(Unit)
                        try {
                            oneTimeBlocker.await()
                        } catch (cancelled: CancellationException) {
                            oneTimeCancelled.complete(Unit)
                            throw cancelled
                        }
                    }
                    else -> Unit
                }
            },
        )
        val baseline = trigger(id = 16)

        updater.observeActiveProfile(baseline)
        updater.observeActiveProfile(baseline.copy(customGeoIpUrl = "https://custom.example/geoip.dat"))
        val running = launch { updater.runDue() }
        periodicStarted.await()
        oneTimeStarted.await()

        targets = listOf(target(id = 16, resourceAutoUpdate = false))
        updater.observeTargets(targets)
        periodicCancelled.await()
        assertTrue(!oneTimeCancelled.isCompleted, "periodic cancellation must not cancel the distinct one-time job")

        oneTimeBlocker.complete(Unit)
        running.join()

        assertEquals(2, attempts)
        assertTrue(!oneTimeCancelled.isCompleted)
        assertNull(updater.nextWakeupAtMillis())
    }

    @Test
    fun one_time_failure_uses_default_backoff_and_latest_profile_guard_skips_a_removed_profile() = runBlocking {
        var nowMillis = 500L
        var targets = listOf(target(id = 12, resourceAutoUpdate = true, interval = ""))
        val updates = mutableListOf<Int>()
        var failNext = true
        val updater = updater(
            readTargets = { targets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId ->
                updates += profileId
                if (failNext) {
                    failNext = false
                    error("temporary failure")
                }
            },
        )
        val baseline = trigger(id = 12)

        updater.observeActiveProfile(baseline)
        updater.observeActiveProfile(baseline.copy(customGeoIpUrl = "https://custom.example/geoip.dat"))
        updater.runDue()
        assertEquals(listOf(12), updates)
        assertEquals(nowMillis + 30_000L, updater.nextWakeupAtMillis())

        nowMillis += 30_000L
        targets = emptyList()
        updater.runDue()
        assertEquals(listOf(12), updates)
        assertNull(updater.nextWakeupAtMillis())
    }

    @Test
    fun cancelling_the_app_scoped_caller_propagates_without_recording_retry() = runBlocking {
        var nowMillis = 10L
        val started = CompletableDeferred<Unit>()
        val blocker = CompletableDeferred<Unit>()
        var attempts = 0
        var callbackObservedCancellation = false
        val updater = updater(
            readTargets = { listOf(target(id = 13, resourceAutoUpdate = true)) },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                if (attempts == 1) {
                    started.complete(Unit)
                    try {
                        blocker.await()
                    } catch (cancelled: CancellationException) {
                        callbackObservedCancellation = true
                        throw cancelled
                    }
                }
                nowMillis += 1
            },
        )

        val running = launch { updater.runDue() }
        started.await()
        running.cancelAndJoin()

        // Cancellation leaves the initial immediate run due instead of advancing to retry backoff.
        assertTrue(running.isCancelled)
        assertTrue(callbackObservedCancellation)
        assertEquals(10L, updater.nextWakeupAtMillis())
        updater.runDue()
        assertEquals(2, attempts)
        assertEquals(nowMillis + 15L * 60L * 1_000L, updater.nextWakeupAtMillis())
    }

    private fun updater(
        readTargets: () -> List<TrafficConfigRefreshTarget>,
        isNetworkConnected: () -> Boolean,
        nowMillis: () -> Long,
        updateAutomatically: suspend (Int) -> Unit,
    ) = DesktopResourceAutoUpdater(
        readLatestTargets = readTargets,
        isNetworkConnected = isNetworkConnected,
        updateAutomatically = updateAutomatically,
        nowMillis = nowMillis,
    )

    private fun target(
        id: Int,
        resourceAutoUpdate: Boolean,
        interval: String = "0.25",
    ) = TrafficConfigRefreshTarget(
        id = id,
        sourceUrl = "",
        autoUpdate = false,
        updateLocked = false,
        updateInterval = "",
        resourceAutoUpdate = resourceAutoUpdate,
        resourceUpdateInterval = interval,
    )

    private fun trigger(id: Int) = ActiveGeoResourceRefreshTrigger(
        profileId = id,
        customGeoIpUrl = "",
        customGeoSiteUrl = "",
    )
}
