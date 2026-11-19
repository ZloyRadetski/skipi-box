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
    fun aHeapCloseToWhatWasLiveAfterTheLastTrimIsLeftAlone() {
        assertFalse(shouldTrimHeap(committedBytes = 60 * Mb, liveAfterLastTrimBytes = 40 * Mb, excessBytes = 32 * Mb))
    }

    @Test
    fun garbageCountedAsUsedStillCountsAsExcess() {
        // The serial collector's old generation holds promoted garbage that Runtime reports as used, so a heap that
        // grew from 40 to 190 MB in a start burst looked "full" and was never given back.
        assertTrue(shouldTrimHeap(committedBytes = 190 * Mb, liveAfterLastTrimBytes = 40 * Mb, excessBytes = 32 * Mb))
    }

    @Test
    fun beforeAnyTrimTheWholeCommittedHeapIsExcess() {
        assertTrue(shouldTrimHeap(committedBytes = 120 * Mb, liveAfterLastTrimBytes = 0L, excessBytes = 32 * Mb))
    }

    @Test
    fun aLargeLiveSetGetsProportionallyMoreRoom() {
        // 100 MB live: half of it (50 MB) is the allowance, so a heap of 140 MB is not collected again every minute.
        assertFalse(shouldTrimHeap(committedBytes = 140 * Mb, liveAfterLastTrimBytes = 100 * Mb, excessBytes = 32 * Mb))
        assertTrue(shouldTrimHeap(committedBytes = 150 * Mb, liveAfterLastTrimBytes = 100 * Mb, excessBytes = 32 * Mb))
    }

    @Test
    fun theFirstCheckRunsSoonAfterTheStartThenEachInterval() = runTest {
        val committed = ArrayDeque(listOf(190 * Mb, 60 * Mb, 120 * Mb))
        var trims = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runDesktopHeapTrim(
                readCommitted = { committed.removeFirst() },
                readUsed = { 40 * Mb },
                trim = { trims++ },
                firstDelayMillis = 12_000L,
                intervalMillis = 60_000L,
                excessBytes = 32 * Mb,
            )
        }

        advanceTimeBy(11_999L)
        assertEquals(0, trims, "nothing runs before the start has settled")
        advanceTimeBy(2L)
        assertEquals(1, trims, "the start burst is given back right away, not a minute later")
        advanceTimeBy(60_000L)
        assertEquals(1, trims, "a heap close to the live data is not collected")
        advanceTimeBy(60_000L)
        assertEquals(2, trims, "a later burst is given back too")
        job.cancel()
    }
}
