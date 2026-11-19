// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.runBlocking
import platform.TunnelConnectRequest
import platform.TunnelOperationException
import platform.TunnelPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Linux and Windows: Core starts and stops HEV TUN, so the app opens the device first and attaches last. */
class DesktopTunnelControllerCoreHostedTunTest {
    private val events = mutableListOf<String>()
    private var coreRunning = false
    private var tunnelAlive = true
    private var openResult: Result<Int> = Result.success(41)
    private var coreStartResult: Result<DesktopCoreState> = Result.success(DesktopCoreState(isRunning = true))
    private var attachResult: Result<Unit> = Result.success(Unit)
    private var coreStopResult: Result<DesktopCoreState> = Result.success(DesktopCoreState(isRunning = false))
    private var stopTunResult: Result<Unit> = Result.success(Unit)
    private var verifyResult: Result<Unit> = Result.success(Unit)
    private var tunMode = true
    private val startRequests = mutableListOf<DesktopCoreStartRequest>()

    private fun controller() = DesktopTunnelController(
        configForProfile = { Result.success("{}") },
        startCore = { request ->
            startRequests += request
            events += "core-start:${request.tunFd}"
            coreRunning = coreStartResult.isSuccess
            coreStartResult
        },
        stopCore = {
            events += "core-stop"
            coreRunning = false
            coreStopResult
        },
        coreState = { DesktopCoreState(isRunning = coreRunning) },
        verifyTunEndpoint = { events += "tun-verify"; verifyResult },
        tunMode = { tunMode },
        prepareTun = { events += "tun-prepare"; Result.success(Unit) },
        releaseTun = { events += "tun-release"; Result.success(Unit) },
        openTunForCore = { events += "tun-open"; openResult },
        attachTun = { events += "tun-attach"; attachResult },
        stopTun = { events += "tun-stop"; stopTunResult },
        tunRunning = { tunnelAlive },
        tunSupported = { true },
    )

    @Test
    fun theTunnelStartsThroughOneCoreCallBetweenOpenAndAttach() = runBlocking {
        val controller = controller()

        assertTrue(controller.connect(TunnelConnectRequest("p")).isSuccess)

        assertEquals(
            listOf("tun-prepare", "tun-open", "core-start:41", "tun-verify", "tun-attach"),
            events,
        )
        assertEquals(TunnelPhase.Connected, controller.snapshot().phase)
    }

    @Test
    fun aWindowsStyleStartPassesNoDescriptorButStillGoesThroughCore() = runBlocking {
        openResult = Result.success(-1)

        assertTrue(controller().connect(TunnelConnectRequest("p")).isSuccess)

        assertEquals(-1, startRequests.single().tunFd)
    }

    @Test
    fun proxyModeNeverOpensTheTunDevice() = runBlocking {
        tunMode = false

        assertTrue(controller().connect(TunnelConnectRequest("p")).isSuccess)

        assertFalse("tun-open" in events || "tun-prepare" in events || "tun-attach" in events, "events=$events")
        assertEquals(null, startRequests.single().tunFd)
    }

    @Test
    fun disconnectStopsCoreAndHevInsideItBeforeClosingTheDevice() = runBlocking {
        val controller = controller()
        controller.connect(TunnelConnectRequest("p"))
        events.clear()

        assertTrue(controller.disconnect().isSuccess)

        assertEquals(listOf("core-stop", "tun-stop"), events)
        assertEquals(TunnelPhase.Disconnected, controller.snapshot().phase)
    }

    @Test
    fun aFailedCoreStopStillClosesTheDeviceAndReportsTheFailure() = runBlocking {
        val controller = controller()
        controller.connect(TunnelConnectRequest("p"))
        events.clear()
        coreStopResult = Result.failure(IllegalStateException("stop timed out"))

        val error = controller.disconnect().exceptionOrNull() as TunnelOperationException

        assertEquals(listOf("core-stop", "tun-stop"), events)
        assertTrue("stop timed out" in error.failure.message)
    }

    @Test
    fun aFailedDeviceCloseIsReportedAfterCoreStopped() = runBlocking {
        val controller = controller()
        controller.connect(TunnelConnectRequest("p"))
        stopTunResult = Result.failure(IllegalStateException("routes still installed"))

        val error = controller.disconnect().exceptionOrNull() as TunnelOperationException

        assertTrue("routes still installed" in error.failure.message)
    }

    @Test
    fun aFailedOpenReleasesThePreparedBindingAndNeverStartsCore() = runBlocking {
        openResult = Result.failure(IllegalStateException("NetworkManager refused"))

        val error = controller().connect(TunnelConnectRequest("p")).exceptionOrNull() as TunnelOperationException

        assertTrue("NetworkManager refused" in error.failure.message)
        assertEquals(listOf("tun-prepare", "tun-open", "tun-release"), events)
    }

    @Test
    fun aFailedCoreSessionStartReleasesTheDevice() = runBlocking {
        coreStartResult = Result.failure(IllegalStateException("hev did not become ready"))

        val error = controller().connect(TunnelConnectRequest("p")).exceptionOrNull() as TunnelOperationException

        assertTrue("hev did not become ready" in error.failure.message)
        assertEquals(listOf("tun-prepare", "tun-open", "core-start:41", "tun-release"), events)
    }

    @Test
    fun aFailedAttachStopsCoreThenReleasesTheDevice() = runBlocking {
        attachResult = Result.failure(IllegalStateException("routes failed"))

        val error = controller().connect(TunnelConnectRequest("p")).exceptionOrNull() as TunnelOperationException

        assertTrue("routes failed" in error.failure.message)
        assertEquals(
            listOf("tun-prepare", "tun-open", "core-start:41", "tun-verify", "tun-attach", "core-stop", "tun-release"),
            events,
        )
    }

    @Test
    fun aDeadLocalEndpointStopsCoreAndReleasesTheDeviceWithoutAttaching() = runBlocking {
        verifyResult = Result.failure(IllegalStateException("nothing listens"))

        controller().connect(TunnelConnectRequest("p"))

        assertEquals(
            listOf("tun-prepare", "tun-open", "core-start:41", "tun-verify", "core-stop", "tun-release"),
            events,
        )
    }

    @Test
    fun aDeadTunnelUnderALiveCoreIsReleasedByTheSnapshot() = runBlocking {
        val controller = controller()
        controller.connect(TunnelConnectRequest("p"))
        events.clear()
        tunnelAlive = false

        val snapshot = controller.snapshot()

        assertEquals(TunnelPhase.Failed, snapshot.phase)
        assertEquals(listOf("tun-stop"), events)
    }
}
