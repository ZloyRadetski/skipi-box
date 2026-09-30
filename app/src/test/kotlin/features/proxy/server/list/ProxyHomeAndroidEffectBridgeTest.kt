// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.ProxyServerState
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomeStore
import engine.proxy.ProxyEngineStatus
import engine.proxy.latency.ProxyServerLatencyTestMode
import features.proxy.server.model.VLESS
import features.proxy.server.usecase.AndroidTunnelController
import features.proxy.server.usecase.ProxyServiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import app.modes.SubscriptionPingModeHttp
import app.modes.SubscriptionPingModeTcp
import features.proxy.server.model.StrategyGroup
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import platform.TunnelConnectRequest
import platform.TunnelTraffic

class ProxyHomeAndroidEffectBridgeTest {
    @Test
    fun selectingAnotherServerWhileRunningRequestsExactlyOneRestart() {
        val first = server(10)
        val second = server(20)
        val state = MutableStateFlow(
            AppState(
                proxyServers = listOf(first, second),
                selectedProxyServerId = first.id,
                proxyRunning = true,
            ),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val restartRequests = mutableListOf<Int>()
        val observer = scope.observeHomeServerSelection(state) { serverId ->
            if (state.value.proxyRunning) restartRequests += serverId
        }
        val bridge = ProxyHomeAndroidEffectBridge(
            onToggleTunnel = { Result.success(Unit) },
            onSelectServer = { id ->
                val parsedId = id.toIntOrNull()
                if (parsedId == null) {
                    Result.failure(IllegalArgumentException("Invalid server id"))
                } else {
                    state.update { current -> current.copy(selectedProxyServerId = parsedId) }
                    Result.success(Unit)
                }
            },
            onOtherEffect = { Result.success(Unit) },
        )
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                selectedServerId = first.id.toString(),
                servers = listOf(
                    app.skipi.app.home.ProxyServerSummary(first.id.toString(), "First", "first", "VLESS"),
                    app.skipi.app.home.ProxyServerSummary(second.id.toString(), "Second", "second", "VLESS"),
                ),
                availableActions = setOf(ProxyHomeActionId.SelectServer),
            ),
            scope = scope,
            effectHandler = bridge,
        )

        store.dispatch(ProxyHomeAction.SelectServer(second.id.toString()))

