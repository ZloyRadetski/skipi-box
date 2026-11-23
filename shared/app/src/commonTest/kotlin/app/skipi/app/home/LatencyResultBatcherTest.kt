// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    @Test
    fun batchesAreAppliedInOrderEvenWhenAnApplyIsSlow() = runTest {
        val events = mutableListOf<String>()
        val firstApplyGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val batcher = LatencyResultBatcher<Int, String>(this, intervalMillis = 100) { batch ->
            events += "start ${batch.keys}"
            if (batch.containsKey(1)) firstApplyGate.await()
            events += "end ${batch.keys}"
        }

        batcher.add(1, "10 ms")
        val firstFlush = launch { batcher.flush() }
        runCurrent()
        batcher.add(2, "20 ms")
        val secondFlush = launch { batcher.flush() }
        runCurrent()

        assertEquals(listOf("start [1]"), events)
        firstApplyGate.complete(Unit)
        firstFlush.join()
        secondFlush.join()
        assertEquals(listOf("start [1]", "end [1]", "start [2]", "end [2]"), events)
    }
}
