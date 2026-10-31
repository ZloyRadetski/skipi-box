// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** As on Android, a strategy group's latency is the best answer among its members. */
class StrategyGroupLatencyTest {
    @Test
    fun theFastestAnsweringMemberIsTheGroupsLatency() = runTest {
        val delays = mapOf("a" to 120L, "b" to null, "c" to 45L, "d" to 300L)

        assertEquals(45L, strategyGroupLatency(delays.keys.toList(), concurrency = 4) { delays.getValue(it) })
    }

    @Test
    fun aGroupWhoseMembersAllFailHasNoLatency() = runTest {
        assertNull(strategyGroupLatency(listOf("a", "b"), concurrency = 4) { null })
        assertNull(strategyGroupLatency(emptyList<String>(), concurrency = 4) { 1L })
    }

    @Test
    fun aMemberThatThrowsCountsAsFailedNotAsTheGroupFailing() = runTest {
        val result = strategyGroupLatency(listOf("broken", "fine"), concurrency = 2) { member ->
            if (member == "broken") error("probe crashed") else 70L
        }
        assertEquals(70L, result)
    }

    @Test
    fun membersAreMeasuredSideBySideWithinTheLimit() = runTest {
        var active = 0
        var peak = 0
        val gate = CompletableDeferred<Unit>()
        val result = strategyGroupLatency((1..6).toList(), concurrency = 3) { member ->
            active += 1
            peak = maxOf(peak, active)
            if (active == 3) gate.complete(Unit)
            gate.await()
            active -= 1
            member.toLong()
        }
        assertEquals(1L, result)
        assertEquals(3, peak)
    }
}
