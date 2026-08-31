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
import platform.TunnelPhase

class DesktopTunnelControllerTest {
    @Test
    fun connects_and_disconnects_using_the_shared_tunnel_contract() = runBlocking {
        var running = false
        val controller = DesktopTunnelController(
            configForProfile = { Result.success("{}") },
            startProcess = {
                running = true
                Result.success(DesktopXrayProcessState(isRunning = true, pid = 42L))
            },
            stopProcess = {
                running = false
                Result.success(DesktopXrayProcessState(isRunning = false))
            },
            processState = { DesktopXrayProcessState(isRunning = running) },
        )

        assertTrue(controller.connect(TunnelConnectRequest("profile-1")).isSuccess)
        assertEquals(TunnelPhase.Connected, controller.snapshot().phase)
        assertTrue(controller.disconnect().isSuccess)
        assertEquals(TunnelPhase.Disconnected, controller.snapshot().phase)
        assertFalse(controller.supports(TunnelCapability.SystemProxy))
    }

    @Test
    fun reports_config_failure_in_the_shared_snapshot() = runBlocking {
        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(IllegalArgumentException("Invalid profile")) },
            startProcess = { error("must not start") },
            stopProcess = { Result.success(DesktopXrayProcessState(isRunning = false)) },
            processState = { DesktopXrayProcessState(isRunning = false) },
        )

        assertTrue(controller.connect(TunnelConnectRequest("bad")).isFailure)
        val snapshot = controller.snapshot()
        assertEquals(TunnelPhase.Failed, snapshot.phase)
        assertEquals("Invalid profile", snapshot.failure?.message)
    }
}
