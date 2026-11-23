// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopConnectStartTest {
    @Test
    fun theMacTunInterfaceIsResolvedOnceAndKeptForEveryStepOfAConnect() {
        var listings = 0
        // Once Core has brought utun9 up, a fresh listing would name it taken and the next step would pick utun10.
        val lister = { listings++; if (listings == 1) emptyList() else listOf("utun9") }
        val pinned = DesktopPinnedTunPlan(DesktopAppSettings(), DesktopConfigLibrary(), macInterfaces = lister)

        val prepare = pinned.plan()
        val attach = pinned.plan(coreAnswersDns = false)

        assertEquals("utun9", prepare.name)
        assertEquals("utun9", attach.name)
        assertEquals(1, listings)
        assertTrue(attach.dnsServers.isEmpty())
    }

    @Test
    fun logTailersStartedForAFailedCoreStartAreStopped() {
        val events = mutableListOf<String>()

        val result = startCoreWithLogTailers(
            startTailers = { events += "start tailers" },
            stopTailers = { events += "stop tailers" },
            start = { Result.failure<Unit>(IllegalStateException("no core")) },
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("start tailers", "stop tailers"), events)
    }

    @Test
    fun logTailersKeepRunningWhenCoreStarted() {
        val events = mutableListOf<String>()

        val result = startCoreWithLogTailers(
            startTailers = { events += "start tailers" },
            stopTailers = { events += "stop tailers" },
            start = { Result.success(Unit) },
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("start tailers"), events)
    }

    @Test
    fun aThrowingStartIsAFailureAndStopsTheTailers() {
        val events = mutableListOf<String>()

        val result = startCoreWithLogTailers<Unit>(
            startTailers = { events += "start tailers" },
            stopTailers = { events += "stop tailers" },
            start = { throw IllegalStateException("boom") },
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("start tailers", "stop tailers"), events)
    }
}
