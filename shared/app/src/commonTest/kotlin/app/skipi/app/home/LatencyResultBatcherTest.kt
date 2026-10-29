// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LatencyResultBatcherTest {
    @Test
    fun resultsInsideTheWindowAreAppliedOnceTogether() = runTest {
        val batches = mutableListOf<Map<Int, String>>()
        val batcher = LatencyResultBatcher<Int, String>(this, intervalMillis = 100) { batches += it }

        batcher.add(1, "10 ms")
        batcher.add(2, "20 ms")
        batcher.add(3, "30 ms")
        runCurrent()
        assertEquals(emptyList(), batches)

        advanceTimeBy(101)
        runCurrent()
        assertEquals(listOf(mapOf(1 to "10 ms", 2 to "20 ms", 3 to "30 ms")), batches)
    }

    @Test
    fun laterResultForTheSameKeyWinsAndNewWindowsStartAfterAFlush() = runTest {
        val batches = mutableListOf<Map<Int, String>>()
        val batcher = LatencyResultBatcher<Int, String>(this, intervalMillis = 100) { batches += it }

        batcher.add(1, "Testing")
        batcher.add(1, "15 ms")
        advanceTimeBy(101)
        runCurrent()
        batcher.add(2, "25 ms")
        advanceTimeBy(101)
        runCurrent()

        assertEquals(listOf(mapOf(1 to "15 ms"), mapOf(2 to "25 ms")), batches)
    }

    @Test
    fun flushAppliesPendingResultsImmediatelyAndOnlyOnce() = runTest {
        val batches = mutableListOf<Map<Int, String>>()
        val batcher = LatencyResultBatcher<Int, String>(this, intervalMillis = 100) { batches += it }

        batcher.add(7, "70 ms")
        batcher.flush()
        assertEquals(listOf(mapOf(7 to "70 ms")), batches)

        advanceTimeBy(500)
        runCurrent()
        batcher.flush()
        assertEquals(1, batches.size)
    }
}
