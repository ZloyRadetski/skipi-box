// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalProxyPageScrollToTopRequest
import app.LocalUpdateAppState
import app.ProxyServerState
import app.R
import app.modes.ConnectionDisplayModeCompact
import app.modes.SubscriptionPingModeHttp
import app.collectProxyServerListState
import engine.proxy.latency.ProxyServerLatencyTestMode
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.restartProxyServiceAfterSelection
import features.proxy.server.usecase.runProxyServerLatencyTest
import features.proxy.server.usecase.updatableSubscriptionGroups
import features.proxy.server.usecase.withUpdatedSubscriptionServers
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.SubscriptionGroupEditorDialog
import features.subscription.usecase.subscriptionUpdateMessage
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import ui.AppTheme
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageWindowPadding
import ui.components.DeleteConfirmationDialog
import ui.text.formatTemplate

private const val ProxyServerEditResultKey = "proxy-server-edit-result"

@Composable
fun ProxyServerListPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val scrollToTopRequest = LocalProxyPageScrollToTopRequest.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val stateStore = LocalAppStateStore.current
    val proxyListState by stateStore.collectProxyServerListState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val services = LocalAppServices.current
    val proxyLatencyTester = services.proxyLatencyTester
    val qrScanner = services.qrScanner
    val subscriptionFetcher = services.subscriptionFetcher
    val proxyEngine = services.proxyEngine
    val proxyServiceUseCase = services.proxyServiceUseCase
    val proxyServerImportFileUseCase = services.proxyServerImportFileUseCase
    val tipNotifier = services.tipNotifier
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val serviceRestartMutex = remember { Mutex() }
    val allSubscriptionsUpdateMutex = remember { Mutex() }

    var searchValue by rememberSaveable { mutableStateOf("") }
    // Derive the initial selectedGroupId from the persisted selectedProxyServerId
    // so the tab starts on the correct group after process death. rememberSaveable
    // cannot survive process kill, so we compute the initial value from proxyListState
    // (synchronously loaded from persistent storage) instead of defaulting to
    // DefaultSubscriptionGroupId.
    val initialSelectedGroupId = remember {
        proxyListState.proxyServers
            .firstOrNull { it.id == proxyListState.selectedProxyServerId }
            ?.groupId
            ?: DefaultSubscriptionGroupId
    }
    var selectedGroupId by rememberSaveable { mutableIntStateOf(initialSelectedGroupId) }
    var serviceOperationInProgress by rememberSaveable { mutableStateOf(false) }
    var pendingProxyServerDeletion by remember { mutableStateOf<ProxyServerState?>(null) }
    var editingSubscriptionGroupId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectingGroupMemberForServer by remember { mutableStateOf<ProxyServerState?>(null) }
    var pingingGroupIds by remember { mutableStateOf(emptySet<Int>()) }
    val servers = proxyListState.proxyServers
    val editingSubscriptionGroup = editingSubscriptionGroupId?.let { groupId ->
        proxyListState.subscriptionGroups.firstOrNull { group -> group.id == groupId }
    }
    var selectedServerId by rememberSaveable { mutableIntStateOf(proxyListState.selectedProxyServerId) }
    val selectedServer = servers.firstOrNull { server -> server.id == selectedServerId }
    val proxyRunning = proxyListState.proxyRunning
    val context = LocalContext.current.applicationContext
    val activeTunnelSample by produceActiveTunnelRuntimeSample(context, proxyRunning)
    val activeOutboundTag = activeTunnelSample?.outboundTag
    val allGroupName = stringResource(R.string.proxy_server_list_all)
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val autoBalancerGroupName = stringResource(R.string.proxy_server_list_auto_balancers)
    val unknownGroupName = stringResource(R.string.common_unknown_group)
    val invalidSubscriptionUrlMessage = stringResource(R.string.subscription_invalid_url)
    val messages = proxyServerListMessages()
    val columns = proxyListState.proxyServerListLayout.resolvedProxyServerListColumns()
    LaunchedEffect(proxyListState.selectedProxyServerId) {
        selectedServerId = proxyListState.selectedProxyServerId
    }

    fun runProxyServiceOperation(operation: suspend () -> Unit) {
        if (serviceOperationInProgress) return
        serviceOperationInProgress = true
        services.appScope.launch {
            try {
                operation()
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    serviceOperationInProgress = false
                }
            }
        }
    }

    fun restartProxyServiceSilently(serverId: Int) {
        restartProxyServiceAfterSelection(
            serverId = serverId,
            scope = services.appScope,
            serviceRestartMutex = serviceRestartMutex,
            stateStore = stateStore,
            proxyEngine = proxyEngine,
            updateAppState = updateAppState,
        )
    }

    LaunchedEffect(stateStore, proxyEngine) {
        var previousServerId = stateStore.state.value.selectedProxyServerId
        stateStore.state
            .map { state -> state.selectedProxyServerId }
            .distinctUntilChanged()
            .collect { serverId ->
                if (serverId == previousServerId) {
                    return@collect
                }
                previousServerId = serverId
                restartProxyServiceSilently(serverId)
            }
    }

    fun testProxyServerLatency(
        targetServers: List<ProxyServerState>,
        mode: ProxyServerLatencyTestMode,
        doneTemplate: String,
        showSingleResult: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        runProxyServerLatencyTest(
            targetServers = targetServers,
            mode = mode,
            doneTemplate = doneTemplate,
            showSingleResult = showSingleResult,
            scope = services.appScope,
            stateStore = stateStore,
            updateAppState = updateAppState,
            proxyLatencyTester = proxyLatencyTester,
            tipNotifier = tipNotifier,
            noTestableServersMessage = messages.noTestableServers,
            latencyResultTemplate = messages.latencyResultTemplate,
            latencyFailedMessage = messages.latencyFailed,
            onFinished = onFinished,
        )
    }

    suspend fun updateSubscription(groupId: Int) {
        val group = stateStore.state.value.subscriptionGroups.firstOrNull { it.id == groupId }
            ?: return
        val result = updateSubscriptions(
            groups = listOf(group),
            subscriptionFetcher = subscriptionFetcher,
            fetchOptions = { subscription ->
                stateStore.state.value.toSubscriptionFetchOptions(subscription)
            },
        )
        if (result.updates.isNotEmpty()) {
            updateAppState { state ->
                state.withUpdatedSubscriptionServers(
                    updates = result.updates,
                    updatedAtMillis = result.updatedAtMillis,
                )
            }
        }
        tipNotifier.show(
            subscriptionUpdateMessage(
                result = result,
                successTemplate = messages.subscriptionUpdateResultTemplate,
                failedTemplate = messages.subscriptionUpdateResultWithFailedTemplate,
            ),
        )
    }

    suspend fun updateAllSubscriptions() {
        allSubscriptionsUpdateMutex.withLock {
            val subscriptionGroups = stateStore.state.value.subscriptionGroups.updatableSubscriptionGroups()
            if (subscriptionGroups.isEmpty()) {
                tipNotifier.show(messages.noSubscriptionUpdates)
                return@withLock
            }
            val result = updateSubscriptions(
                groups = subscriptionGroups,
                subscriptionFetcher = subscriptionFetcher,
                fetchOptions = { group -> stateStore.state.value.toSubscriptionFetchOptions(group) },
            )
            if (result.updates.isNotEmpty()) {
                updateAppState { state ->
                    state.withUpdatedSubscriptionServers(
                        updates = result.updates,
                        updatedAtMillis = result.updatedAtMillis,
                    )
                }
            }
            tipNotifier.show(
                subscriptionUpdateMessage(
                    result = result,
                    successTemplate = messages.subscriptionUpdateResultTemplate,
                    failedTemplate = messages.subscriptionUpdateResultWithFailedTemplate,
                ),
            )
        }
    }

    fun pingSubscription(groupId: Int) {
        if (pingingGroupIds.contains(groupId)) {
            scope.launch {
                tipNotifier.show(messages.pingInProgress)
            }
            return
        }
        val mode = if (proxyListState.subscriptionPingMode == SubscriptionPingModeHttp) {
            ProxyServerLatencyTestMode.RealConnection
        } else {
            ProxyServerLatencyTestMode.TcpConnect
        }
        pingingGroupIds = pingingGroupIds + groupId
        testProxyServerLatency(
            targetServers = servers.filter { server -> server.groupId == groupId },
            mode = mode,
            doneTemplate = if (mode == ProxyServerLatencyTestMode.RealConnection) {
                messages.realConnectionDoneTemplate
            } else {
                messages.latencyDoneTemplate
            },
            onFinished = {
                pingingGroupIds = pingingGroupIds - groupId
            },
        )
    }

    fun deleteProxyServer(server: ProxyServerState) {
        if (serviceOperationInProgress) return
        val remarks = server.server.getInfo().remarks

        fun removeServer(stopResult: ProxyServiceResult.Success? = null): Boolean {
            var deleted = false
            updateAppState { state ->
                val nextServers = state.proxyServers.filterNot { it.id == server.id }
                if (nextServers.size == state.proxyServers.size) {
                    stopResult?.let { result ->
                        state.copy(
                            proxyRunning = result.proxyRunning,
                            localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                        )
                    } ?: state
                } else {
                    deleted = true
                    val selectedProxyServerId = if (state.selectedProxyServerId == server.id) {
                        nextServers.firstOrNull()?.id ?: state.selectedProxyServerId
                    } else {
                        state.selectedProxyServerId
                    }
                    state.copy(
                        proxyServers = nextServers,
                        selectedProxyServerId = selectedProxyServerId,
                        proxyRunning = stopResult?.proxyRunning ?: state.proxyRunning,
                        localProxyPort = stopResult?.appState?.localProxyPort ?: state.localProxyPort,
                    )
                }
            }
            return deleted
        }

        val stateSnapshot = stateStore.state.value
        if (stateSnapshot.selectedProxyServerId != server.id || !stateSnapshot.proxyRunning) {
            if (removeServer()) {
                services.appScope.launch {
                    tipNotifier.show(messages.deletedTemplate.formatTemplate("name" to remarks))
                }
            }
            return
        }

        runProxyServiceOperation {
            when (val stopResult = proxyServiceUseCase.stop(stateStore.state.value.runMode)) {
                is ProxyServiceResult.Success -> {
                    if (removeServer(stopResult)) {
                        tipNotifier.show(messages.deletedTemplate.formatTemplate("name" to remarks))
                    }
                }

                ProxyServiceResult.MissingServer -> {
                    tipNotifier.show(messages.selectServerFirst)
                }

                is ProxyServiceResult.Failed -> {
                    updateAppState { state -> state.copy(proxyRunning = false) }
                    tipNotifier.showError(stopResult.error, messages.serviceStopped)
                }
            }
        }
    }

    fun requestProxyServerDeletion(server: ProxyServerState) {
        if (proxyListState.enableDeletionConfirmation) {
            pendingProxyServerDeletion = server
        } else {
            deleteProxyServer(server)
        }
    }

    ProxyServerEditResultHandler(
        navigator = navigator,
        resultKey = ProxyServerEditResultKey,
        messages = messages,
        updateAppState = updateAppState,
        tipNotifier = tipNotifier,
        onSelectedGroupIdChange = { selectedGroupId = it },
    )

    val groupState = proxyServerListGroups(
        state = proxyListState,
        selectedGroupId = selectedGroupId,
        searchValue = searchValue,
        allGroupName = allGroupName,
        defaultGroupName = defaultGroupName,
        autoBalancerGroupName = autoBalancerGroupName,
    )
    val itemTextFormatter = rememberProxyServerListItemTextFormatter(
        groupNames = groupState.groupNames,
        unknownGroupName = unknownGroupName,
    )
    val groupTabIds = groupState.groupTabs.map { group -> group.id }
    val groupPagerState = key(groupTabIds) {
        rememberPagerState(
            initialPage = groupState.selectedTabIndex,
            pageCount = { groupTabIds.size.coerceAtLeast(1) },
        )
    }
    val groupPagerOffsetFraction by remember(groupPagerState) {
        derivedStateOf { groupPagerState.currentPageOffsetFraction }
    }

    LaunchedEffect(groupTabIds) {
        val lastIndex = groupTabIds.lastIndex
        if (lastIndex >= 0 && groupPagerState.currentPage > lastIndex) {
            groupPagerState.scrollToPage(lastIndex)
        }
    }

    LaunchedEffect(groupState.selectedTabIndex, groupTabIds) {
        if (
            groupTabIds.isNotEmpty() &&
            !groupPagerState.isScrollInProgress &&
            groupPagerState.currentPage != groupState.selectedTabIndex
        ) {
            // Subscription pages can contain large lists.  Switching a tab should be
            // immediate rather than animating and composing two heavy pages at once.
            groupPagerState.scrollToPage(groupState.selectedTabIndex)
        }
    }

    LaunchedEffect(groupPagerState, groupTabIds) {
        snapshotFlow { groupPagerState.targetPage }
            .collect { page ->
                groupState.groupTabs.getOrNull(page)?.let { group ->
                    if (selectedGroupId != group.id) {
                        selectedGroupId = group.id
                    }
                }
            }
    }

    val contentPadding = pageContentPaddingWithCutout(
        innerPadding = PaddingValues(0.dp),
        outerPadding = padding,
        isWideScreen = isWideScreen,
    )
    val floatingToolbarBottomPadding = contentPadding.calculateBottomPadding()
    val showFloatingToolbar = proxyListState.connectionDisplayMode == ConnectionDisplayModeCompact ||
        proxyListState.classicShowFloatingPowerButton
    val listPadding = pageListPadding(
        contentPadding = contentPadding,
        bottomExtra = if (showFloatingToolbar) {
            ProxyServerListFloatingToolbarReservedBottomPadding
        } else {
            0.dp
        },
    )
    val dragScrollThresholdBottomPadding = pageListPadding(contentPadding).calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .pageWindowPadding(padding),
    ) {
        ProxyServerListTopBar(
            isWideScreen = isWideScreen,
            scrollBehavior = topAppBarScrollBehavior,
            searchValue = searchValue,
            onSearchValueChange = { searchValue = it },
            groupState = groupState,
            groupPagerPage = groupPagerState.currentPage,
            groupPagerOffsetFraction = groupPagerOffsetFraction,
            selectedServer = selectedServer,
            proxyListState = proxyListState,
            stateStore = stateStore,
            updateAppState = updateAppState,
            navigator = navigator,
            qrScanner = qrScanner,
            proxyServerImportFileUseCase = proxyServerImportFileUseCase,
            subscriptionFetcher = subscriptionFetcher,
            proxyServiceUseCase = proxyServiceUseCase,
            clipboard = clipboard,
            tipNotifier = tipNotifier,
            scope = scope,
            backgroundScope = services.appScope,
            messages = messages,
            resultKey = ProxyServerEditResultKey,
            serviceOperationInProgress = serviceOperationInProgress,
            runProxyServiceOperation = ::runProxyServiceOperation,
            onSelectedGroupIdChange = { selectedGroupId = it },
            onMoveSubscriptionGroup = { groupId, offset ->
                updateAppState { state -> state.withMovedSubscriptionGroup(groupId, offset) }
            },
            onTestProxyServerLatency = ::testProxyServerLatency,
        )

        features.updater.ui.AppUpdateBanner(
            updateInfo = proxyListState.availableAppUpdate,
            dismissedVersion = proxyListState.dismissedUpdateVersion,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            ProxyServerListPager(
                groupPagerState = groupPagerState,
                scrollToTopRequest = scrollToTopRequest,
                groupState = groupState,
                searchValue = searchValue,
                servers = servers,
                selectedServerId = selectedServerId,
                columns = columns,
                sort = proxyListState.proxyServerListSort,
                unknownGroupName = unknownGroupName,
                itemTextFormatter = itemTextFormatter,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                listPadding = listPadding,
                dragScrollThresholdBottomPadding = dragScrollThresholdBottomPadding,
                contentPadding = contentPadding,
                stateStore = stateStore,
                updateAppState = updateAppState,
                navigator = navigator,
                clipboard = clipboard,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
                resultKey = ProxyServerEditResultKey,
                onSelectedServerIdChange = { selectedServerId = it },
                onDeleteServer = ::requestProxyServerDeletion,
                onUpdateSubscription = ::updateSubscription,
                onUpdateAllSubscriptions = ::updateAllSubscriptions,
                onPingSubscription = ::pingSubscription,
                onEditSubscription = { groupId -> editingSubscriptionGroupId = groupId },
                onOpenSelectGroupMember = { selectingGroupMemberForServer = it },
                pingingGroupIds = pingingGroupIds,
                activeOutboundTag = activeOutboundTag,
            )
            if (showFloatingToolbar) {
                val showPingInFloatingToolbar = proxyListState.connectionDisplayMode == ConnectionDisplayModeCompact
                ProxyServerListFloatingToolbar(
                    running = proxyRunning,
                    serviceOperationInProgress = serviceOperationInProgress,
                    bottomPadding = floatingToolbarBottomPadding,
                    showPingAction = showPingInFloatingToolbar,
                    onToggleRunning = {
                        runProxyServiceOperation {
                            when (
                                val result = proxyServiceUseCase.toggle(
                                    state = stateStore.state.value,
                                    selectedServer = selectedServer,
                                )
                            ) {
                                is ProxyServiceResult.Success -> {
                                    updateAppState { state ->
                                        state.copy(
                                            proxyRunning = result.proxyRunning,
                                            localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                                        )
                                    }
                                    tipNotifier.show(
                                        if (result.proxyRunning) messages.serviceStarted else messages.serviceStopped,
                                    )
                                }

                                ProxyServiceResult.MissingServer -> {
                                    tipNotifier.show(messages.selectServerFirst)
                                }

                                is ProxyServiceResult.Failed -> {
                                    updateAppState { state -> state.copy(proxyRunning = false) }
                                    tipNotifier.showError(result.error, messages.serviceStopped)
                                }
                            }
                        }
                    },
                    onRealConnectionTest = {
                        val server = servers.firstOrNull { it.id == selectedServerId }
                        if (server == null) {
                            scope.launch {
                                tipNotifier.show(messages.selectServerFirst)
                            }
                        } else {
                            val mode = if (proxyListState.subscriptionPingMode == SubscriptionPingModeHttp) {
                                ProxyServerLatencyTestMode.RealConnection
                            } else {
                                ProxyServerLatencyTestMode.TcpConnect
                            }
                            val doneTemplate = if (mode == ProxyServerLatencyTestMode.RealConnection) {
                                messages.realConnectionDoneTemplate
                            } else {
                                messages.latencyDoneTemplate
                            }
                            testProxyServerLatency(
                                targetServers = listOf(server),
                                mode = mode,
                                doneTemplate = doneTemplate,
                                showSingleResult = true,
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }

    pendingProxyServerDeletion?.let { server ->
        DeleteConfirmationDialog(
            show = true,
            title = stringResource(R.string.deletion_confirmation_delete_proxy_server),
            onDismissRequest = { pendingProxyServerDeletion = null },
            onConfirm = {
                pendingProxyServerDeletion = null
                deleteProxyServer(server)
            },
        )
    }
    SubscriptionGroupEditorDialog(
        show = editingSubscriptionGroup != null,
        group = editingSubscriptionGroup,
        nextGroupId = proxyListState.subscriptionGroups.maxOfOrNull { it.id }?.plus(1) ?: 1,
        onDismissRequest = { editingSubscriptionGroupId = null },
        onDismissFinished = {},
        onSave = { group, _ ->
            updateAppState { state ->
                val updatedServers = state.proxyServers.map { serverState ->
                    val server = serverState.server
                    if (serverState.groupId == group.id && server is Custom) {
                        if (server.overrideInboundAndDns != group.autoOverrideRules) {
                            serverState.copy(server = server.copy(overrideInboundAndDns = group.autoOverrideRules))
                        } else {
                            serverState
                        }
                    } else {
                        serverState
                    }
                }
                state.copy(
                    subscriptionGroups = state.subscriptionGroups.map { current ->
                        if (current.id == group.id) group else current
                    },
                    proxyServers = updatedServers,
                )
            }
            if (group.url.isNotBlank() && group.enabled) {
                scope.launch { updateSubscription(group.id) }
            }
        },
        onInvalidUrl = {
            scope.launch { tipNotifier.show(invalidSubscriptionUrlMessage) }
        },
    )
    SelectGroupMemberDialog(
        show = selectingGroupMemberForServer != null,
        groupServer = selectingGroupMemberForServer,
        onDismissRequest = { selectingGroupMemberForServer = null },
        onSelectMember = { memberId ->
            val targetGroup = selectingGroupMemberForServer ?: return@SelectGroupMemberDialog
            updateAppState { state ->
                val updatedServers = state.proxyServers.map { serverState ->
                    if (serverState.id == targetGroup.id && serverState.server is StrategyGroup) {
                        val updatedStrategy = serverState.server.copy(selectedMemberId = memberId)
                        serverState.copy(server = updatedStrategy)
                    } else {
                        serverState
                    }
                }
                state.copy(proxyServers = updatedServers)
            }
            if (proxyRunning) {
                runProxyServiceOperation {
                    proxyServiceUseCase.restart(
                        state = stateStore.state.value,
                        selectedServer = selectedServer,
                    )
                }
            }
        },
    )
}
