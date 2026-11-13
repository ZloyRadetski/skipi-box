// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopStartupQuietTest {
    @Test
    fun theQuietPeriodIsCountedFromTheFirstFrameNotFromTheLaunch() = runTest {
        val firstFrame = CompletableDeferred<Unit>()
        var settled = false
        val waiting = launch {
            awaitDesktopStartupQuiet(quietMillis = 8_000L, firstFrame = { firstFrame.await() })
            settled = true
        }

        advanceTimeBy(100_000L)
        runCurrent()
        assertFalse(settled, "no frame yet, so the start has not settled however long it takes")

        firstFrame.complete(Unit)
        advanceTimeBy(7_999L)
        runCurrent()
        assertFalse(settled, "the quiet period has not passed")

        advanceTimeBy(2L)
        runCurrent()
        assertTrue(settled)
        waiting.join()
    }
}
