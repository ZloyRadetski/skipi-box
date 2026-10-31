// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import platform.TunnelCapability
import platform.TunnelConnectRequest
import platform.TunnelOperationException
import platform.TunnelOperationStage
import platform.TunnelPhase

class DesktopTunnelControllerTunModeTest {
    private val events = mutableListOf<String>()
    private var coreRunning = false
    private var tunMode = true
    private var tunRunning = false
    private var startTunResult: Result<Unit> = Result.success(Unit)
    private var stopTunResult: Result<Unit> = Result.success(Unit)
    private var prepareTunResult: Result<Unit> = Result.success(Unit)
    private var coreStartResult: Result<DesktopCoreState> = Result.success(DesktopCoreState(isRunning = true))

    private fun controller() = DesktopTunnelController(
        configForProfile = { Result.success("{}") },
        startCore = {
            events += "core-start"
            coreRunning = coreStartResult.isSuccess
            coreStartResult
        },
        stopCore = {
            events += "core-stop"
            coreRunning = false
            Result.success(DesktopCoreState(isRunning = false))
        },
        coreState = { DesktopCoreState(isRunning = coreRunning) },
        awaitSystemProxyEndpoint = { events += "proxy-await"; Result.success(Unit) },
        acquireSystemProxy = { events += "proxy-acquire"; Result.success(Unit) },
        releaseSystemProxy = { events += "proxy-release"; Result.success(Unit) },
        systemProxySupported = { true },
        tunMode = { tunMode },
        prepareTun = {
            events += "tun-prepare"
            prepareTunResult
        },
        releaseTun = {
            events += "tun-release"
            Result.success(Unit)
        },
        startTun = {
            events += "tun-start"
            tunRunning = startTunResult.isSuccess
            startTunResult
        },
        stopTun = {
            events += "tun-stop"
            tunRunning = false
            stopTunResult
        },
        tunRunning = { tunRunning },
        tunSupported = { true },
    )

    @Test
    fun tunModeStartsTheTunAfterCoreAndSkipsTheSystemProxy() = runBlocking {
        val controller = controller()

        assertTrue(controller.connect(TunnelConnectRequest("p")).isSuccess)

        assertEquals(listOf("tun-prepare", "core-start", "tun-start"), events)
        assertEquals(TunnelPhase.Connected, controller.snapshot().phase)
        assertTrue(controller.supports(TunnelCapability.Tun))
    }

    @Test
    fun tunModeDisconnectStopsTheTunBeforeCore() = runBlocking {
        val controller = controller()
        controller.connect(TunnelConnectRequest("p"))
        events.clear()

        assertTrue(controller.disconnect().isSuccess)

        assertEquals(listOf("proxy-release", "tun-stop", "core-stop"), events)
        assertEquals(TunnelPhase.Disconnected, controller.snapshot().phase)
    }

    @Test
    fun failedTunStartStopsCoreAndFails() = runBlocking {
        startTunResult = Result.failure(IllegalStateException("NetworkManager refused"))
        val controller = controller()

        val error = controller.connect(TunnelConnectRequest("p")).exceptionOrNull() as TunnelOperationException

        assertEquals(TunnelOperationStage.Start, error.failure.stage)
        assertTrue("NetworkManager refused" in error.failure.message)
        assertEquals(listOf("tun-prepare", "core-start", "tun-start", "core-stop", "tun-release"), events)
        assertEquals(TunnelPhase.Failed, controller.snapshot().phase)
    }

    @Test
    fun theSocketBindingIsReleasedWhenCoreFailsToStart() = runBlocking {
        coreStartResult = Result.failure(IllegalStateException("port busy"))
        val controller = controller()

        val result = controller.connect(TunnelConnectRequest("p"))

        assertTrue(result.isFailure)
        assertEquals(listOf("tun-prepare", "core-start", "tun-release"), events)
    }

    @Test
    fun aFailedPrepareNeverStartsCore() = runBlocking {
        prepareTunResult = Result.failure(IllegalStateException("cannot bind"))
        val controller = controller()

        val error = controller.connect(TunnelConnectRequest("p")).exceptionOrNull() as TunnelOperationException

        assertTrue("cannot bind" in error.failure.message)
        assertEquals(listOf("tun-prepare"), events)
        assertEquals(TunnelPhase.Failed, controller.snapshot().phase)
    }

    @Test
    fun theModeIsFixedAtConnectTime() = runBlocking {
        val controller = controller()
        controller.connect(TunnelConnectRequest("p"))
        tunMode = false
        events.clear()

        controller.disconnect()

        assertTrue("tun-stop" in events)
    }

    @Test
    fun failedTunStopKeepsCoreRunningAndReportsFailure() = runBlocking {
        val controller = controller()
        controller.connect(TunnelConnectRequest("p"))
        events.clear()
        stopTunResult = Result.failure(IllegalStateException("hev stuck"))

        val result = controller.disconnect()

        assertTrue(result.isFailure)
        assertFalse("core-stop" in events)
        assertEquals(TunnelPhase.Failed, controller.snapshot().phase)
    }

    @Test
    fun aDeadTunnelWhileConnectedFailsTheSessionAndReleasesIt() = runBlocking {
        val controller = controller()
        controller.connect(TunnelConnectRequest("p"))
        tunRunning = false
        events.clear()

        val snapshot = controller.snapshot()

        assertEquals(TunnelPhase.Failed, snapshot.phase)
        assertEquals(TunnelOperationStage.ReadStatus, snapshot.failure?.stage)
        assertTrue("tun-stop" in events)
    }

    @Test
    fun withoutTunModeTheSystemProxyPathIsUnchanged() = runBlocking {
        tunMode = false
        val controller = controller()

        controller.connect(TunnelConnectRequest("p"))

        assertEquals(listOf("core-start", "proxy-await", "proxy-acquire"), events)
    }
}
