// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigAutoUpdateExecutionResult
import features.config.runtime.TrafficConfigRefreshTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTrafficAutoUpdateLoopTest {
    @Test
    fun a_new_profile_runs_on_wakeup_while_first_profile_is_blocked_and_owner_cancel_stops_it() = runTest {
        val wakeups = Channel<Unit>(Channel.CONFLATED)
        var targets = listOf(target(id = 1))
        val firstProfileStarted = CompletableDeferred<Unit>()
        val firstProfileCancelled = CompletableDeferred<Unit>()
        val releaseFirstProfile = CompletableDeferred<Unit>()
        val secondProfileStarted = CompletableDeferred<Unit>()
        val updatedIds = mutableListOf<Int>()
        val updater = DesktopTrafficConfigAutoUpdater(
            readLatestTargets = { targets },
            isNetworkConnected = { true },
            updateAutomatically = { profileId ->
                updatedIds += profileId
                when (profileId) {
                    1 -> {
                        firstProfileStarted.complete(Unit)
                        try {
                            releaseFirstProfile.await()
                            TrafficConfigAutoUpdateExecutionResult.Success
                        } catch (cancelled: CancellationException) {
                            firstProfileCancelled.complete(Unit)
                            throw cancelled
                        }
                    }
                    2 -> {
                        secondProfileStarted.complete(Unit)
                        TrafficConfigAutoUpdateExecutionResult.Success
                    }
                    else -> TrafficConfigAutoUpdateExecutionResult.Success
                }
            },
            nowMillis = { testScheduler.currentTime },
        )
        val failures = mutableListOf<Throwable>()
        val owner = launch {
            runDesktopAutomaticRefreshLoop(
                runDue = { updater.runDue() },
                nextWakeupAtMillis = { updater.nextWakeupAtMillis() },
                wakeups = wakeups,
                nowMillis = { testScheduler.currentTime },
                minimumDelayMillis = 1L,
                idleDelayMillis = 60_000L,
                onFailure = { failures += it },
            )
        }
        try {
            runCurrent()
            firstProfileStarted.await()

            targets = listOf(target(id = 1), target(id = 2))
            updater.observeTargets(targets)
            assertTrue(wakeups.trySend(Unit).isSuccess)
            runCurrent()

            secondProfileStarted.await()
            assertEquals(listOf(1, 2), updatedIds)
            assertFalse(firstProfileCancelled.isCompleted, "the first profile remains active while the new one runs")
        } finally {
            owner.cancelAndJoin()
            wakeups.close()
        }

        assertTrue(firstProfileCancelled.isCompleted, "the loop owns and cancels its running profile pass")
        assertTrue(failures.isEmpty())
    }

    @Test
    fun loop_waits_for_the_updater_deadline_and_uses_it_for_the_next_period() = runTest {
        val wakeups = Channel<Unit>(Channel.CONFLATED)
        var callbackCount = 0
        val targets = listOf(target(id = 12))
        val updater = DesktopTrafficConfigAutoUpdater(
            readLatestTargets = { targets },
            isNetworkConnected = { true },
            updateAutomatically = {
                callbackCount += 1
                TrafficConfigAutoUpdateExecutionResult.Success
            },
            nowMillis = { testScheduler.currentTime },
        )
        val failures = mutableListOf<Throwable>()
        val owner = launch {
            runDesktopAutomaticRefreshLoop(
                runDue = { updater.runDue() },
                nextWakeupAtMillis = { updater.nextWakeupAtMillis() },
                wakeups = wakeups,
                nowMillis = { testScheduler.currentTime },
                minimumDelayMillis = 1L,
                idleDelayMillis = 24L * 60L * 60L * 1_000L,
                onFailure = { failures += it },
            )
        }
        try {
            runCurrent()
            assertEquals(1, callbackCount)
            val firstDeadline = updater.nextWakeupAtMillis()!!
            val untilDeadline = firstDeadline - testScheduler.currentTime
            assertTrue(untilDeadline > 1L)

            advanceTimeBy(untilDeadline - 1L)
            runCurrent()
            assertEquals(1, callbackCount, "no second callback runs before the updater's next deadline")

            advanceTimeBy(1L)
            runCurrent()

            assertEquals(2, callbackCount, "the loop runs work when the updater's deadline is reached")
            assertTrue(updater.nextWakeupAtMillis()!! > testScheduler.currentTime)
            assertTrue(failures.isEmpty())
        } finally {
            owner.cancelAndJoin()
            wakeups.close()
        }
    }

    private fun target(id: Int) = TrafficConfigRefreshTarget(
        id = id,
        sourceUrl = "https://example.test/config.conf",
        autoUpdate = true,
        updateLocked = false,
        updateInterval = "0.25",
        resourceAutoUpdate = false,
        resourceUpdateInterval = "invalid",
    )
}
