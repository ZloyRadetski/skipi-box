// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopExitWatchdogTest {
    @Test
    fun theWatchdogEndsTheProcessWhenTheExitHasNotHappenedInTime() {
        val slept = AtomicLong()
        val exited = CountDownLatch(1)

        val thread = startDesktopExitWatchdog(
            delayMillis = 1_234L,
            sleep = { millis -> slept.set(millis) },
            exit = { exited.countDown() },
        )

        assertTrue(exited.await(5, TimeUnit.SECONDS), "the watchdog ran the exit")
        assertEquals(1_234L, slept.get())
        assertTrue(thread.isDaemon, "the watchdog itself must not keep the process alive")
    }
}
