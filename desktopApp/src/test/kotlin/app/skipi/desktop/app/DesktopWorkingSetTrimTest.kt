// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopWorkingSetTrimTest {
    private fun activity(
        focused: Boolean,
        unfocusedFor: Long = 0,
        sinceLastTrim: Long? = null,
        sinceStart: Long = 60_000,
    ) = WorkingSetActivity(focused, unfocusedFor, sinceLastTrim, sinceStart)

    @Test
    fun theStartupMemoryIsTrimmedOnceTheWindowHasSettled() {
        assertFalse(shouldTrimWorkingSet(activity(focused = true, sinceStart = 5_000)))
        assertTrue(shouldTrimWorkingSet(activity(focused = true, sinceStart = WorkingSetStartupSettleMillis)))
    }

    @Test
    fun aFocusedWindowIsNeverTrimmedAgainAfterTheStartupTrim() {
        assertFalse(shouldTrimWorkingSet(activity(focused = true, sinceLastTrim = 10 * 60_000L)))
    }

    @Test
    fun aWindowThatLostFocusIsTrimmedAfterAShortWhile() {
        assertFalse(shouldTrimWorkingSet(activity(focused = false, unfocusedFor = 1_000, sinceLastTrim = 120_000)))
        assertTrue(shouldTrimWorkingSet(activity(focused = false, unfocusedFor = WorkingSetBlurSettleMillis, sinceLastTrim = 120_000)))
    }

    @Test
    fun trimmingWhileInTheBackgroundIsSpacedOut() {
        assertFalse(shouldTrimWorkingSet(activity(focused = false, unfocusedFor = 60_000, sinceLastTrim = 10_000)))
        assertTrue(shouldTrimWorkingSet(activity(focused = false, unfocusedFor = 60_000, sinceLastTrim = WorkingSetMinGapMillis)))
    }

    @Test
    fun theLoopTrimsAfterStartupThenOnlyWhileTheWindowIsNotFocused() = runTest {
        var focused = true
        var trims = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runDesktopWorkingSetTrim(
                isFocused = { focused },
                trim = { trims++ },
                nowMillis = { currentTime },
                pollMillis = 1_000L,
            )
        }

        advanceTimeBy(WorkingSetStartupSettleMillis + 2_000L)
        assertEquals(1, trims, "one trim once the start has settled")

        advanceTimeBy(5 * 60_000L)
        assertEquals(1, trims, "none while the window stays focused")

        focused = false
        advanceTimeBy(WorkingSetBlurSettleMillis + 2_000L)
        assertEquals(2, trims, "one shortly after the focus is lost")

        advanceTimeBy(WorkingSetMinGapMillis)
        assertEquals(3, trims, "and again after the minimum gap while it stays unfocused")

        focused = true
        advanceTimeBy(5 * 60_000L)
        assertEquals(3, trims, "none once focus is back")
        job.cancel()
    }

    @Test
    fun theNativeHandlesAreBuiltOnceHoweverOftenTheSetIsTrimmed() {
        var built = 0
        var calls = 0
        val action = OnceBuiltAction { built++; { calls++ } }

        repeat(5) { action.run() }

        assertEquals(1, built)
        assertEquals(5, calls)
    }
}
