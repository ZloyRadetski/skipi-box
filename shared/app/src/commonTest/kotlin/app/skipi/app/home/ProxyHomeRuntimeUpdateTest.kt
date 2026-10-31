// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import platform.TunnelPhase
import platform.TunnelSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Connecting and disconnecting only change runtime fields. With many subscriptions the page
 * projection must not be rebuilt for them, otherwise every phase change costs a pass over every
 * server of every subscription.
 */
class ProxyHomeRuntimeUpdateTest {
    private val groupCount = 30
    private val serversPerGroup = 40

    private fun catalog(): ProxyHomeInput {
        val groups = (0 until groupCount).map { g ->
            ProxyGroupSummary(
                id = "group-$g",
                title = "Group $g",
                serverCount = serversPerGroup,
                enabled = true,
                serverIds = (0 until serversPerGroup).mapTo(linkedSetOf()) { "s-$g-$it" },
            )
        }
        val servers = (0 until groupCount).flatMap { g ->
            (0 until serversPerGroup).map { s ->
                ProxyServerSummary(
                    id = "s-$g-$s",
                    title = "Server $g/$s",
                    address = "h$g-$s.example",
                    protocol = "vless",
                    groupId = "group-$g",
                    searchText = "Server $g/$s",
                    sortKey = "server $g/$s",
                )
            }
        }
        return ProxyHomeInput(selectedServerId = "s-3-4", groups = groups, servers = servers)
    }

    private fun store(input: ProxyHomeInput) = ProxyHomeStore(
        initialInput = input,
        scope = CoroutineScope(Dispatchers.Unconfined),
        effectHandler = ProxyHomeEffectHandler { Result.success(Unit) },
    )

    @Test
    fun connectingDoesNotRebuildThePagesOfEverySubscription() {
        val input = catalog()
        val store = store(input)
        val before = store.uiState.value

        store.updateInput(input.copy(tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connecting), tunnelBusy = true))
        val connecting = store.uiState.value
        store.updateInput(input.copy(tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected)))
        val connected = store.uiState.value

        assertEquals(TunnelPhase.Connecting, connecting.tunnelSnapshot.phase)
        assertEquals(TunnelPhase.Connected, connected.tunnelSnapshot.phase)
        assertSame(before.pages, connecting.pages)
        assertSame(before.pages, connected.pages)
        assertSame(before.servers, connected.servers)
    }

    @Test
    fun statusAndIpUpdatesKeepThePages() {
        val input = catalog()
        val store = store(input)
        val before = store.uiState.value

        store.updateInput(input.copy(statusMessage = "Connected", runtimeOutboundMetric = "12 ms"))

        assertSame(before.pages, store.uiState.value.pages)
    }

    @Test
    fun anEqualButNewCatalogListKeepsThePages() {
        val input = catalog()
        val store = store(input)
        val before = store.uiState.value

        store.updateInput(
            input.copy(servers = input.servers.toList(), groups = input.groups.toList(), tunnelBusy = true),
        )

        assertSame(before.pages, store.uiState.value.pages)
    }

    @Test
    fun aLatencyResultStillUpdatesTheServerRow() {
        val input = catalog()
        val store = store(input)
        val before = store.uiState.value

        val tested = input.servers.map { if (it.id == "s-5-7") it.copy(latencyMs = 42) else it }
        store.updateInput(input.copy(servers = tested))

        val after = store.uiState.value
        assertNotSame(before.pages, after.pages)
        assertEquals(42L, after.pages[5].servers.first { it.id == "s-5-7" }.latencyMs)
    }

    @Test
    fun searchSortAndSelectionStillRebuildThePages() {
        val input = catalog()
        val store = store(input)
        val before = store.uiState.value.pages

        store.dispatch(ProxyHomeAction.SetSearchQuery("Server 2/"))
        val searched = store.uiState.value.pages
        assertNotSame(before, searched)
        assertEquals(serversPerGroup, searched[2].servers.size)
        assertEquals(emptyList(), searched[0].servers)

        store.dispatch(ProxyHomeAction.SetSearchQuery(""))
        store.updateInput(input.copy(selectedServerId = "s-9-1"))
        val reselected = store.uiState.value.pages
        assertEquals(true, reselected[9].servers.first { it.id == "s-9-1" }.selected)
        assertEquals(false, reselected[3].servers.first { it.id == "s-3-4" }.selected)
    }
}
