// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import platform.TunnelPhase
import platform.TunnelSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The compact ping button measures only the selected server, never the page or the group. */
class CompactPingActionTest {
    @Test
    fun whileConnectedItMeasuresTheLiveConnectionOfTheSelectedServer() {
        assertEquals(
            ProxyHomeAction.TestConnection("a"),
            stateOf(TunnelPhase.Connected, selected = "a").compactPingAction(),
        )
    }

    @Test
    fun repeatedUseWhileConnectedStillMeasuresOnlyTheConnection() {
        val state = stateOf(TunnelPhase.Connected, selected = "a")
        repeat(3) { assertEquals(ProxyHomeAction.TestConnection("a"), state.compactPingAction()) }
    }

    @Test
    fun whileDisconnectedItPingsOnlyTheSelectedServerNotTheVisiblePage() {
        assertEquals(
            ProxyHomeAction.TestServer("a"),
            stateOf(TunnelPhase.Disconnected, selected = "a").compactPingAction(),
        )
    }

    @Test
    fun aSelectedServerThatCannotBePingedOnItsOwnHasNoDisconnectedAction() {
        assertNull(stateOf(TunnelPhase.Disconnected, selected = "a", canTest = false).compactPingAction())
    }

    @Test
    fun withoutASelectedServerThereIsNothingToPing() {
        assertNull(stateOf(TunnelPhase.Connected, selected = null).compactPingAction())
        assertNull(stateOf(TunnelPhase.Disconnected, selected = null).compactPingAction())
    }

    private fun stateOf(phase: TunnelPhase, selected: String?, canTest: Boolean = true): ProxyHomeUiState = ProxyHomeStore(
        initialInput = ProxyHomeInput(
            tunnelSnapshot = TunnelSnapshot(phase = phase),
            selectedServerId = selected,
            servers = listOf(
                ProxyServerSummary(id = "a", title = "A", address = "a.example", protocol = "vless", canTest = canTest),
                ProxyServerSummary(id = "b", title = "B", address = "b.example", protocol = "vless"),
            ),
        ),
        scope = CoroutineScope(Dispatchers.Unconfined),
        effectHandler = ProxyHomeEffectHandler { Result.success(Unit) },
    ).uiState.value
}
