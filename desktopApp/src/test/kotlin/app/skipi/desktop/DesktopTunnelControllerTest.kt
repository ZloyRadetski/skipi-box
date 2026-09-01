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

    @Test
    fun ownsWindowsSystemProxyInTheSameSharedTunnelLifecycle() = runBlocking {
        var running = false
        val calls = mutableListOf<String>()
        val controller = DesktopTunnelController(
            configForProfile = { Result.success("{}") },
            startProcess = {
                calls += "start"
                running = true
                Result.success(DesktopXrayProcessState(isRunning = true, pid = 42L))
            },
            stopProcess = {
                calls += "stop"
                running = false
                Result.success(DesktopXrayProcessState(isRunning = false))
            },
            processState = { DesktopXrayProcessState(isRunning = running) },
            awaitSystemProxyEndpoint = {
                calls += "ready"
                Result.success(Unit)
            },
            acquireSystemProxy = {
                calls += "acquire"
                Result.success(Unit)
            },
            releaseSystemProxy = {
                calls += "release"
                Result.success(Unit)
            },
            systemProxySupported = { true },
        )

        assertTrue(controller.connect(TunnelConnectRequest("profile-1")).isSuccess)
        assertTrue(controller.disconnect().isSuccess)

        assertEquals(listOf("start", "ready", "acquire", "release", "stop"), calls)
        assertTrue(controller.supports(TunnelCapability.SystemProxy))
    }

    @Test
    fun rollsBackXrayWhenWindowsSystemProxyCannotBeAcquired() = runBlocking {
        var running = false
        val controller = DesktopTunnelController(
            configForProfile = { Result.success("{}") },
            startProcess = {
                running = true
                Result.success(DesktopXrayProcessState(isRunning = true))
            },
            stopProcess = {
                running = false
                Result.success(DesktopXrayProcessState(isRunning = false))
            },
            processState = { DesktopXrayProcessState(isRunning = running) },
            acquireSystemProxy = { Result.failure(IllegalStateException("Registry policy denied the change")) },
        )

        assertTrue(controller.connect(TunnelConnectRequest("profile-1")).isFailure)
        assertFalse(running)
        assertEquals(TunnelPhase.Failed, controller.snapshot().phase)
    }

    @Test
    fun rollsBackXrayWhenTheHttpEndpointNeverBecomesReady() = runBlocking {
        var running = false
        var acquired = false
        val controller = DesktopTunnelController(
            configForProfile = { Result.success("{}") },
            startProcess = {
                running = true
                Result.success(DesktopXrayProcessState(isRunning = true))
            },
            stopProcess = {
                running = false
                Result.success(DesktopXrayProcessState(isRunning = false))
            },
            processState = { DesktopXrayProcessState(isRunning = running) },
            awaitSystemProxyEndpoint = { Result.failure(IllegalStateException("HTTP inbound did not start")) },
            acquireSystemProxy = {
                acquired = true
                Result.success(Unit)
            },
        )

        assertTrue(controller.connect(TunnelConnectRequest("profile-1")).isFailure)
        assertFalse(running)
        assertFalse(acquired)
        assertEquals(TunnelPhase.Failed, controller.snapshot().phase)
    }

    @Test
    fun reports_when_an_unexpected_xray_exit_cannot_restore_the_system_proxy() = runBlocking {
        var running = false
        val failedRestore = DesktopTunnelController(
            configForProfile = { Result.success("{}") },
            startProcess = {
                running = true
                Result.success(DesktopXrayProcessState(isRunning = true))
            },
            stopProcess = {
                running = false
                Result.success(DesktopXrayProcessState(isRunning = false))
            },
            processState = { DesktopXrayProcessState(isRunning = running) },
            releaseSystemProxy = { Result.failure(IllegalStateException("registry access denied")) },
        )
        assertTrue(failedRestore.connect(TunnelConnectRequest("profile-1")).isSuccess)
        running = false

        val snapshot = failedRestore.snapshot()
        assertEquals(TunnelPhase.Failed, snapshot.phase)
        assertTrue(snapshot.failure?.message.orEmpty().contains("system proxy"))
        assertTrue(snapshot.failure?.message.orEmpty().contains("registry access denied"))
    }
}
