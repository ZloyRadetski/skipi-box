// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val Mb = 1_024L * 1_024L

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopHeapTrimTest {

    @Test
    fun aHeapWithLittleFreeCommittedSpaceIsLeftAlone() {
        assertFalse(shouldTrimHeap(HeapUsage(committedBytes = 90 * Mb, usedBytes = 60 * Mb), excessBytes = 64 * Mb))
    }

    @Test
    fun aHeapHoldingMoreFreeSpaceThanTheLimitIsTrimmed() {
        assertTrue(shouldTrimHeap(HeapUsage(committedBytes = 300 * Mb, usedBytes = 40 * Mb), excessBytes = 64 * Mb))
    }

    @Test
    fun theLimitItselfCountsAsExcess() {
        assertTrue(shouldTrimHeap(HeapUsage(committedBytes = 104 * Mb, usedBytes = 40 * Mb), excessBytes = 64 * Mb))
    }

    @Test
    fun theLoopTrimsEachMinuteOnlyWhileTheHeapHoldsExcess() = runTest {
        val usages = ArrayDeque(
            listOf(
                HeapUsage(committedBytes = 300 * Mb, usedBytes = 40 * Mb),
                HeapUsage(committedBytes = 60 * Mb, usedBytes = 40 * Mb),
                HeapUsage(committedBytes = 250 * Mb, usedBytes = 40 * Mb),
            ),
        )
        var trims = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runDesktopHeapTrim(
                readUsage = { usages.removeFirst() },
                trim = { trims++ },
                intervalMillis = 60_000L,
                excessBytes = 64 * Mb,
            )
        }

        advanceTimeBy(59_999L)
        assertEquals(0, trims, "nothing runs before the first interval has passed")
        advanceTimeBy(2L)
        assertEquals(1, trims, "the first check finds a burst's leftovers and trims them")
        advanceTimeBy(60_000L)
        assertEquals(1, trims, "a tight heap is not collected")
        advanceTimeBy(60_000L)
        assertEquals(2, trims)
        job.cancel()
    }
}
