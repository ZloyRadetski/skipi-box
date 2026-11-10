// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A group's latency is its best member's, so a member that is still silent long after the best answer arrived
 * cannot improve it and is not waited for. Dead members used to hold the whole group until their own timeout.
 */
class StrategyGroupLatencyCutoffTest {
    @Test
    fun deadAndSlowMembersDoNotHoldTheGroupUntilTheirTimeout() = runTest {
        val result = strategyGroupLatency(listOf("fast", "slow", "dead"), concurrency = 3) { member ->
            when (member) {
                "fast" -> { delay(50); 50L }
                "slow" -> { delay(5_000); 5_000L }
                else -> { delay(5_000); null }
            }
        }

        assertEquals(50L, result)
        assertTrue(currentTime < 1_000, "the group took $currentTime ms")
    }

    @Test
    fun aMemberThatAnswersInsideTheGraceWindowCanStillWin() = runTest {
        // The first answer is 200 ms; another member reports 90 ms for a probe that took longer on the wall clock.
        val result = strategyGroupLatency(listOf("first", "better"), concurrency = 2) { member ->
            if (member == "first") { delay(200); 200L } else { delay(300); 90L }
        }

        assertEquals(90L, result)
    }

    @Test
    fun membersWaitingForTheirTurnAreNotCutForTheTimeTheyWaited() = runTest {
        val probed = mutableListOf<String>()
        val result = strategyGroupLatency(listOf("a", "b", "c"), concurrency = 1) { member ->
            probed += member
            delay(50)
            if (member == "c") 20L else 50L
        }

        assertEquals(listOf("a", "b", "c"), probed)
        assertEquals(20L, result)
    }

    @Test
    fun whenNothingAnswersEveryMemberIsAwaitedAndTheGroupHasNoLatency() = runTest {
        val result = strategyGroupLatency(listOf("a", "b"), concurrency = 2) { delay(5_000); null }

        assertEquals(null, result)
        assertEquals(5_000L, currentTime)
    }

    @Test
    fun aStragglerStillRunningWhenTheGroupIsDecidedIsCancelled() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        val result = strategyGroupLatency(listOf("fast", "stuck"), concurrency = 2) { member ->
            if (member == "fast") {
                delay(10); 10L
            } else {
                try { CompletableDeferred<Long?>().await() } finally { cancelled.complete(Unit) }
            }
        }

        advanceUntilIdle()
        assertEquals(10L, result)
        assertTrue(cancelled.isCompleted)
    }

    @Test
    fun cancellingTheCallerCancelsTheProbes() = runTest {
        val cancelled = CompletableDeferred<Unit>()
        val job = launch {
            strategyGroupLatency(listOf("a"), concurrency = 1) {
                try { CompletableDeferred<Long?>().await() } finally { cancelled.complete(Unit) }
            }
        }
        runCurrent()

        job.cancel()
        advanceUntilIdle()

        assertTrue(cancelled.isCompleted)
    }
}
