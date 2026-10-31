// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.TunnelPhase
import platform.TunnelSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectedLatencyActionsTest {
    @Test
    fun theGroupStepOfASubscriptionServerPingsItsSubscription() {
        val subscription = ProxySubscriptionSummary(id = "sub-7", title = "Work", serverCount = 2, enabled = true, refreshing = false)
        val state = stateOf(
            selected = "a",
            groups = listOf(
                ProxyGroupSummary(
                    id = "all", title = "All", serverCount = 3, enabled = true,
                    serverIds = setOf("a", "b", "c"), kind = ProxyHomeGroupKind.Manual,
                ),
                ProxyGroupSummary(
                    id = "sub-7", title = "Work", serverCount = 2, enabled = true,
                    serverIds = setOf("a", "b"), subscription = subscription, kind = ProxyHomeGroupKind.Subscription,
                ),
            ),
            serverGroupId = "sub-7",
        )

        assertEquals(ProxyHomeAction.PingSubscription("sub-7"), state.connectedLatencyGroupAction())
    }

    @Test
    fun theGroupStepOfAManualServerTestsItsGroup() {
        val state = stateOf(
            selected = "a",
            groups = listOf(
                ProxyGroupSummary(
                    id = "manual", title = "Mine", serverCount = 2, enabled = true,
                    serverIds = setOf("a", "b"), kind = ProxyHomeGroupKind.Manual,
                ),
            ),
            serverGroupId = "manual",
        )

        assertEquals(ProxyHomeAction.TestGroup("manual"), state.connectedLatencyGroupAction())
    }

    @Test
    fun theGroupStepFallsBackToTheGroupThatContainsTheServer() {
        val state = stateOf(
            selected = "a",
            groups = listOf(
                ProxyGroupSummary(
                    id = "other", title = "Other", serverCount = 1, enabled = true,
                    serverIds = setOf("c"), kind = ProxyHomeGroupKind.Manual,
                ),
                ProxyGroupSummary(
                    id = "holder", title = "Holder", serverCount = 2, enabled = true,
                    serverIds = setOf("a", "b"), kind = ProxyHomeGroupKind.Manual,
                ),
            ),
            serverGroupId = "gone",
        )

        assertEquals(ProxyHomeAction.TestGroup("holder"), state.connectedLatencyGroupAction())
    }

    @Test
    fun theGroupStepHasNothingToDoWithoutASelectedServerOrGroup() {
        assertNull(stateOf(selected = null, groups = emptyList(), serverGroupId = "x").connectedLatencyGroupAction())
        assertNull(stateOf(selected = "a", groups = emptyList(), serverGroupId = "x").connectedLatencyGroupAction())
    }

    @Test
    fun testingTheConnectionIsOneEffectForTheGivenServerWhenTheHostOffersIt() = runTest {
        val effects = mutableListOf<ProxyHomeEffect>()
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
                availableActions = setOf(ProxyHomeActionId.TestConnection),
            ),
            scope = this,
            effectHandler = ProxyHomeEffectHandler { effect -> effects += effect; Result.success(Unit) },
        )

        store.dispatch(ProxyHomeAction.TestConnection("a"))
        runCurrent()

        assertEquals(listOf<ProxyHomeEffect>(ProxyHomeEffect.TestConnection("a")), effects)
    }

    @Test
    fun testingTheConnectionIsIgnoredWhenTheHostDoesNotOfferIt() = runTest {
        val effects = mutableListOf<ProxyHomeEffect>()
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(),
            scope = this,
            effectHandler = ProxyHomeEffectHandler { effect -> effects += effect; Result.success(Unit) },
        )

        store.dispatch(ProxyHomeAction.TestConnection("a"))
        runCurrent()

        assertTrue(effects.isEmpty())
    }

    private fun stateOf(
        selected: String?,
        groups: List<ProxyGroupSummary>,
        serverGroupId: String,
    ): ProxyHomeUiState = ProxyHomeStore(
        initialInput = ProxyHomeInput(
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
            selectedServerId = selected,
            servers = listOf(
                ProxyServerSummary(
                    id = "a", title = "A", address = "a.example", protocol = "vless", groupId = serverGroupId,
                ),
            ),
            groups = groups,
        ),
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        effectHandler = ProxyHomeEffectHandler { Result.success(Unit) },
    ).uiState.value
}
