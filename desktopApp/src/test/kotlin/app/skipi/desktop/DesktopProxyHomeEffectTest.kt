// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeEffectHandler
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyServerSummary
import app.skipi.ui.home.dialogs.SkipiAddSourceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import platform.TunnelPhase

class DesktopProxyHomeEffectTest {
    @Test
    fun subscriptionTrafficSumDoesNotOverflowOrBecomeNegative() {
        assertEquals(
            Long.MAX_VALUE,
            desktopSubscriptionUsedBytes(Long.MAX_VALUE - 5, 10),
        )
        assertEquals(42L, desktopSubscriptionUsedBytes(-10, 42))
    }

    @Test
    fun storeDispatchInvokesTunnelAndSelectionCallbacksOnceAndKeepsTunnelHostOwned() {
        var tunnelToggles = 0
        val selectedServerIds = mutableListOf<Int>()
        val effects = DesktopProxyHomeEffectContext(
            onToggleTunnel = { tunnelToggles += 1 },
            onSelectServer = { selectedServerIds += it },
        )
        val input = ProxyHomeInput(
            tunnelSnapshot = desktopProxyHomeTunnelSnapshot(running = true, connecting = true),
            canToggleTunnel = true,
            tunnelBusy = true,
            servers = listOf(
                ProxyServerSummary("37", "Server", "proxy.example", "http"),
            ),
            availableActions = setOf(ProxyHomeActionId.ToggleTunnel, ProxyHomeActionId.SelectServer),
        )
        val store = ProxyHomeStore(
            initialInput = input,
            scope = CoroutineScope(Dispatchers.Unconfined),
            effectHandler = ProxyHomeEffectHandler(effects::handle),
        )

        store.dispatch(ProxyHomeAction.ToggleTunnel)
        store.dispatch(ProxyHomeAction.SelectServer("37"))

        assertEquals(1, tunnelToggles)
        assertEquals(listOf(37), selectedServerIds)
        assertEquals(TunnelPhase.Connected, store.uiState.value.tunnelSnapshot.phase)
        assertTrue(store.uiState.value.tunnelBusy)

        store.updateInput(
            input.copy(
                tunnelSnapshot = desktopProxyHomeTunnelSnapshot(running = false, connecting = false),
                tunnelBusy = false,
            ),
        )
        assertEquals(TunnelPhase.Disconnected, store.uiState.value.tunnelSnapshot.phase)
        assertEquals(1, tunnelToggles)
    }

    @Test
    fun snapshotUsesOnlyHostRunningAndConnectingValues() {
        assertEquals(
            TunnelPhase.Connected,
            desktopProxyHomeTunnelSnapshot(running = true, connecting = true).phase,
        )
        assertEquals(
            TunnelPhase.Connecting,
            desktopProxyHomeTunnelSnapshot(running = false, connecting = true).phase,
        )
        assertEquals(
            TunnelPhase.Disconnected,
            desktopProxyHomeTunnelSnapshot(running = false, connecting = false).phase,
        )
    }

    @Test
    fun sortEffectChangesOnlyTheHostPreferenceCallback() {
        val modes = mutableListOf<ProxyHomeSortMode>()
        val effects = DesktopProxyHomeEffectContext(onSetSortMode = { modes += it })

        assertTrue(effects.handle(ProxyHomeEffect.SetSort(ProxyHomeSortMode.Latency)).isSuccess)

        assertEquals(listOf(ProxyHomeSortMode.Latency), modes)
    }

    @Test
    fun subscriptionOperationsAreRejectedWhileAnUpdateIsInProgress() {
        var urlChanges = 0
        var refreshes = 0
        var providerUpdates = 0
        val effects = DesktopProxyHomeEffectContext(
            subscriptions = listOf(DesktopStoredSubscription(id = 7, url = "https://example.com/sub")),
            updatingSubscription = true,
            onSubscriptionUrlChange = { urlChanges += 1 },
            onUpdateSubscription = { refreshes += 1 },
            onUpdateSubscriptionProvider = { _, _ ->
                providerUpdates += 1
                Result.success(Unit)
            },
        )

        assertTrue(effects.handle(ProxyHomeEffect.RefreshSubscription("7")).isFailure)
        assertTrue(effects.handle(ProxyHomeEffect.ToggleSubscriptionEnabled("7")).isFailure)

        assertEquals(0, urlChanges)
        assertEquals(0, refreshes)
        assertEquals(0, providerUpdates)
    }

