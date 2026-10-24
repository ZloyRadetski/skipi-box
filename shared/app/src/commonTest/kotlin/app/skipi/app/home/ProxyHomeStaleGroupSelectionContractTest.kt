// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeStaleGroupSelectionContractTest {
    @Test
    fun staleRestoredGroupFallsBackToFirstVisibleGroupButFreshSelectionUsesSelectedServerGroup() {
        val input = ProxyHomeInput(
            selectedServerId = "server-2",
            servers = listOf(
                ProxyServerSummary(
                    id = "server-2",
                    title = "Selected server",
                    address = "selected.example",
                    protocol = "vless",
                    groupId = "group-2",
                ),
            ),
            groups = listOf(
                group("group-1", "First visible group"),
                group("group-2", "Selected server group", serverIds = setOf("server-2")),
            ),
        )

        // Android seeds a new Home selection from the selected server, but a restored,
        // parseable stale tab ID reaches proxyServerListGroups and falls back to its first tab.
        val freshStore = createStore(input, ProxyHomePresentation())
        val restoredStore = createStore(input, ProxyHomePresentation(selectedGroupId = "removed-group"))

        assertEquals("group-2", freshStore.uiState.value.selectedGroupId)
        assertEquals("group-1", restoredStore.uiState.value.selectedGroupId)
    }

    private fun createStore(
        input: ProxyHomeInput,
        presentation: ProxyHomePresentation,
    ) = ProxyHomeStore(
        initialInput = input,
        initialPresentation = presentation,
        scope = CoroutineScope(Dispatchers.Unconfined),
        effectHandler = ProxyHomeEffectHandler { Result.success(Unit) },
    )

    private fun group(
        id: String,
        title: String,
        serverIds: Set<String>? = emptySet(),
    ) = ProxyGroupSummary(
        id = id,
        title = title,
        serverCount = serverIds?.size ?: 0,
        enabled = true,
        serverIds = serverIds,
        kind = ProxyHomeGroupKind.Subscription,
    )
}
