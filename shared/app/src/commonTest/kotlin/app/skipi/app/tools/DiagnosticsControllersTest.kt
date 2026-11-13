// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.dnsleak.DnsLeakFailureKind
import features.tools.dnsleak.DnsLeakStage
import features.tools.dnsleak.DnsLeakTestFailure
import features.tools.dnsleak.DnsLeakTestOutcome
import features.tools.dnsleak.DnsLeakVerdict
import features.tools.ipinfo.IpInfoState
import features.tools.speedtest.SpeedTestPhase
import features.tools.speedtest.SpeedTestResult
import features.tools.speedtest.SpeedTestState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
    fun aFinishedSpeedTestKeepsItsResultWhenTheScreenStopsIt() = runTest {
        val finished = SpeedTestState(
            phase = SpeedTestPhase.Finished,
            result = SpeedTestResult(pingMs = 20.0, jitterMs = 2.0, downloadMbps = 90.0, uploadMbps = 40.0),
        )
        val controller = SpeedTestController(
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            runnerFactory = { onState -> SpeedTestJob { onState(finished); null } },
        )

        controller.start()
        testScheduler.runCurrent()
        controller.stop()

        assertEquals(finished, controller.state.value)
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
    fun dnsLeakControllerExposesTheStageOfTheRunInProgress() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val controller = DnsLeakTestController(scope) { onStage ->
            onStage(DnsLeakStage.Locating)
            gate.await()
            DnsLeakTestOutcome(emptyList(), DnsLeakVerdict.Unknown)
        }

        assertNull(controller.state.value.stage)
        controller.start()
        assertEquals(DnsLeakStage.Probing, controller.state.value.stage)
        testScheduler.runCurrent()
        assertEquals(DnsLeakStage.Locating, controller.state.value.stage)

        gate.complete(Unit)
        testScheduler.runCurrent()
        assertNull(controller.state.value.stage)
        assertFalse(controller.state.value.running)
    }

    @Test
    fun dnsLeakNewRunDropsThePreviousOutcomeAndFailure() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val outcome = DnsLeakTestOutcome(emptyList(), DnsLeakVerdict.NoLeak)
        var fail = false
        val gate = CompletableDeferred<Unit>()
        val controller = DnsLeakTestController(scope) {
            if (fail) {
                gate.await()
                throw DnsLeakTestFailure(hasVpnNetwork = false)
            }
            outcome
        }
        controller.start()
        testScheduler.runCurrent()
        assertEquals(outcome, controller.state.value.outcome)

        fail = true
        controller.start()
        assertNull(controller.state.value.outcome)
        testScheduler.runCurrent()
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertTrue(controller.state.value.failed)
        assertNull(controller.state.value.outcome)

        fail = false
        controller.start()
        assertFalse(controller.state.value.failed)
        assertNull(controller.state.value.failureKind)
    }

    @Test
    fun dnsLeakRestartIsNotUndoneByTheCancelledRunWindingDown() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = DnsLeakTestController(scope) { awaitCancellation() }
        controller.start()
        testScheduler.runCurrent()

        controller.start()
        testScheduler.runCurrent()

        assertTrue(controller.state.value.running)
        assertEquals(DnsLeakStage.Probing, controller.state.value.stage)
    }

    @Test
    fun dnsLeakStopClearsTheStage() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = DnsLeakTestController(scope) { awaitCancellation() }
        controller.start()
        testScheduler.runCurrent()

        controller.stop()

        assertNull(controller.state.value.stage)
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

    @Test
    fun aCancelledSpeedTestWindingDownDoesNotOverwriteTheNewRun() = runTest {
        val gate = CompletableDeferred<Unit>()
        var run = 0
        val controller = SpeedTestController(
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            runnerFactory = { onState ->
                val current = ++run
                SpeedTestJob {
                    if (current == 1) {
                        try {
                            gate.await()
                        } finally {
                            // Mirrors SpeedTestRunner's cancellation cleanup, which runs after the new run began.
                            withContext(NonCancellable) {
                                yield()
                                onState(SpeedTestState())
                            }
                        }
                    } else {
                        onState(SpeedTestState(phase = SpeedTestPhase.Download, progress = 0.5f, currentMbps = 3.0))
                        gate.await()
                    }
                    null
                }
            },
        )

        controller.start()
        testScheduler.runCurrent()
        controller.start()
        testScheduler.runCurrent()

        assertEquals(SpeedTestPhase.Download, controller.state.value.phase)
    }
}

