// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerState
import engine.proxy.ProxyEngineStatus
import features.proxy.server.model.Custom
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.TunnelCapability
import platform.TunnelConnectRequest
import platform.TunnelConfiguration
import platform.TunnelPhase
import platform.TunnelOperationException
import platform.TunnelOperationStage
import platform.TunnelTraffic

class AndroidTunnelControllerTest {
    @Test
    fun connects_selected_profile_and_reports_shared_traffic() = runBlocking {
        val fixture = Fixture()

        assertTrue(fixture.controller.connect(TunnelConnectRequest("41")).isSuccess)
        assertEquals(41, fixture.startedProfileId)
        assertTrue(fixture.state.proxyRunning)
        assertEquals("2080", fixture.state.localProxyPort)

        val snapshot = fixture.controller.snapshot()
        assertEquals(TunnelPhase.Connected, snapshot.phase)
        assertEquals(TunnelTraffic(uploadBytes = 7L, downloadBytes = 8L), snapshot.traffic)
        assertTrue(fixture.controller.supports(TunnelCapability.Tun))
        assertFalse(fixture.controller.supports(TunnelCapability.SystemProxy))
        assertFalse(fixture.controller.supports(TunnelCapability.PreparedConfiguration))
        assertEquals("41", snapshot.profileId)
    }

    @Test
    fun disconnects_through_the_same_shared_contract() = runBlocking {
        val fixture = Fixture(running = true)

        assertTrue(fixture.controller.disconnect().isSuccess)
        assertEquals(1, fixture.stopCalls)
        assertFalse(fixture.state.proxyRunning)
        assertEquals(TunnelPhase.Disconnected, fixture.controller.snapshot().phase)
    }

    @Test
    fun rejects_a_profile_that_is_not_present_in_the_android_state() = runBlocking {
        val fixture = Fixture()

        val failure = fixture.controller.connect(TunnelConnectRequest("404")).exceptionOrNull()
        assertTrue(failure is TunnelOperationException)
        assertEquals("profile_unavailable", (failure as TunnelOperationException).failure.code)
        assertEquals("41", fixture.state.selectedProxyServerId.toString())
        assertEquals(null, fixture.startedProfileId)
        assertFalse(fixture.state.proxyRunning)
    }

    @Test
    fun rejects_prepared_configuration_when_android_profile_adapter_does_not_support_it() = runBlocking {
        val fixture = Fixture()

        val error = fixture.controller.connect(
            TunnelConnectRequest(
                profileId = "41",
                configuration = TunnelConfiguration("{}"),
            ),
        ).exceptionOrNull() as TunnelOperationException

        assertEquals("capability_unavailable", error.failure.code)
        assertEquals(TunnelOperationStage.PrepareConfiguration, error.failure.stage)
        assertEquals(TunnelCapability.PreparedConfiguration.name, error.failure.platformCode)
        assertEquals(null, fixture.startedProfileId)
    }

    private class Fixture(
        running: Boolean = false,
    ) {
        var state = AppState(
            proxyServers = listOf(
                ProxyServerState(
                    id = 41,
                    server = Custom(),
                    groupId = 0,
                ),
            ),
            selectedProxyServerId = 41,
            proxyRunning = running,
        )
        var nativeRunning = running
        var startedProfileId: Int? = null
        var stopCalls = 0

        val controller = AndroidTunnelController(
            readState = { state },
            updateState = { transform -> state = transform(state) },
            prepareForConnection = { current -> current },
            readStatus = { Result.success(ProxyEngineStatus(running = nativeRunning)) },
            startService = { requestedState, server ->
                startedProfileId = server.id
                nativeRunning = true
                ProxyServiceResult.Success(
                    proxyRunning = true,
                    appState = requestedState.copy(localProxyPort = "2080"),
                )
            },
            stopService = {
                stopCalls += 1
                nativeRunning = false
                ProxyServiceResult.Success(proxyRunning = false)
            },
            readTraffic = { TunnelTraffic(uploadBytes = 7L, downloadBytes = 8L) },
        )
    }
}
