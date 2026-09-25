// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.TunnelPhase
import platform.TunnelSnapshot

class ProxyHomeStoreTest {
    @Test
    fun togglingTunnelTransitionsState() {
        val store = ProxyHomeStore()
        assertEquals(TunnelPhase.Disconnected, store.uiState.value.tunnelSnapshot.phase)
        assertFalse(store.uiState.value.isRunning)

        store.dispatch(ProxyHomeAction.ToggleTunnel)
        assertEquals(ProxyConnectionPhase.Connecting, store.uiState.value.connectionPhase)
        assertTrue(store.uiState.value.isConnecting)

        store.updateState { it.copy(tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected)) }
        assertTrue(store.uiState.value.isRunning)

        store.dispatch(ProxyHomeAction.ToggleTunnel)
        assertEquals(TunnelPhase.Disconnected, store.uiState.value.tunnelSnapshot.phase)
    }

    @Test
    fun selectingServerUpdatesSelectedState() {
        val servers = listOf(
            ProxyServerSummary(id = "1", title = "Server 1", address = "1.1.1.1", protocol = "vless"),
            ProxyServerSummary(id = "2", title = "Server 2", address = "2.2.2.2", protocol = "vmess"),
        )
        val store = ProxyHomeStore(ProxyHomeUiState(servers = servers))

        store.dispatch(ProxyHomeAction.SelectServer("2"))
        val currentServers = store.uiState.value.servers
        assertFalse(currentServers[0].selected)
        assertTrue(currentServers[1].selected)
        assertEquals("Server 2", store.uiState.value.selectedServerTitle)
    }

    @Test
    fun searchVisibilityAndQueryTransitions() {
        val store = ProxyHomeStore()
        assertFalse(store.uiState.value.isSearchVisible)

        store.dispatch(ProxyHomeAction.SetSearchVisible(true))
        assertTrue(store.uiState.value.isSearchVisible)

        store.dispatch(ProxyHomeAction.SetSearchQuery("vless"))
        assertEquals("vless", store.uiState.value.searchQuery)
    }
}