    @Test
    fun subscriptionPingPassesItsIdentityWithTheActualTestTargets() {
        val server = VLESS(remarks = "London", id = "l", server = "london.example", port = "443")
        val storedServer = DesktopStoredProxyServer(
            id = 31,
            serverJson = server.encodePersistedProxyServer(),
            subscriptionId = 7,
        )
        val subscription = DesktopStoredSubscription(id = 7, url = "https://example.com/sub", name = "Work")
        val library = DesktopServerLibrary(servers = listOf(storedServer))
        val catalog = DesktopProxyGroups.create(library, DesktopSubscriptionLibrary(listOf(subscription)))
        val dispatched = mutableListOf<Pair<Int, List<Int>>>()
        val effects = DesktopProxyHomeEffectContext(
            decodedServers = listOf(storedServer to server),
            groupCatalog = catalog,
            subscriptions = listOf(subscription),
            onPingSubscription = { subscriptionId, targets ->
                dispatched += subscriptionId to targets.map { (serverId, _) -> serverId }
            },
        )

        assertTrue(effects.handle(ProxyHomeEffect.PingSubscription("7")).isSuccess)

        assertEquals(listOf(7 to listOf(31)), dispatched)
    }

    @Test
    fun duplicateDesktopSubscriptionPingIsRejectedWhileTheSubscriptionIsBusy() {
        var starts = 0
        val effects = DesktopProxyHomeEffectContext(
            subscriptions = listOf(DesktopStoredSubscription(id = 7, url = "https://example.com/sub")),
            pingingSubscriptionIds = setOf(9),
            onPingSubscription = { _, _ -> starts += 1 },
        )

        assertTrue(effects.handle(ProxyHomeEffect.PingSubscription("7")).isFailure)

        assertEquals(0, starts)
    }

    @Test
    fun desktopImportDialogIsManualInputAndQrSourceIsRejected() {
        var dialogOpens = 0
        val effects = DesktopProxyHomeEffectContext(onOpenImportDialog = { dialogOpens += 1 })

        assertTrue(effects.handle(ProxyHomeEffect.ImportServers(ProxyHomeImportSource.ManualInput)).isSuccess)
        assertTrue(effects.handle(ProxyHomeEffect.ImportServers(ProxyHomeImportSource.QrCode)).isFailure)

        assertEquals(1, dialogOpens)
    }

    @Test
    fun typedServerAddDoesNotOpenTheServerLinkDialog() {
        val openedModes = mutableListOf<SkipiAddSourceMode>()
        val effects = DesktopProxyHomeEffectContext(onOpenAdd = { openedModes += it })

        assertTrue(effects.handle(ProxyHomeEffect.AddServer(ProxyHomeServerKind.Custom)).isSuccess)

        assertEquals(emptyList<SkipiAddSourceMode>(), openedModes)
    }

    @Test
    fun typedServerAddPassesEveryKindOnceToDraftCreationWithoutOpeningDialogs() {
        val requestedKinds = mutableListOf<ProxyHomeServerKind>()
        val openedModes = mutableListOf<SkipiAddSourceMode>()
        var importDialogOpens = 0
        val effects = DesktopProxyHomeEffectContext(
            onCreateServerDraft = { requestedKinds += it },
            onOpenAdd = { openedModes += it },
            onOpenImportDialog = { importDialogOpens += 1 },
        )
        val everyServerKind: List<ProxyHomeServerKind> = ProxyHomeServerKind.entries.toList()

        everyServerKind.forEach { kind ->
            assertTrue(effects.handle(ProxyHomeEffect.AddServer(kind)).isSuccess)
        }

        assertEquals(everyServerKind, requestedKinds)
        assertEquals(emptyList<SkipiAddSourceMode>(), openedModes)
        assertEquals(0, importDialogOpens)
    }