        assertEquals(second.id, state.value.selectedProxyServerId)
        assertEquals(listOf(second.id), restartRequests)
        observer.cancel()
        scope.cancel()
    }

    @Test
    fun toggleTunnelEffectInvokesAndroidTunnelControllerStartPathOnce() {
        val selected = server(41)
        val state = MutableStateFlow(
            AppState(
                proxyServers = listOf(selected),
                selectedProxyServerId = selected.id,
                proxyRunning = false,
            ),
        )
        var serviceStartCalls = 0
        var hapticCalls = 0
        var latencyCancelCalls = 0
        val controller = AndroidTunnelController(
            readState = { state.value },
            updateState = { transform -> state.update(transform) },
            prepareForConnection = { current -> current },
            readStatus = { Result.success(ProxyEngineStatus(running = state.value.proxyRunning)) },
            startService = { current, _ ->
                serviceStartCalls++
                ProxyServiceResult.Success(proxyRunning = true, appState = current)
            },
            stopService = { ProxyServiceResult.Success(proxyRunning = false) },
            readTraffic = { TunnelTraffic() },
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val bridge = ProxyHomeAndroidEffectBridge(
            onToggleTunnel = {
                hapticCalls++
                latencyCancelCalls++
                controller.connect(TunnelConnectRequest(state.value.selectedProxyServerId.toString()))
            },
            onSelectServer = { Result.success(Unit) },
            onOtherEffect = { Result.success(Unit) },
        )
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                selectedServerId = selected.id.toString(),
                canToggleTunnel = true,
                availableActions = setOf(ProxyHomeActionId.ToggleTunnel),
            ),
            scope = scope,
            effectHandler = bridge,
        )

        store.dispatch(ProxyHomeAction.ToggleTunnel)

        assertEquals(1, hapticCalls)
        assertEquals(1, latencyCancelCalls)
        assertEquals(1, serviceStartCalls)
        assertTrue(state.value.proxyRunning)
        scope.cancel()
    }

    @Test
    fun selectingServerWhileDisconnectedUpdatesStateWithoutRestartAndDuplicateSelectionIsNoop() {
        val first = server(10)
        val second = server(20)
        val state = MutableStateFlow(
            AppState(
                proxyServers = listOf(first, second),
                selectedProxyServerId = first.id,
                proxyRunning = false,
            ),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val restartRequests = mutableListOf<Int>()
        var dispatchCount = 0
        val observer = scope.observeHomeServerSelection(state) { serverId ->
            if (state.value.proxyRunning) restartRequests += serverId
        }
        val bridge = ProxyHomeAndroidEffectBridge(
            onToggleTunnel = { Result.success(Unit) },
            onSelectServer = { id ->
                dispatchCount++
                val parsedId = id.toIntOrNull()
                if (parsedId == null) {
                    Result.failure(IllegalArgumentException("Invalid server id"))
                } else {
                    state.update { current -> current.copy(selectedProxyServerId = parsedId) }
                    Result.success(Unit)
                }
            },
            onOtherEffect = { Result.success(Unit) },
        )
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                selectedServerId = first.id.toString(),
                servers = listOf(
                    app.skipi.app.home.ProxyServerSummary(first.id.toString(), "First", "first", "VLESS"),
                    app.skipi.app.home.ProxyServerSummary(second.id.toString(), "Second", "second", "VLESS"),
                ),
                availableActions = setOf(ProxyHomeActionId.SelectServer),
            ),
            scope = scope,
            effectHandler = bridge,
        )

        store.dispatch(ProxyHomeAction.SelectServer(second.id.toString()))

        assertEquals(second.id, state.value.selectedProxyServerId)
        assertEquals(1, dispatchCount)
        assertTrue(restartRequests.isEmpty())

        store.dispatch(ProxyHomeAction.SelectServer(second.id.toString()))
        assertEquals(2, dispatchCount)
        assertTrue(restartRequests.isEmpty())

        observer.cancel()
        scope.cancel()
    }

    @Test
    fun testGroupSelectsRealConnectionWhenConfiguredAsHttp() {
        val s1 = server(1, groupId = 1)
        val s2 = server(2, groupId = 2)
        val allServers = listOf(s1, s2)
        var testedMode: ProxyServerLatencyTestMode? = null
        var testedTemplate: String? = null
        var testedTargets: List<ProxyServerState>? = null

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeHttp },
            getLatencyDoneTemplate = { "TCP_DONE" },
            getRealConnectionDoneTemplate = { "HTTP_DONE" },
            getAllServers = { allServers },
            getGroupMemberIds = { groupId -> if (groupId == "1") setOf("1") else null },
            isHostBusy = { false },
            launchTest = { targets, mode, template, _ ->
                testedTargets = targets
                testedMode = mode
                testedTemplate = template
                Job()
            },
            onCancel = {},
        )
        val bridge = ProxyHomeAndroidEffectBridge(
            onToggleTunnel = { Result.success(Unit) },
            onSelectServer = { Result.success(Unit) },
            latencyCoordinator = coordinator,
        )
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                availableActions = setOf(ProxyHomeActionId.TestGroup),
                groups = listOf(
                    app.skipi.app.home.ProxyGroupSummary(
                        id = "1",
                        title = "Group 1",
                        serverCount = 1,
                        enabled = true,
                        serverIds = setOf("1"),
                    ),
                ),
            ),
            scope = scope,
            effectHandler = bridge,
        )

        store.dispatch(ProxyHomeAction.TestGroup("1"))

        assertEquals(ProxyServerLatencyTestMode.RealConnection, testedMode)
        assertEquals("HTTP_DONE", testedTemplate)
        assertEquals(listOf(s1), testedTargets)
        scope.cancel()
    }

    @Test
    fun testVisibleServersSelectsTcpConnectWhenConfiguredAsTcp() {
        val s1 = server(10)
        val s2 = server(20)
        val s3 = server(30)
        val allServers = listOf(s1, s2, s3)
        var testedMode: ProxyServerLatencyTestMode? = null
        var testedTemplate: String? = null
        var testedTargets: List<ProxyServerState>? = null

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeTcp },
            getLatencyDoneTemplate = { "TCP_DONE" },
            getRealConnectionDoneTemplate = { "HTTP_DONE" },
            getAllServers = { allServers },
            getGroupMemberIds = { null },
            isHostBusy = { false },
            launchTest = { targets, mode, template, _ ->
                testedTargets = targets
                testedMode = mode
                testedTemplate = template
                Job()
            },
            onCancel = {},
        )
        val bridge = ProxyHomeAndroidEffectBridge(
            onToggleTunnel = { Result.success(Unit) },
            onSelectServer = { Result.success(Unit) },
            latencyCoordinator = coordinator,
        )
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                availableActions = setOf(ProxyHomeActionId.TestVisibleServers),
                servers = listOf(
                    app.skipi.app.home.ProxyServerSummary("10", "S10", "s10", "VLESS"),
                    app.skipi.app.home.ProxyServerSummary("30", "S30", "s30", "VLESS"),
                ),
            ),
            scope = scope,
            effectHandler = bridge,
        )

        store.dispatch(ProxyHomeAction.TestVisibleServers(listOf("10", "30")))

        assertEquals(ProxyServerLatencyTestMode.TcpConnect, testedMode)
        assertEquals("TCP_DONE", testedTemplate)
        assertEquals(listOf(s1, s3), testedTargets)
        scope.cancel()
    }

    @Test
    fun testGroupUsesOnlyGroupMembersAndExcludesOtherGroups() {
        val s1 = server(1, groupId = 42)
        val s2 = server(2, groupId = 42)
        val s3 = server(3, groupId = 99)
        val allServers = listOf(s1, s2, s3)
        var testedTargets: List<ProxyServerState>? = null

        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeTcp },
            getLatencyDoneTemplate = { "DONE" },
            getRealConnectionDoneTemplate = { "DONE" },
            getAllServers = { allServers },
            getGroupMemberIds = { groupId -> if (groupId == "42") setOf("1", "2") else null },
            isHostBusy = { false },
            launchTest = { targets, _, _, _ ->
                testedTargets = targets
                Job()
            },
            onCancel = {},
        )

        val result = coordinator.testGroup("42")

        assertTrue(result.isSuccess)
        assertEquals(listOf(s1, s2), testedTargets)
        assertFalse(testedTargets?.contains(s3) == true)
    }

    @Test
    fun testGroupUsesHomeMembershipForAllAndAutoBalancerGroups() {
        val manual = server(10, groupId = 1)
        val subscription = server(20, groupId = 2)
        val autoBalancer = ProxyServerState(
            id = 30,
            server = StrategyGroup(remarks = "Auto balancer"),
            groupId = AutoBalancerGroupId,
        )
        val unrelatedStrategy = ProxyServerState(
            id = 40,
            server = StrategyGroup(remarks = "Unrelated strategy"),
            groupId = 99,
        )
        val allServers = listOf(manual, subscription, autoBalancer, unrelatedStrategy)
        val memberships = mapOf(
            AllProxyGroupId.toString() to setOf("10", "20"),
            AutoBalancerGroupId.toString() to setOf("30"),
        )
        var testedTargets: List<ProxyServerState> = emptyList()
        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeTcp },
            getLatencyDoneTemplate = { "DONE" },
            getRealConnectionDoneTemplate = { "DONE" },
            getAllServers = { allServers },
            getGroupMemberIds = memberships::get,
            isHostBusy = { false },
            launchTest = { targets, _, _, _ ->
                testedTargets = targets
                Job()
            },
            onCancel = {},
        )

        assertTrue(coordinator.testGroup(AllProxyGroupId.toString()).isSuccess)
        assertEquals(listOf(manual, subscription), testedTargets)

        coordinator.cancel()
        assertTrue(coordinator.testGroup(AutoBalancerGroupId.toString()).isSuccess)
        assertEquals(listOf(autoBalancer), testedTargets)
        assertFalse(testedTargets.contains(unrelatedStrategy))
    }

    @Test
    fun testVisibleServersObeysIntendedVisibleSubset() {
        val s1 = server(1)
        val s2 = server(2)
        val s3 = server(3)
        val allServers = listOf(s1, s2, s3)
        var testedTargets: List<ProxyServerState>? = null

        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeTcp },
            getLatencyDoneTemplate = { "DONE" },
            getRealConnectionDoneTemplate = { "DONE" },
            getAllServers = { allServers },
            getGroupMemberIds = { null },
            isHostBusy = { false },
            launchTest = { targets, _, _, _ ->
                testedTargets = targets
                Job()
            },
            onCancel = {},
        )

        val result = coordinator.testVisibleServers(listOf("2"))

        assertTrue(result.isSuccess)
        assertEquals(listOf(s2), testedTargets)
    }

    @Test
    fun testCancelDispatchReachesRunningOperation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var onCancelCalled = false
        val runningJob = Job()

        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeTcp },
            getLatencyDoneTemplate = { "DONE" },
            getRealConnectionDoneTemplate = { "DONE" },
            getAllServers = { listOf(server(1)) },
            getGroupMemberIds = { groupId -> if (groupId == "1") setOf("1") else null },
            isHostBusy = { false },
            launchTest = { _, _, _, _ -> runningJob },
            onCancel = { onCancelCalled = true },
        )
        val bridge = ProxyHomeAndroidEffectBridge(
            onToggleTunnel = { Result.success(Unit) },
            onSelectServer = { Result.success(Unit) },
            latencyCoordinator = coordinator,
        )
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                availableActions = setOf(ProxyHomeActionId.CancelLatencyTests),
            ),
            scope = scope,
            effectHandler = bridge,
        )

        coordinator.testVisibleServers(listOf("1"))
        assertTrue(coordinator.isBusy)
        assertFalse(runningJob.isCancelled)

        store.dispatch(ProxyHomeAction.CancelLatencyTests)

        assertTrue(runningJob.isCancelled)
        assertFalse(coordinator.isBusy)
        assertTrue(onCancelCalled)
        scope.cancel()
    }

    @Test
    fun testBusyLatencyOperationIsNotStartedTwice() {
        var launchCount = 0
        val pendingJob = Job()

        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeTcp },
            getLatencyDoneTemplate = { "DONE" },
            getRealConnectionDoneTemplate = { "DONE" },
            getAllServers = { listOf(server(1)) },
            getGroupMemberIds = { groupId -> if (groupId == "1") setOf("1") else null },
            isHostBusy = { false },
            launchTest = { _, _, _, _ ->
                launchCount++
                pendingJob
            },
            onCancel = {},
        )

        val firstResult = coordinator.testVisibleServers(listOf("1"))
        assertTrue(firstResult.isSuccess)
        assertEquals(1, launchCount)
        assertTrue(coordinator.isBusy)

        val secondResult = coordinator.testGroup("1")
        assertTrue(secondResult.isFailure)
        assertEquals(1, launchCount)

        val thirdResult = coordinator.testVisibleServers(listOf("1"))
        assertTrue(thirdResult.isFailure)
        assertEquals(1, launchCount)
    }

    @Test
    fun hostLatencyStateBlocksNewTestsWithoutATrackedCoordinatorJob() {
        var hostBusy = true
        var launchCount = 0
        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeTcp },
            getLatencyDoneTemplate = { "DONE" },
            getRealConnectionDoneTemplate = { "DONE" },
            getAllServers = { listOf(server(1)) },
            getGroupMemberIds = { null },
            isHostBusy = { hostBusy },
            launchTest = { _, _, _, _ ->
                launchCount++
                Job()
            },
            onCancel = {},
        )

        assertTrue(coordinator.isBusy)
        assertTrue(coordinator.testVisibleServers(listOf("1")).isFailure)
        assertEquals(0, launchCount)

        hostBusy = false
        assertTrue(coordinator.testVisibleServers(listOf("1")).isSuccess)
        assertEquals(1, launchCount)
    }

    @Test
    fun finishedOldRunCannotClearTheNewRunBusyState() {
        val completionCallbacks = mutableListOf<() -> Unit>()
        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeTcp },
            getLatencyDoneTemplate = { "DONE" },
            getRealConnectionDoneTemplate = { "DONE" },
            getAllServers = { listOf(server(1)) },
            getGroupMemberIds = { null },
            isHostBusy = { false },
            launchTest = { _, _, _, onFinished ->
                completionCallbacks += onFinished
                Job()
            },
            onCancel = {},
        )

        assertTrue(coordinator.testVisibleServers(listOf("1")).isSuccess)
        coordinator.cancel()
        assertTrue(coordinator.testVisibleServers(listOf("1")).isSuccess)
        assertTrue(coordinator.isBusy)

        completionCallbacks.first().invoke()
        assertTrue(coordinator.isBusy)
        completionCallbacks.last().invoke()
        assertFalse(coordinator.isBusy)
    }

    @Test
    fun testUnsupportedInvalidGroupIdFailsFast() {
        var launchCount = 0
        val coordinator = ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { SubscriptionPingModeTcp },
            getLatencyDoneTemplate = { "DONE" },
            getRealConnectionDoneTemplate = { "DONE" },
            getAllServers = { listOf(server(1)) },
            getGroupMemberIds = { null },
            isHostBusy = { false },
            launchTest = { _, _, _, _ ->
                launchCount++
                Job()
            },
            onCancel = {},
        )

        val result = coordinator.testGroup("not_a_number")
        assertTrue(result.isFailure)
        assertEquals(0, launchCount)
    }

    private fun server(id: Int, groupId: Int = 1): ProxyServerState = ProxyServerState(
        id = id,
        server = VLESS(remarks = "Server $id", id = "id-$id", server = "server$id.example", port = "443"),
        groupId = groupId,
    )
}
