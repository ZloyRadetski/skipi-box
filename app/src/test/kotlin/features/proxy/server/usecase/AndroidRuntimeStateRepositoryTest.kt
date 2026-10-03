// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.skipi.app.runtime.RuntimeMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import platform.TunnelPhase
import platform.TunnelOperationStage
import platform.TunnelSnapshot
import platform.TunnelTraffic
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidRuntimeStateRepositoryTest {
    @Test
    fun cold_start_snapshot_uses_running_service_when_app_state_says_stopped() = runTest {
        val appState = MutableStateFlow(
            AppState(
                selectedProxyServerId = 41,
                proxyRunning = false,
            ),
        )
        val repository = AndroidRuntimeStateRepository(
            appState = appState,
            scope = backgroundScope,
            runtimeStatusChanges = MutableSharedFlow(extraBufferCapacity = 1),
            isRunning = { true },
            readTraffic = { TunnelTraffic(uploadBytes = 12, downloadBytes = 34) },
        )
        assertEquals(TunnelPhase.Connected, repository.state.value.tunnel.phase)
        assertEquals("41", repository.state.value.tunnel.profileId)
        assertEquals(TunnelTraffic(uploadBytes = 12, downloadBytes = 34), repository.state.value.tunnel.traffic)

        runCurrent()
        assertEquals(TunnelPhase.Connected, repository.state.value.tunnel.phase)
    }

    @Test
    fun background_service_and_selected_profile_changes_refresh_the_shared_snapshot() = runTest {
        val appState = MutableStateFlow(
            AppState(
                selectedProxyServerId = 41,
                proxyRunning = false,
            ),
        )
        val runtimeStatusChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var running = false
        val repository = AndroidRuntimeStateRepository(
            appState = appState,
            scope = backgroundScope,
            runtimeStatusChanges = runtimeStatusChanges,
            isRunning = { running },
            readTraffic = { TunnelTraffic(uploadBytes = 8, downloadBytes = 13) },
        )
        runCurrent()
        assertEquals(TunnelPhase.Disconnected, repository.state.value.tunnel.phase)

        running = true
        runtimeStatusChanges.emit(Unit)
        runCurrent()
        assertEquals(TunnelPhase.Connected, repository.state.value.tunnel.phase)
        assertEquals("41", repository.state.value.tunnel.profileId)

        appState.value = appState.value.copy(selectedProxyServerId = 42)
        runCurrent()
        assertEquals(TunnelPhase.Connected, repository.state.value.tunnel.phase)
        assertEquals("42", repository.state.value.tunnel.profileId)

        running = false
        runtimeStatusChanges.emit(Unit)
        runCurrent()
        assertEquals(TunnelPhase.Disconnected, repository.state.value.tunnel.phase)
        assertEquals(TunnelTraffic(), repository.state.value.tunnel.traffic)
    }

    @Test
    fun ignores_stale_app_state_and_zeroes_cached_traffic_when_service_is_stopped() = runTest {
        val appState = MutableStateFlow(
            AppState(
                selectedProxyServerId = 41,
                proxyRunning = true,
            ),
        )
        val runtimeStatusChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val repository = AndroidRuntimeStateRepository(
            appState = appState,
            scope = backgroundScope,
            runtimeStatusChanges = runtimeStatusChanges,
            isRunning = { false },
            // The cache can retain its last sample briefly after the service stops.
            readTraffic = { TunnelTraffic(uploadBytes = 90, downloadBytes = 120) },
        )
        assertEquals(TunnelPhase.Disconnected, repository.state.value.tunnel.phase)
        assertEquals(TunnelTraffic(), repository.state.value.tunnel.traffic)
        runCurrent()
        assertEquals(TunnelPhase.Disconnected, repository.state.value.tunnel.phase)
        assertEquals(TunnelTraffic(), repository.state.value.tunnel.traffic)
    }

    @Test
    fun proxy_running_changes_trigger_a_read_but_never_override_service_status() = runTest {
        val appState = MutableStateFlow(
            AppState(
                selectedProxyServerId = 41,
                proxyRunning = false,
            ),
        )
        var running = true
        val repository = AndroidRuntimeStateRepository(
            appState = appState,
            scope = backgroundScope,
            runtimeStatusChanges = MutableSharedFlow(extraBufferCapacity = 1),
            isRunning = { running },
            readTraffic = { TunnelTraffic(uploadBytes = 5, downloadBytes = 8) },
        )
        assertEquals(TunnelPhase.Connected, repository.state.value.tunnel.phase)

        running = false
        appState.value = appState.value.copy(proxyRunning = true)
        runCurrent()

        assertEquals(TunnelPhase.Disconnected, repository.state.value.tunnel.phase)
        assertEquals(TunnelTraffic(), repository.state.value.tunnel.traffic)
    }

    @Test
    fun status_read_failure_is_published_as_a_failed_snapshot() = runTest {
        val repository = AndroidRuntimeStateRepository(
            appState = MutableStateFlow(AppState(selectedProxyServerId = 41)),
            scope = backgroundScope,
            runtimeStatusChanges = MutableSharedFlow(extraBufferCapacity = 1),
            isRunning = { error("service status unavailable") },
            readTraffic = { TunnelTraffic() },
        )
        runCurrent()

        assertEquals(TunnelPhase.Failed, repository.state.value.tunnel.phase)
        assertEquals("android_runtime", repository.state.value.tunnel.failure?.code)
        assertEquals(TunnelOperationStage.ReadStatus, repository.state.value.tunnel.failure?.stage)
        assertEquals("service status unavailable", repository.state.value.tunnel.failure?.message)
    }

    @Test
    fun status_read_cancellation_is_rethrown_without_publishing_failure() = runTest {
        var cancelStatusRead = false
        val repository = AndroidRuntimeStateRepository(
            appState = MutableStateFlow(AppState(selectedProxyServerId = 41)),
            scope = backgroundScope,
            runtimeStatusChanges = MutableSharedFlow(extraBufferCapacity = 1),
            isRunning = {
                if (cancelStatusRead) throw CancellationException("cancelled")
                true
            },
            readTraffic = { TunnelTraffic() },
        )
        runCurrent()
        assertEquals(TunnelPhase.Connected, repository.state.value.tunnel.phase)

        cancelStatusRead = true
        assertFailsWith<CancellationException> {
            repository.refresh()
        }
        assertEquals(TunnelPhase.Connected, repository.state.value.tunnel.phase)
    }

    @Test
    fun refreshing_tunnel_status_preserves_other_runtime_fields() = runTest {
        val repository = AndroidRuntimeStateRepository(
            appState = MutableStateFlow(AppState(selectedProxyServerId = 41)),
            scope = backgroundScope,
            runtimeStatusChanges = MutableSharedFlow(extraBufferCapacity = 1),
            isRunning = { false },
            readTraffic = { TunnelTraffic() },
        )
        repository.update { runtime ->
            runtime.copy(
                latencyByServerId = mapOf(41 to 24L),
                testingServerIds = setOf(41),
                message = RuntimeMessage(text = "Profile check finished"),
            )
        }
        repository.refresh()

        assertEquals(mapOf(41 to 24L), repository.state.value.latencyByServerId)
        assertEquals(setOf(41), repository.state.value.testingServerIds)
        assertEquals(RuntimeMessage(text = "Profile check finished"), repository.state.value.message)

        repository.publishSnapshot(TunnelSnapshot(phase = TunnelPhase.Connected, profileId = "41"))
        assertEquals(TunnelPhase.Connected, repository.state.value.tunnel.phase)
        assertEquals(mapOf(41 to 24L), repository.state.value.latencyByServerId)
        assertEquals(setOf(41), repository.state.value.testingServerIds)
        assertEquals(RuntimeMessage(text = "Profile check finished"), repository.state.value.message)
    }
}
