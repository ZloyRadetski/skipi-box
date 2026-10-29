// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.dnsleak.DnsLeakFailureKind
import features.tools.dnsleak.DnsLeakTestFailure
import features.tools.dnsleak.DnsLeakTestOutcome
import features.tools.dnsleak.DnsLeakVerdict
import features.tools.ipinfo.IpInfoState
import features.tools.speedtest.SpeedTestPhase
import features.tools.speedtest.SpeedTestState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticsControllersTest {
    @Test
    fun speedTestStartPublishesPingImmediatelyAndStopResetsToIdle() = runTest {
        val gate = CompletableDeferred<Unit>()
        val controller = SpeedTestController(
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            runnerFactory = { onState ->
                SpeedTestJob {
                    onState(SpeedTestState(phase = SpeedTestPhase.Download, progress = 0.5f, currentMbps = 3.0))
                    gate.await()
                    null
                }
            },
        )

        controller.start()
        assertEquals(SpeedTestPhase.Ping, controller.state.value.phase)
        testScheduler.runCurrent()
        assertEquals(SpeedTestPhase.Download, controller.state.value.phase)

        controller.stop()
        assertEquals(SpeedTestState(), controller.state.value)
    }

    @Test
    fun dnsLeakControllerReportsOutcomeAndFailureKind() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val outcome = DnsLeakTestOutcome(emptyList(), DnsLeakVerdict.NoLeak)
        val ok = DnsLeakTestController(scope) { outcome }
        ok.start()
        assertTrue(ok.state.value.running)
        testScheduler.runCurrent()
        assertFalse(ok.state.value.running)
        assertEquals(outcome, ok.state.value.outcome)

        val failing = DnsLeakTestController(scope) { throw DnsLeakTestFailure(hasVpnNetwork = true) }
        failing.start()
        testScheduler.runCurrent()
        assertTrue(failing.state.value.failed)
        assertEquals(DnsLeakFailureKind.TunnelNotPassing, failing.state.value.failureKind)
        assertNull(failing.state.value.outcome)
    }

    @Test
    fun dnsLeakStopCancelsWithoutMarkingFailure() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = DnsLeakTestController(scope) { awaitCancellation() }
        controller.start()
        testScheduler.runCurrent()

        controller.stop()
        testScheduler.runCurrent()

        assertFalse(controller.state.value.running)
        assertFalse(controller.state.value.failed)
    }

    @Test
    fun ipInfoControllerMapsFailuresAndTunnelIssue() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = IpInfoController(scope) { throw IpInfoFailure("VPN tunnel is up but passes no traffic", true) }

        controller.refresh()
        assertEquals(IpInfoState.Loading, controller.state.value)
        testScheduler.runCurrent()

        val failure = controller.state.value as IpInfoState.Failure
        assertTrue(failure.isTunnelIssue)
    }

    @Test
    fun ipInfoStopWhileLoadingReturnsToIdle() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = IpInfoController(scope) { awaitCancellation() }
        controller.refresh()
        testScheduler.runCurrent()

        controller.stop()

        assertEquals(IpInfoState.Idle, controller.state.value)
    }
}
