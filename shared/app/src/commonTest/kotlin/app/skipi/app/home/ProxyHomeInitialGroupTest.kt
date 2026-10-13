// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeInitialGroupTest {
    @Test
    fun initialSeedUsesTheDecodedSelectedRecordRawGroupId() {
        val initialGroupId = rawInitialProxyHomeGroupId(
            selectedServerId = 8,
            decodedServerGroupIds = listOf(7 to 7, 8 to 8, 9 to -2, 10 to 1),
            defaultGroupId = 1,
        )

        assertEquals(8, initialGroupId)
    }

    @Test
    fun missingSelectionOrMissingDecodedRecordUsesTheHostDefault() {
        val decodedServerGroupIds = listOf(7 to 7, 8 to 8)

        assertEquals(
            1,
            rawInitialProxyHomeGroupId(
                selectedServerId = null,
                decodedServerGroupIds = decodedServerGroupIds,
                defaultGroupId = 1,
            ),
        )
        assertEquals(
            1,
            rawInitialProxyHomeGroupId(
                selectedServerId = 99,
                decodedServerGroupIds = decodedServerGroupIds,
                defaultGroupId = 1,
            ),
        )
    }

    @Test
    fun unknownRawGroupIsPassedToExistingStoreFallbackAndValidRestoredGroupWins() {
        val unknownRawGroupId = rawInitialProxyHomeGroupId(
            selectedServerId = "8",
            decodedServerGroupIds = listOf("8" to "subscription:99"),
            defaultGroupId = "manual",
        )
        assertEquals("subscription:99", unknownRawGroupId)

        val input = ProxyHomeInput(
            selectedServerId = "8",
            servers = listOf(
                ProxyServerSummary(
                    id = "8",
                    title = "Selected server",
                    address = "selected.example",
                    protocol = "vless",
                    groupId = unknownRawGroupId,
                ),
            ),
            groups = listOf(
                group("subscription:7", "Earlier subscription"),
                group("manual", "Manual"),
            ),
        )
        val invalidRequestStore = createStore(
            input = input,
            presentation = ProxyHomePresentation(selectedGroupId = unknownRawGroupId),
        )
        assertEquals("subscription:7", invalidRequestStore.uiState.value.selectedGroupId)

        val restoredStore = createStore(
            input = input,
            presentation = ProxyHomePresentation(selectedGroupId = "manual"),
        )
        assertEquals("manual", restoredStore.uiState.value.selectedGroupId)
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

    private fun group(id: String, title: String) = ProxyGroupSummary(
        id = id,
        title = title,
        serverCount = 0,
        enabled = true,
        serverIds = emptySet(),
    )
}