    @Test
    fun reorderItemSwapsAdjacentElementsAndRejectsBoundary() {
        val list = listOf("A", "B", "C")
        assertEquals(listOf("B", "A", "C"), list.reorderItem(0, 1))
        assertEquals(listOf("A", "C", "B"), list.reorderItem(1, 1))
        assertEquals(listOf("B", "A", "C"), list.reorderItem(1, -1))

        // Boundary moves
        assertNull(list.reorderItem(0, -1))
        assertNull(list.reorderItem(2, 1))
        assertNull(list.reorderItem(0, 0))
        assertNull(list.reorderItem(5, 1))
    }

    @Test
    fun reorderServerInLibraryMovesAmongPeersAndPreservesMetadata() {
        val s1 = DesktopStoredProxyServer(id = 1, serverJson = "json1", subscriptionId = 10)
        val s2 = DesktopStoredProxyServer(id = 2, serverJson = "json2", subscriptionId = 20)
        val s3 = DesktopStoredProxyServer(id = 3, serverJson = "json3", subscriptionId = 10)
        val initial = listOf(s1, s2, s3)

        // S1 moved down among its peers (subscriptionId = 10): target peer is S3
        val reordered = reorderServerInLibrary(initial, serverId = 1, offset = 1)
        assertNotNull(reordered)
        assertEquals(listOf(3, 2, 1), reordered.map { it.id })
        assertEquals(listOf("json3", "json2", "json1"), reordered.map { it.serverJson })
        assertEquals(listOf(10, 20, 10), reordered.map { it.subscriptionId })

        // Boundary rejection among peers
        assertNull(reorderServerInLibrary(initial, serverId = 1, offset = -1))
        assertNull(reorderServerInLibrary(initial, serverId = 3, offset = 1))
        assertNull(reorderServerInLibrary(initial, serverId = 2, offset = 1)) // S2 is alone in subscription 20
        assertNull(reorderServerInLibrary(initial, serverId = 99, offset = 1)) // non-existent
    }

    @Test
    fun moveGroupDispatchesCallbackOnceAndRejectsBuiltinsAndBoundaries() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val subs = listOf(
            DesktopStoredSubscription(id = 10, url = "https://one.com"),
            DesktopStoredSubscription(id = 20, url = "https://two.com"),
        )
        val context = DesktopProxyHomeEffectContext(
            subscriptions = subs,
            onMoveGroup = { id, offset ->
                moves += (id to offset)
                Result.success(Unit)
            },
        )

        // Valid move
        assertTrue(context.handle(ProxyHomeEffect.MoveGroup("subscription:10", 1)).isSuccess)
        assertEquals(listOf(10 to 1), moves)

        // Built-in groups rejected
        assertTrue(context.handle(ProxyHomeEffect.MoveGroup("all", 1)).isFailure)
        assertTrue(context.handle(ProxyHomeEffect.MoveGroup("manual", 1)).isFailure)
        assertTrue(context.handle(ProxyHomeEffect.MoveGroup("auto-balancers", 1)).isFailure)

