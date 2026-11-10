// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Manual servers, the user's proxy groups and the balancers get the header a subscription has, minus refresh and settings. */
class ProxyHomeGroupHeaderTest {
    @Test
    fun aManualGroupWithServersHasAHeaderThatPingsTheGroup() {
        val state = stateOf(kind = ProxyHomeGroupKind.Manual)

        val header = state.groupHeader(state.pages.single())

        assertEquals("Group", header?.title)
        assertEquals(2, header?.serverCount)
        assertEquals(false, header?.pinging)
        assertEquals(ProxyHomeAction.TestGroup("g"), header?.pingAction)
    }

    @Test
    fun balancersGetTheSameHeader() {
        val state = stateOf(kind = ProxyHomeGroupKind.AutoBalancer)

        assertEquals(ProxyHomeAction.TestGroup("g"), state.groupHeader(state.pages.single())?.pingAction)
    }

    @Test
    fun subscriptionsAllAndOtherGroupsKeepTheirOwnHeaderOrNone() {
        listOf(ProxyHomeGroupKind.Subscription, ProxyHomeGroupKind.All, ProxyHomeGroupKind.Other).forEach { kind ->
            val state = stateOf(kind = kind)

            assertNull(state.groupHeader(state.pages.single()), kind.name)
        }
    }

    @Test
    fun anEmptyGroupShowsItsEmptyStateInsteadOfAHeader() {
        val state = stateOf(kind = ProxyHomeGroupKind.Manual, servers = emptyList())

        assertNull(state.groupHeader(state.pages.single()))
    }

    @Test
    fun whileTheGroupsServersAreBeingPingedTheButtonCancelsTheRun() {
        val state = stateOf(kind = ProxyHomeGroupKind.Manual, testing = setOf("a"), testingLatency = true)

        val header = state.groupHeader(state.pages.single())

        assertEquals(true, header?.pinging)
        assertEquals(ProxyHomeAction.CancelLatencyTests, header?.pingAction)
    }

    @Test
    fun aRunOnAnotherGroupDoesNotMarkThisHeaderAsPingingAndBlocksStartingANewOne() {
        val state = stateOf(kind = ProxyHomeGroupKind.Manual, testingLatency = true)

        val header = state.groupHeader(state.pages.single())

        assertEquals(false, header?.pinging)
        assertNull(header?.pingAction)
    }

    @Test
    fun aHostThatCannotPingGroupsOffersNoPingButKeepsTheHeader() {
        val state = stateOf(kind = ProxyHomeGroupKind.Manual, available = emptySet())

        val header = state.groupHeader(state.pages.single())

        assertEquals("Group", header?.title)
        assertNull(header?.pingAction)
    }

    @Test
    fun aDisabledGroupCannotBePinged() {
        val state = stateOf(kind = ProxyHomeGroupKind.Manual, enabled = false)

        assertNull(state.groupHeader(state.pages.single())?.pingAction)
    }

    private fun stateOf(
        kind: ProxyHomeGroupKind,
        servers: List<String> = listOf("a", "b"),
        testing: Set<String> = emptySet(),
        testingLatency: Boolean = false,
        enabled: Boolean = true,
        available: Set<ProxyHomeActionId> = setOf(ProxyHomeActionId.TestGroup, ProxyHomeActionId.CancelLatencyTests),
    ): ProxyHomeUiState = ProxyHomeStore(
        initialInput = ProxyHomeInput(
            groups = listOf(
                ProxyGroupSummary(
                    id = "g",
                    title = "Group",
                    serverCount = servers.size,
                    enabled = enabled,
                    serverIds = servers.toSet(),
                    kind = kind,
                ),
            ),
            servers = servers.map { id ->
                ProxyServerSummary(
                    id = id,
                    title = id,
                    address = "$id.example",
                    protocol = "vless",
                    latencyTesting = id in testing,
                )
            },
            isTestingLatency = testingLatency,
            availableActions = available,
        ),
        scope = CoroutineScope(Dispatchers.Unconfined),
        effectHandler = ProxyHomeEffectHandler { Result.success(Unit) },
    ).uiState.value
}
