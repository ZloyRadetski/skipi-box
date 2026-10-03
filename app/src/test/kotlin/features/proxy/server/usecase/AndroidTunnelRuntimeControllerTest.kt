// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import platform.TunnelCapability
import platform.TunnelConnectRequest
import platform.TunnelController
import platform.TunnelPhase
import platform.TunnelSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidTunnelRuntimeControllerTest {
    @Test
    fun publishes_controller_snapshot_and_delegates_tunnel_operations() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = FakeTunnelController()
            val runtimeRepository = AndroidRuntimeStateRepository(
                appState = MutableStateFlow(AppState(proxyRunning = false)),
                scope = scope,
                runtimeStatusChanges = MutableSharedFlow(extraBufferCapacity = 1),
                isRunning = { false },
                readTraffic = { platform.TunnelTraffic() },
            )
            val tunnelController = AndroidTunnelRuntimeController(controller, runtimeRepository)

            assertEquals(TunnelPhase.Disconnected, tunnelController.snapshot().phase)
            assertEquals(TunnelPhase.Disconnected, runtimeRepository.state.value.tunnel.phase)

            assertTrue(tunnelController.connect(TunnelConnectRequest("41")).isSuccess)
            assertEquals(1, controller.connectCalls)
            assertEquals(TunnelPhase.Connected, runtimeRepository.state.value.tunnel.phase)

            assertTrue(tunnelController.disconnect().isSuccess)
            assertEquals(1, controller.disconnectCalls)
            assertEquals(TunnelPhase.Disconnected, runtimeRepository.state.value.tunnel.phase)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun publishes_failed_snapshot_without_reporting_a_successful_connection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = FakeTunnelController(connectResult = Result.failure(IllegalStateException("VPN permission denied")))
            val runtimeRepository = AndroidRuntimeStateRepository(
                appState = MutableStateFlow(AppState()),
                scope = scope,
                runtimeStatusChanges = MutableSharedFlow(extraBufferCapacity = 1),
                isRunning = { false },
                readTraffic = { platform.TunnelTraffic() },
            )
            val tunnelController = AndroidTunnelRuntimeController(controller, runtimeRepository)

            assertTrue(tunnelController.connect(TunnelConnectRequest("41")).isFailure)
            assertEquals(TunnelPhase.Failed, runtimeRepository.state.value.tunnel.phase)
            assertEquals("VPN permission denied", runtimeRepository.state.value.tunnel.failure?.message)
        } finally {
            scope.cancel()
        }
    }

    private class FakeTunnelController(
        private val connectResult: Result<Unit> = Result.success(Unit),
    ) : TunnelController {
        var connectCalls = 0
        var disconnectCalls = 0
        private var current = TunnelSnapshot()

        override suspend fun connect(request: TunnelConnectRequest): Result<Unit> {
            connectCalls += 1
            current = if (connectResult.isSuccess) {
                TunnelSnapshot(phase = TunnelPhase.Connected, profileId = request.profileId)
            } else {
                TunnelSnapshot(
                    phase = TunnelPhase.Failed,
                    failure = platform.TunnelFailure(
                        code = "vpn_permission_denied",
                        message = connectResult.exceptionOrNull()?.message.orEmpty(),
                        recoverable = true,
                    ),
                )
            }
            return connectResult
        }

        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls += 1
            current = TunnelSnapshot()
            return Result.success(Unit)
        }

        override suspend fun snapshot(): TunnelSnapshot = current

        override fun capabilities(): Set<TunnelCapability> = emptySet()
    }
}