        // Boundary moves rejected cleanly
        assertTrue(context.handle(ProxyHomeEffect.MoveGroup("subscription:10", -1)).isFailure)
        assertTrue(context.handle(ProxyHomeEffect.MoveGroup("subscription:20", 1)).isFailure)
        assertEquals(1, moves.size) // no extra dispatches
    }

    @Test
    fun moveServerDispatchesOnlyDuringDefaultSortAndRejectsOtherSortModes() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val s1 = DesktopStoredProxyServer(id = 1, serverJson = "{}", subscriptionId = null)
        val s2 = DesktopStoredProxyServer(id = 2, serverJson = "{}", subscriptionId = null)
        val servers = listOf(s1 to null, s2 to null)

        val defaultContext = DesktopProxyHomeEffectContext(
            decodedServers = servers,
            sortMode = ProxyHomeSortMode.Default,
            onMoveServer = { id, offset ->
                moves += (id to offset)
                Result.success(Unit)
            },
        )
        assertTrue(defaultContext.handle(ProxyHomeEffect.MoveServer("1", 1)).isSuccess)
        assertEquals(listOf(1 to 1), moves)

        // Boundary rejection
        assertTrue(defaultContext.handle(ProxyHomeEffect.MoveServer("1", -1)).isFailure)
        assertTrue(defaultContext.handle(ProxyHomeEffect.MoveServer("2", 1)).isFailure)

        // Rejection during Name and Latency sort
        val nameContext = DesktopProxyHomeEffectContext(
            decodedServers = servers,
            sortMode = ProxyHomeSortMode.Name,
            onMoveServer = { id, offset ->
                moves += (id to offset)
                Result.success(Unit)
            },
        )
        assertTrue(nameContext.handle(ProxyHomeEffect.MoveServer("1", 1)).isFailure)

        val latencyContext = DesktopProxyHomeEffectContext(
            decodedServers = servers,
            sortMode = ProxyHomeSortMode.Latency,
            onMoveServer = { id, offset ->
                moves += (id to offset)
                Result.success(Unit)
            },
        )
        assertTrue(latencyContext.handle(ProxyHomeEffect.MoveServer("1", 1)).isFailure)
        assertEquals(1, moves.size) // no additional dispatches
    }

    @Test
    fun editGroupDispatchesToUIAndRejectsBuiltins() {
        val openedGroups = mutableListOf<String?>()
        val subs = listOf(DesktopStoredSubscription(id = 5, url = "", name = "Manual Folder"))
        val context = DesktopProxyHomeEffectContext(
            subscriptions = subs,
            onOpenEditGroup = { openedGroups += it },
        )

        // EditGroup(null) opens dialog for new manual group
        assertTrue(context.handle(ProxyHomeEffect.EditGroup(null)).isSuccess)
        assertEquals(listOf<String?>(null), openedGroups)

        // EditGroup(id) for persisted group
        assertTrue(context.handle(ProxyHomeEffect.EditGroup("subscription:5")).isSuccess)
        assertEquals(listOf<String?>(null, "subscription:5"), openedGroups)

        // Built-ins rejected
        assertTrue(context.handle(ProxyHomeEffect.EditGroup("all")).isFailure)
        assertTrue(context.handle(ProxyHomeEffect.EditGroup("manual")).isFailure)
        assertTrue(context.handle(ProxyHomeEffect.EditGroup("auto-balancers")).isFailure)
    }

    @Test
    fun deleteGroupDispatchesConfirmOrDeleteAndRejectsBuiltins() {
        var confirmedId: Int? = null
        var directDeletedId: Int? = null
        val subs = listOf(DesktopStoredSubscription(id = 5, url = "", name = "Manual Folder"))

        val confirmContext = DesktopProxyHomeEffectContext(
            subscriptions = subs,
            confirmDeletion = true,
            onDeleteSubscriptionConfirm = { confirmedId = it },
            onDeleteSubscription = { directDeletedId = it },
        )
        assertTrue(confirmContext.handle(ProxyHomeEffect.DeleteGroup("subscription:5")).isSuccess)
        assertEquals(5, confirmedId)
        assertNull(directDeletedId)

        val directContext = DesktopProxyHomeEffectContext(
            subscriptions = subs,
            confirmDeletion = false,
            onDeleteSubscriptionConfirm = { confirmedId = it },
            onDeleteSubscription = { directDeletedId = it },
        )
        assertTrue(directContext.handle(ProxyHomeEffect.DeleteGroup("subscription:5")).isSuccess)
        assertEquals(5, directDeletedId)

        // Built-ins rejected
        assertTrue(confirmContext.handle(ProxyHomeEffect.DeleteGroup("all")).isFailure)
        assertTrue(confirmContext.handle(ProxyHomeEffect.DeleteGroup("manual")).isFailure)
    }

    @Test
    fun refreshSubscriptionRejectsBlankUrl() {
        var refreshCount = 0
        val subs = listOf(
            DesktopStoredSubscription(id = 1, url = "", name = "Blank manual"),
            DesktopStoredSubscription(id = 2, url = "https://example.com/sub", name = "Real sub"),
        )
        val context = DesktopProxyHomeEffectContext(
            subscriptions = subs,
            onUpdateSubscription = { refreshCount += 1 },
        )

        assertTrue(context.handle(ProxyHomeEffect.RefreshSubscription("1")).isFailure)
        assertEquals(0, refreshCount)

        assertTrue(context.handle(ProxyHomeEffect.RefreshSubscription("2")).isSuccess)
        assertEquals(1, refreshCount)
    }
}
