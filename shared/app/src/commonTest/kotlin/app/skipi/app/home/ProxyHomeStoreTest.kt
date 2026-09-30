// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.TunnelPhase
import platform.TunnelSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyHomeStoreTest {
    @Test
    fun inputRefreshesAuthoritativeTunnelSelectionAndStatus() {
        val store = createStore(
            input = ProxyHomeInput(
                tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
                selectedServerId = "server-2",
                selectedServerTitle = "Server two",
                canToggleTunnel = false,
                tunnelBusy = true,
                statusMessage = "Connected",
                isStatusError = false,
            ),
        )

        assertEquals(TunnelPhase.Connected, store.uiState.value.tunnelSnapshot.phase)
        assertEquals("server-2", store.uiState.value.selectedServerId)
        assertEquals("Server two", store.uiState.value.selectedServerTitle)
        assertFalse(store.uiState.value.canToggleTunnel)
        assertTrue(store.uiState.value.tunnelBusy)

        store.updateInput(
            ProxyHomeInput(
                tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Failed),
                selectedServerId = "server-1",
                selectedServerTitle = "Server one",
                statusMessage = "VPN stopped",
                isStatusError = true,
            ),
        )

        assertEquals(TunnelPhase.Failed, store.uiState.value.tunnelSnapshot.phase)
        assertEquals("server-1", store.uiState.value.selectedServerId)
        assertEquals("Server one", store.uiState.value.selectedServerTitle)
        assertEquals("VPN stopped", store.uiState.value.statusMessage)
        assertTrue(store.uiState.value.isStatusError)
    }

    @Test
    fun localSearchSurvivesInputUpdatesAndMissingGroupSelectionFallsBackWithoutRestoringStaleGroup() {
        val input = inputWithGroups(
            selectedServerId = "server-2",
            groups = listOf(
                group("group-1", "Group one", setOf("server-1")),
                group("group-2", "Group two", setOf("server-2")),
                group("all", "All", setOf("server-1", "server-2")),
            ),
        )
        val store = createStore(input)

        assertEquals("group-2", store.uiState.value.selectedGroupId)
        store.dispatch(ProxyHomeAction.SetSearchQuery("beta"))
        store.dispatch(ProxyHomeAction.SetSearchVisible(true))
        store.dispatch(ProxyHomeAction.SelectGroup("all"))

        store.updateInput(input.copy(statusMessage = "Updated"))
        assertEquals("all", store.uiState.value.selectedGroupId)
        assertEquals("beta", store.uiState.value.searchQuery)
        assertTrue(store.uiState.value.isSearchVisible)
        assertEquals(listOf("server-2"), store.uiState.value.servers.map { it.id })

        store.dispatch(ProxyHomeAction.SetSearchQuery("Alpha"))
        assertEquals(listOf("server-1"), store.uiState.value.servers.map { it.id })
        assertEquals("server-2", store.uiState.value.selectedServer?.id)
        store.dispatch(ProxyHomeAction.SetSearchQuery("beta"))

        store.updateInput(
            input.copy(
                groups = input.groups.filterNot { it.id == "all" },
                statusMessage = "Still connected",
            ),
        )
        assertEquals("group-2", store.uiState.value.selectedGroupId)
        assertEquals("beta", store.uiState.value.searchQuery)
        assertTrue(store.uiState.value.isSearchVisible)

        store.updateInput(input.copy(statusMessage = "All group returned"))
        assertEquals("group-2", store.uiState.value.selectedGroupId)

        store.updateInput(
            input.copy(
                groups = input.groups.filterNot { it.id in setOf("all", "group-2") },
            ),
        )
        assertEquals("group-1", store.uiState.value.selectedGroupId)
    }

    @Test
    fun explicitGroupMembershipTakesPriorityOverStaleServerGroupId() {
        val server = ProxyServerSummary(
            id = "server-1",
            title = "Server one",
            address = "example.org",
            protocol = "vless",
            groupId = "group-1",
        )
        val store = createStore(
            ProxyHomeInput(
                selectedServerId = server.id,
                servers = listOf(server),
                groups = listOf(
                    group("group-1", "Old group", emptySet()),
                    group("group-2", "Current group", setOf(server.id)),
                ),
            ),
        )

        assertEquals("group-2", store.uiState.value.selectedGroupId)
        assertEquals(listOf(server.id), store.uiState.value.servers.map { it.id })
    }

    @Test
    fun tunnelActionIsDispatchedOnceAndDoesNotInventConnectionState() = runTest {
        val effects = RecordingEffectHandler()
        val input = ProxyHomeInput(
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Disconnected),
            availableActions = setOf(ProxyHomeActionId.ToggleTunnel),
        )
        val store = createStore(input, effects = effects, scope = this)

        store.dispatch(ProxyHomeAction.ToggleTunnel)
        runCurrent()

        assertEquals<ProxyHomeEffect>(ProxyHomeEffect.ToggleTunnel, effects.effects.single())
        assertEquals(TunnelPhase.Disconnected, store.uiState.value.tunnelSnapshot.phase)
    }

    @Test
    fun inputCanKeepToggleAvailableDuringBusyDesktopReconnect() = runTest {
        val effects = RecordingEffectHandler()
        val store = createStore(
            input = ProxyHomeInput(
                tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
                tunnelBusy = true,
                canToggleTunnel = true,
                availableActions = setOf(ProxyHomeActionId.ToggleTunnel),
            ),
            effects = effects,
            scope = this,
        )

        store.dispatch(ProxyHomeAction.ToggleTunnel)
        runCurrent()

        assertTrue(store.uiState.value.tunnelBusy)
        assertEquals<ProxyHomeEffect>(ProxyHomeEffect.ToggleTunnel, effects.effects.single())
        assertEquals(TunnelPhase.Connected, store.uiState.value.tunnelSnapshot.phase)
    }

    @Test
    fun allVisibleIntentUsesLatestSearchAndDispatchesOneEffect() = runTest {
        val effects = RecordingEffectHandler()
        val input = inputWithGroups(
            selectedServerId = "server-1",
            groups = listOf(group("group-1", "Group one", setOf("server-1", "server-2"))),
            availableActions = setOf(ProxyHomeActionId.TestVisibleServers),
        )
        val store = createStore(input, effects = effects, scope = this)

        store.dispatch(ProxyHomeAction.SetSearchQuery("Beta"))
        store.dispatch(ProxyHomeAction.TestAllVisibleServers)
        runCurrent()

        assertEquals<ProxyHomeEffect>(
            ProxyHomeEffect.TestVisibleServers(listOf("server-2")),
            effects.effects.single(),
        )
    }

    @Test
    fun operationBusyStateCoversSuspendedEffect() = runTest {
        val releaseEffect = CompletableDeferred<Unit>()
        val effects = mutableListOf<ProxyHomeEffect>()
        val handler = ProxyHomeEffectHandler { effect ->
            effects += effect
            releaseEffect.await()
            Result.success(Unit)
        }
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                availableActions = setOf(ProxyHomeActionId.RefreshAllSubscriptions),
            ),
            scope = this,
            effectHandler = handler,
        )

        store.dispatch(ProxyHomeAction.RefreshAllSubscriptions)
        runCurrent()
        assertEquals(setOf(ProxyHomeActionId.RefreshAllSubscriptions), store.uiState.value.busyActions)
        assertEquals(1, effects.size)

        releaseEffect.complete(Unit)
        runCurrent()
        assertTrue(store.uiState.value.busyActions.isEmpty())
    }

    @Test
    fun duplicateSelectionIsSuppressedImmediatelyWhileItsEffectIsBusy() = runTest {
        val releaseEffect = CompletableDeferred<Unit>()
        val effects = mutableListOf<ProxyHomeEffect>()
        val store = ProxyHomeStore(
            initialInput = inputWithGroups(
                selectedServerId = "server-1",
                groups = listOf(group("group-1", "Group one", setOf("server-1", "server-2"))),
                availableActions = setOf(ProxyHomeActionId.SelectServer),
            ),
            scope = this,
            effectHandler = ProxyHomeEffectHandler { effect ->
                effects += effect
                releaseEffect.await()
                Result.success(Unit)
            },
        )

        store.dispatch(ProxyHomeAction.SelectServer("server-2"))
        assertEquals(setOf(ProxyHomeActionId.SelectServer), store.uiState.value.busyActions)
        store.dispatch(ProxyHomeAction.SelectServer("server-2"))
        assertEquals("server-1", store.uiState.value.selectedServerId)
        assertNull(store.uiState.value.rejectedAction)

        runCurrent()
        assertEquals<ProxyHomeEffect>(ProxyHomeEffect.SelectServer("server-2"), effects.single())

        releaseEffect.complete(Unit)
        runCurrent()
        assertTrue(store.uiState.value.busyActions.isEmpty())
    }

    @Test
    fun tunnelToggleRemainsReentrantDuringAReconnectEffect() = runTest {
        val releaseEffect = CompletableDeferred<Unit>()
        val effects = mutableListOf<ProxyHomeEffect>()
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
                canToggleTunnel = true,
                tunnelBusy = true,
                availableActions = setOf(ProxyHomeActionId.ToggleTunnel),
            ),
            scope = this,
            effectHandler = ProxyHomeEffectHandler { effect ->
                effects += effect
                releaseEffect.await()
                Result.success(Unit)
            },
        )

        store.dispatch(ProxyHomeAction.ToggleTunnel)
        store.dispatch(ProxyHomeAction.ToggleTunnel)
        assertEquals(setOf(ProxyHomeActionId.ToggleTunnel), store.uiState.value.busyActions)

        runCurrent()
        assertEquals(2, effects.size)
        assertEquals<List<ProxyHomeEffect>>(
            listOf(ProxyHomeEffect.ToggleTunnel, ProxyHomeEffect.ToggleTunnel),
            effects,
        )
        assertEquals(TunnelPhase.Connected, store.uiState.value.tunnelSnapshot.phase)

        releaseEffect.complete(Unit)
        runCurrent()
        assertTrue(store.uiState.value.busyActions.isEmpty())
    }

    @Test
    fun unavailableOperationIsRejectedAndDoesNotReachTheHandler() = runTest {
        val effects = RecordingEffectHandler()
        val store = createStore(
            input = ProxyHomeInput(statusMessage = "Ready"),
            effects = effects,
            scope = this,
        )

        store.dispatch(ProxyHomeAction.RefreshAllSubscriptions)
        runCurrent()

        assertTrue(effects.effects.isEmpty())
        assertEquals(ProxyHomeActionId.RefreshAllSubscriptions, store.uiState.value.rejectedAction)
        assertEquals(ProxyHomeLocalError.Unavailable, store.uiState.value.localError)
        assertNull(store.uiState.value.statusMessage)
        assertTrue(store.uiState.value.isStatusError)

        store.dispatch(ProxyHomeAction.DismissMessage)
        assertNull(store.uiState.value.localError)
        assertNull(store.uiState.value.statusMessage)
    }

    @Test
    fun unsupportedVariantIsRejectedEvenWhenItsOperationIsAvailable() = runTest {
        val effects = RecordingEffectHandler()
        val store = createStore(
            input = ProxyHomeInput(
                availableActions = setOf(ProxyHomeActionId.AddServer),
                availableServerKinds = setOf(ProxyHomeServerKind.Custom),
            ),
            effects = effects,
            scope = this,
        )

        store.dispatch(ProxyHomeAction.AddServer(ProxyHomeServerKind.Vless))
        runCurrent()

        assertTrue(effects.effects.isEmpty())
        assertEquals(ProxyHomeActionId.AddServer, store.uiState.value.rejectedAction)
        assertEquals(ProxyHomeLocalError.VariantUnavailable, store.uiState.value.localError)
        assertNull(store.uiState.value.statusMessage)
    }

    @Test
    fun serverSpecificCopyAndStrategyCapabilitiesAreEnforced() = runTest {
        val effects = RecordingEffectHandler()
        val store = createStore(
            input = ProxyHomeInput(
                servers = listOf(
                    ProxyServerSummary(
                        id = "server-1",
                        title = "Alpha",
                        address = "alpha.example",
                        protocol = "custom",
                        availableCopyFormats = setOf(ProxyHomeCopyFormat.Url),
                    ),
                ),
                availableActions = setOf(
                    ProxyHomeActionId.CopyServer,
                    ProxyHomeActionId.OpenStrategyMemberPicker,
                ),
                availableCopyFormats = setOf(ProxyHomeCopyFormat.Url, ProxyHomeCopyFormat.FullJson),
            ),
            effects = effects,
            scope = this,
        )

        store.dispatch(ProxyHomeAction.CopyServer("server-1", ProxyHomeCopyFormat.FullJson))
        store.dispatch(ProxyHomeAction.OpenStrategyMemberPicker("server-1"))
        runCurrent()

        assertTrue(effects.effects.isEmpty())
        assertEquals(ProxyHomeActionId.OpenStrategyMemberPicker, store.uiState.value.rejectedAction)
    }

    @Test
    fun failedEffectSurfacesErrorAndDismissalHidesUnchangedSourceMessage() = runTest {
        val effects = RecordingEffectHandler().apply {
            result = Result.failure(IllegalStateException("Refresh failed"))
        }
        val input = ProxyHomeInput(
            statusMessage = "Subscription update failed",
            isStatusError = true,
            availableActions = setOf(ProxyHomeActionId.RefreshSubscription),
            groups = listOf(
                group(
                    id = "subscription-1",
                    title = "Subscription",
                    serverIds = emptySet(),
                    subscription = ProxySubscriptionSummary(
                        id = "subscription-1",
                        title = "Subscription",
                        serverCount = 0,
                        enabled = true,
                        refreshing = false,
                    ),
                ),
            ),
        )
        val store = createStore(input, effects = effects, scope = this)

        store.dispatch(ProxyHomeAction.RefreshSubscription("subscription-1"))
        runCurrent()
        assertNull(store.uiState.value.localError)
        assertEquals("Refresh failed", store.uiState.value.statusMessage)
        assertTrue(store.uiState.value.isStatusError)

        store.dispatch(ProxyHomeAction.DismissMessage)
        assertNull(store.uiState.value.statusMessage)
        store.updateInput(input)
        assertNull(store.uiState.value.statusMessage)

        store.updateInput(input.copy(statusMessage = "New message"))
        assertEquals("New message", store.uiState.value.statusMessage)
    }

    @Test
    fun failedEffectWithoutPlatformMessageUsesTypedOperationError() = runTest {
        val effects = RecordingEffectHandler().apply {
            result = Result.failure(IllegalStateException())
        }
        val store = createStore(
            input = ProxyHomeInput(
                availableActions = setOf(ProxyHomeActionId.RefreshAllSubscriptions),
            ),
            effects = effects,
            scope = this,
        )

        store.dispatch(ProxyHomeAction.RefreshAllSubscriptions)
        runCurrent()

        assertEquals(ProxyHomeLocalError.OperationFailed, store.uiState.value.localError)
        assertNull(store.uiState.value.statusMessage)
        assertTrue(store.uiState.value.isStatusError)
    }

    @Test
    fun serverSelectionIsNotOptimisticAndUpdatesOnlyFromInput() = runTest {
        val effects = RecordingEffectHandler()
        val input = inputWithGroups(
            selectedServerId = "server-1",
            availableActions = setOf(ProxyHomeActionId.SelectServer),
            groups = listOf(group("group-1", "Group one", setOf("server-1", "server-2"))),
        )
        val store = createStore(input, effects = effects, scope = this)

        store.dispatch(ProxyHomeAction.SelectServer("server-2"))
        assertEquals("server-1", store.uiState.value.selectedServerId)
        assertEquals(setOf(ProxyHomeActionId.SelectServer), store.uiState.value.busyActions)
        runCurrent()
        assertEquals("server-1", store.uiState.value.selectedServerId)
        assertTrue(store.uiState.value.busyActions.isEmpty())

        store.updateInput(input.copy(selectedServerId = "server-2", selectedServerTitle = "Beta"))
        assertEquals("server-2", store.uiState.value.selectedServerId)
        assertEquals("Beta", store.uiState.value.selectedServerTitle)
        assertEquals<ProxyHomeEffect>(ProxyHomeEffect.SelectServer("server-2"), effects.effects.single())
    }

    @Test
    fun pagesCalculateMembershipInOriginalOrderAndApplySearchFilter() {
        val servers = listOf(
            ProxyServerSummary(id = "s1", title = "Alpha", address = "10.0.0.1", protocol = "vless", groupId = "g1"),
            ProxyServerSummary(id = "s2", title = "Echo", address = "10.0.0.2", protocol = "socks", groupId = "g2"),
            ProxyServerSummary(id = "s3", title = "Gamma", address = "10.0.0.3", protocol = "trojan", groupId = "g1"),
        )
        val sub1 = ProxySubscriptionSummary(id = "g1", title = "Sub 1", serverCount = 2, enabled = true, refreshing = false)
        val groups = listOf(
            group("g1", "Group 1", serverIds = setOf("s1", "s3"), subscription = sub1),
            group("g2", "Group 2", serverIds = null),
        )
        val input = ProxyHomeInput(
            selectedServerId = "s3",
            servers = servers,
            groups = groups,
        )
        val store = createStore(input)

        val state = store.uiState.value
        assertEquals(2, state.pages.size)
        assertEquals("g1", state.pages[0].group.id)
        assertEquals(listOf("s1", "s3"), state.pages[0].servers.map { it.id })
        assertEquals(sub1, state.pages[0].subscription)
        assertTrue(state.pages[0].subscriptionExpanded)
        assertTrue(state.pages[0].servers.first { it.id == "s3" }.selected)
        assertFalse(state.pages[0].servers.first { it.id == "s1" }.selected)

        assertEquals("g2", state.pages[1].group.id)
        assertEquals(listOf("s2"), state.pages[1].servers.map { it.id })
        assertNull(state.pages[1].subscription)

        // Projection of selected page (g1)
        assertEquals(listOf("s1", "s3"), state.servers.map { it.id })
        assertEquals(sub1, state.subscription)

        // Search filtering across all pages: "a" matches Alpha (s1) and Gamma (s3), but not Echo (s2)
        store.dispatch(ProxyHomeAction.SetSearchQuery("a"))
        val searchState = store.uiState.value
        assertEquals(listOf("s1", "s3"), searchState.pages[0].servers.map { it.id })
        assertEquals(emptyList(), searchState.pages[1].servers.map { it.id })

        store.dispatch(ProxyHomeAction.SetSearchQuery("Echo"))
        val echoState = store.uiState.value
        assertEquals(emptyList(), echoState.pages[0].servers.map { it.id })
        assertEquals(listOf("s2"), echoState.pages[1].servers.map { it.id })
        // Selected server s3 is still resolved globally even when hidden by search
        assertEquals("s3", echoState.selectedServer?.id)
    }

    @Test
    fun sortingMaintainsStableNameTiesAndOrdersLatencyValues() {
        val servers = listOf(
            ProxyServerSummary(id = "s1", title = "alpha", address = "1", protocol = "p", latencyMs = null),
            ProxyServerSummary(id = "s2", title = "Alpha", address = "2", protocol = "p", latencyMs = 100),
            ProxyServerSummary(id = "s3", title = "beta", address = "3", protocol = "p", latencyMs = 50),
            ProxyServerSummary(id = "s4", title = "ALPHA", address = "4", protocol = "p", latencyMs = 50),
            ProxyServerSummary(id = "s5", title = "Charlie", address = "5", protocol = "p", latencyMs = null),
        )
        val groups = listOf(group("g1", "Group 1", serverIds = setOf("s1", "s2", "s3", "s4", "s5")))
        val baseInput = ProxyHomeInput(servers = servers, groups = groups)

        // Default sort: preserves original order
        val storeDefault = createStore(baseInput.copy(sortMode = ProxyHomeSortMode.Default))
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), storeDefault.uiState.value.pages[0].servers.map { it.id })

        // Name sort: case-insensitive with stable order on ties
        val storeName = createStore(baseInput.copy(sortMode = ProxyHomeSortMode.Name))
        assertEquals(listOf("s1", "s2", "s4", "s3", "s5"), storeName.uiState.value.pages[0].servers.map { it.id })

        // Latency sort matches Android's tie rule: canonical name breaks equal latency ties.
        val storeLatency = createStore(baseInput.copy(sortMode = ProxyHomeSortMode.Latency))
        assertEquals(listOf("s4", "s3", "s2", "s1", "s5"), storeLatency.uiState.value.pages[0].servers.map { it.id })
    }

    @Test
    fun latencySortPlacesTestingServersBeforeUnmeasuredAndKeepsFailuresLast() {
        val servers = listOf(
            ProxyServerSummary(id = "untested", title = "Alpha", address = "a", protocol = "p"),
            ProxyServerSummary(id = "testing", title = "Zulu", address = "z", protocol = "p", latencyTesting = true),
            ProxyServerSummary(id = "failed", title = "Beta", address = "b", protocol = "p", latencyError = true),
            ProxyServerSummary(id = "slow", title = "Slow", address = "s", protocol = "p", latencyMs = 200),
            ProxyServerSummary(id = "fast-z", title = "Zulu", address = "fz", protocol = "p", latencyMs = 50),
            ProxyServerSummary(id = "fast-a", title = "Alpha", address = "fa", protocol = "p", latencyMs = 50),
        )
        val store = createStore(
            ProxyHomeInput(
                servers = servers,
                groups = listOf(group("all", "All", servers.mapTo(linkedSetOf()) { it.id })),
                sortMode = ProxyHomeSortMode.Latency,
            ),
        )

        assertEquals(
            listOf("fast-a", "fast-z", "slow", "testing", "untested", "failed"),
            store.uiState.value.pages.single().servers.map { it.id },
        )
    }

    @Test
    fun toggleSubscriptionExpandedIsLocalSurvivesUpdatesAndPrunesMissingGroups() = runTest {
        val effects = RecordingEffectHandler()
        val sub1 = ProxySubscriptionSummary(id = "g1", title = "Sub 1", serverCount = 1, enabled = true, refreshing = false)
        val sub2 = ProxySubscriptionSummary(id = "g2", title = "Sub 2", serverCount = 1, enabled = true, refreshing = false)
        val input = ProxyHomeInput(
            groups = listOf(
                group("g1", "Group 1", serverIds = setOf("s1"), subscription = sub1),
                group("g2", "Group 2", serverIds = setOf("s2"), subscription = sub2),
            ),
            servers = listOf(
                ProxyServerSummary(id = "s1", title = "S1", address = "a1", protocol = "p", groupId = "g1"),
                ProxyServerSummary(id = "s2", title = "S2", address = "a2", protocol = "p", groupId = "g2"),
            ),
        )
        val store = createStore(input, effects = effects, scope = this)

        // Initially cards are expanded
        assertTrue(store.uiState.value.pages[0].subscriptionExpanded)
        assertTrue(store.uiState.value.pages[1].subscriptionExpanded)

        // Toggle g1 to collapsed
        store.dispatch(ProxyHomeAction.ToggleSubscriptionExpanded("g1"))
        assertFalse(store.uiState.value.pages[0].subscriptionExpanded)
        assertTrue(store.uiState.value.pages[1].subscriptionExpanded)
        // Must NOT call effect handler
        assertTrue(effects.effects.isEmpty())

        // Switch selected group - collapsed state persists
        store.dispatch(ProxyHomeAction.SelectGroup("g2"))
        assertFalse(store.uiState.value.pages[0].subscriptionExpanded)
        assertTrue(store.uiState.value.pages[1].subscriptionExpanded)

        // Update input with status - collapsed state persists across input refreshes
        store.updateInput(input.copy(statusMessage = "Refreshed"))
        assertFalse(store.uiState.value.pages[0].subscriptionExpanded)
        assertTrue(store.uiState.value.pages[1].subscriptionExpanded)

        // Remove g1 from input - pruning happens
        store.updateInput(input.copy(groups = listOf(group("g2", "Group 2", serverIds = setOf("s2"), subscription = sub2))))
        assertEquals(1, store.uiState.value.pages.size)
        assertTrue(store.uiState.value.pages[0].subscriptionExpanded)

        // Re-add g1 - since it was pruned when removed, it returns to default (expanded)
        store.updateInput(input)
        assertTrue(store.uiState.value.pages[0].subscriptionExpanded)
        assertTrue(store.uiState.value.pages[1].subscriptionExpanded)
    }

    @Test
    fun serverSelectionFailureSurfacesErrorWithoutModifyingSelectedServer() = runTest {
        val effects = RecordingEffectHandler().apply {
            result = Result.failure(IllegalStateException("Failed to connect"))
        }
        val input = inputWithGroups(
            selectedServerId = "server-1",
            availableActions = setOf(ProxyHomeActionId.SelectServer),
            groups = listOf(group("group-1", "Group one", setOf("server-1", "server-2"))),
        )
        val store = createStore(input, effects = effects, scope = this)

        store.dispatch(ProxyHomeAction.SelectServer("server-2"))
        assertEquals("server-1", store.uiState.value.selectedServerId)
        assertEquals(setOf(ProxyHomeActionId.SelectServer), store.uiState.value.busyActions)

        runCurrent()
        assertEquals("server-1", store.uiState.value.selectedServerId)
        assertTrue(store.uiState.value.busyActions.isEmpty())
        assertEquals("Failed to connect", store.uiState.value.statusMessage)
        assertTrue(store.uiState.value.isStatusError)
    }

    @Test
    fun displayOptionsRequestedColumnsAreNormalizedOnUiStateBoundary() {
        val storeZero = createStore(
            ProxyHomeInput(displayOptions = ProxyHomeDisplayOptions(requestedColumns = 0)),
        )
        assertEquals(1, storeZero.uiState.value.displayOptions.requestedColumns)

        val storeNegative = createStore(
            ProxyHomeInput(displayOptions = ProxyHomeDisplayOptions(requestedColumns = -5)),
        )
        assertEquals(1, storeNegative.uiState.value.displayOptions.requestedColumns)

        val storeTwo = createStore(
            ProxyHomeInput(displayOptions = ProxyHomeDisplayOptions(requestedColumns = 2)),
        )
        assertEquals(2, storeTwo.uiState.value.displayOptions.requestedColumns)

        val storeFive = createStore(
            ProxyHomeInput(displayOptions = ProxyHomeDisplayOptions(requestedColumns = 5)),
        )
        assertEquals(3, storeFive.uiState.value.displayOptions.requestedColumns)
    }

    @Test
    fun proxyGroupSummaryKindDefaultsToOther() {
        val grp = group("g1", "Group")
        assertEquals(ProxyHomeGroupKind.Other, grp.kind)
        val customGrp = grp.copy(kind = ProxyHomeGroupKind.Subscription)
        assertEquals(ProxyHomeGroupKind.Subscription, customGrp.kind)
    }

    @Test
    fun nameSortingUsesCanonicalSortKeyAndDoesNotReorderByFlagStrippedDisplayTitle() {
        val servers = listOf(
            ProxyServerSummary(
                id = "s1",
                title = "Alpha",
                address = "alpha.example",
                protocol = "vless",
                flag = "🇺🇸",
                sortKey = "🇺🇸 Alpha",
            ),
            ProxyServerSummary(
                id = "s2",
                title = "Zulu",
                address = "zulu.example",
                protocol = "vless",
                flag = null,
                sortKey = "Zulu",
            ),
        )
        val groups = listOf(group("g1", "Group 1", serverIds = setOf("s1", "s2")))

        val store = createStore(
            ProxyHomeInput(
                servers = servers,
                groups = groups,
                sortMode = ProxyHomeSortMode.Name,
            ),
        )

        // In Unicode, 'Z' (90) comes before '🇺' (127482).
        // Sorting by canonical sortKey keeps Zulu before Alpha, ignoring stripped display title.
        assertEquals(listOf("s2", "s1"), store.uiState.value.pages[0].servers.map { it.id })
        assertEquals("Zulu", store.uiState.value.pages[0].servers[0].title)
        assertEquals("Alpha", store.uiState.value.pages[0].servers[1].title)
        assertEquals(listOf("s2", "s1"), store.uiState.value.servers.map { it.id })
    }

    @Test
    fun nameSortingFallsBackToDisplayTitleWhenSortKeyIsNull() {
        val servers = listOf(
            ProxyServerSummary(id = "s1", title = "Zulu", address = "z", protocol = "p", sortKey = null),
            ProxyServerSummary(id = "s2", title = "Alpha", address = "a", protocol = "p", sortKey = null),
        )
        val groups = listOf(group("g1", "Group 1", serverIds = setOf("s1", "s2")))

        val store = createStore(
            ProxyHomeInput(
                servers = servers,
                groups = groups,
                sortMode = ProxyHomeSortMode.Name,
            ),
        )

        // When sortKey is null, falls back to display title: Alpha (s2) before Zulu (s1)
        assertEquals(listOf("s2", "s1"), store.uiState.value.pages[0].servers.map { it.id })
    }

    @Test
    fun reusablePageConstructionHandlesOverlappingGroupsEmptyGroupsAndLegacyMembership() {
        val s1 = ProxyServerSummary(id = "s1", title = "Alpha", address = "1", protocol = "p", groupId = "legacy")
        val s2 = ProxyServerSummary(id = "s2", title = "Beta", address = "2", protocol = "p", groupId = "g1")
        val s3 = ProxyServerSummary(id = "s3", title = "Gamma", address = "3", protocol = "p", groupId = "g2")
        val s4 = ProxyServerSummary(id = "s4", title = "Delta", address = "4", protocol = "p", groupId = "legacy")

        val groups = listOf(
            group("all", "All", serverIds = setOf("s1", "s2", "s3", "s4")),
            group("g1", "Group 1", serverIds = setOf("s1", "s2")),
            group("g2", "Group 2", serverIds = setOf("s2", "s3")), // overlaps on s2
            group("empty", "Empty", serverIds = emptySet()),
            group("legacy", "Legacy", serverIds = null), // legacy membership from server.groupId
        )

        val store = createStore(
            ProxyHomeInput(
                selectedServerId = "s2",
                servers = listOf(s1, s2, s3, s4),
                groups = groups,
                sortMode = ProxyHomeSortMode.Default,
            ),
        )

        val state = store.uiState.value
        assertEquals(5, state.pages.size)

        // All group
        assertEquals(listOf("s1", "s2", "s3", "s4"), state.pages[0].servers.map { it.id })
        // Group 1
        assertEquals(listOf("s1", "s2"), state.pages[1].servers.map { it.id })
        // Group 2 (overlapping membership on s2)
        assertEquals(listOf("s2", "s3"), state.pages[2].servers.map { it.id })
        // Empty group
        assertTrue(state.pages[3].servers.isEmpty())
        // Legacy group (s1 and s4 have groupId == "legacy")
        assertEquals(listOf("s1", "s4"), state.pages[4].servers.map { it.id })

        // Selection is correctly reflected in pages
        assertTrue(state.pages[0].servers.first { it.id == "s2" }.selected)
        assertFalse(state.pages[0].servers.first { it.id == "s1" }.selected)
        assertTrue(state.pages[1].servers.first { it.id == "s2" }.selected)
        assertTrue(state.pages[2].servers.first { it.id == "s2" }.selected)
    }

    @Test
    fun globalSortingAndSearchFilterAppliedOnceAndPreservesOriginalOrderOnTies() {
        val s1 = ProxyServerSummary(id = "s1", title = "Echo 1", address = "10.0.0.1", protocol = "vless", latencyMs = 100, sortKey = "Echo")
        val s2 = ProxyServerSummary(id = "s2", title = "Echo 2", address = "10.0.0.2", protocol = "vless", latencyMs = 100, sortKey = "Echo")
        val s3 = ProxyServerSummary(id = "s3", title = "Alpha", address = "10.0.0.3", protocol = "vless", latencyMs = 50, sortKey = "Alpha")
        val s4 = ProxyServerSummary(id = "s4", title = "Beta", address = "10.0.0.4", protocol = "vless", latencyMs = null, sortKey = "Beta")

        val g1 = group("g1", "Group 1", serverIds = setOf("s1", "s2", "s3", "s4"))
        val baseInput = ProxyHomeInput(
            selectedServerId = "s4",
            servers = listOf(s1, s2, s3, s4),
            groups = listOf(g1),
        )

        // 1. Name sort with tie between s1 and s2: Alpha (s3) -> Beta (s4) -> Echo 1 (s1) -> Echo 2 (s2)
        val nameStore = createStore(baseInput.copy(sortMode = ProxyHomeSortMode.Name))
        assertEquals(listOf("s3", "s4", "s1", "s2"), nameStore.uiState.value.pages[0].servers.map { it.id })

        // 2. Latency sort with tie between s1 and s2 (both 100ms): Alpha 50ms (s3) -> s1 (100ms) -> s2 (100ms) -> s4 (null)
        val latencyStore = createStore(baseInput.copy(sortMode = ProxyHomeSortMode.Latency))
        assertEquals(listOf("s3", "s1", "s2", "s4"), latencyStore.uiState.value.pages[0].servers.map { it.id })

        // 3. Search query filters globally once and selectedServer outside filter remains resolved
        nameStore.dispatch(ProxyHomeAction.SetSearchQuery("Echo"))
        val searchedState = nameStore.uiState.value
        assertEquals(listOf("s1", "s2"), searchedState.pages[0].servers.map { it.id })
        // s4 is hidden by search query "Echo", but uiState.selectedServer is still resolved
        assertEquals("s4", searchedState.selectedServer?.id)
        assertTrue(searchedState.selectedServer?.selected == true)
    }

    private fun createStore(
        input: ProxyHomeInput = ProxyHomeInput(),
        effects: RecordingEffectHandler = RecordingEffectHandler(),
        scope: CoroutineScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
    ) = ProxyHomeStore(
        initialInput = input,
        scope = scope,
        effectHandler = effects,
    )

    private fun inputWithGroups(
        selectedServerId: String?,
        groups: List<ProxyGroupSummary>,
        availableActions: Set<ProxyHomeActionId> = emptySet(),
    ) = ProxyHomeInput(
        selectedServerId = selectedServerId,
        servers = listOf(
            ProxyServerSummary(
                id = "server-1",
                title = "Alpha",
                address = "alpha.example",
                protocol = "vless",
                groupId = "group-1",
                searchText = "Alpha",
            ),
            ProxyServerSummary(
                id = "server-2",
                title = "Beta",
                address = "beta.example",
                protocol = "vmess",
                groupId = "group-2",
                searchText = "Beta",
            ),
        ),
        groups = groups,
        availableActions = availableActions,
    )

    private fun group(
        id: String,
        title: String,
        serverIds: Set<String>? = null,
        subscription: ProxySubscriptionSummary? = null,
    ) = ProxyGroupSummary(
        id = id,
        title = title,
        serverCount = serverIds?.size ?: 0,
        enabled = true,
        serverIds = serverIds,
        subscription = subscription,
    )

    private class RecordingEffectHandler : ProxyHomeEffectHandler {
        val effects = mutableListOf<ProxyHomeEffect>()
        var result: Result<Unit> = Result.success(Unit)

        override suspend fun handle(effect: ProxyHomeEffect): Result<Unit> {
            effects += effect
            return result
        }
    }
}
