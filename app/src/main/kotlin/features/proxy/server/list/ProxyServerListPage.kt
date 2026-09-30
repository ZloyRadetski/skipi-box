// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalProxyPageScrollToTopRequest
import app.LocalUpdateAppState
import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.R
import app.isTestingLatency
import app.activeTrafficConfig
import app.activeTunnelTargetDisplayName
import app.collectAppState
import app.modes.ProxyServerListSortDefault
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName
import app.collectProxyServerListState
import ui.feedback.LocalAppHaptics
import engine.proxy.latency.ProxyServerLatencyTestMode
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.presentation.ProxyHomePresentationAction
import features.proxy.server.presentation.ProxyHomePresentationState
import features.proxy.server.presentation.reduce
import features.proxy.server.usecase.AndroidTunnelController
import features.proxy.server.usecase.ProxyServerLatencyTracker
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import ui.AppTheme
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageWindowPadding
import ui.components.DeleteConfirmationDialog
import app.skipi.ui.home.SkipiProxyHomeScaffold
import app.skipi.ui.home.SkipiProxyHomeScaffoldState
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeEffectHandler
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomePresentation
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyHomeStore
import app.skipi.ui.home.ProxyHomeScreen
import ui.text.formatTemplate
import platform.TunnelConnectRequest
import platform.TunnelFailure
import platform.TunnelPhase
import platform.TunnelSnapshot
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.usecase.ProxyServerCopyTextResult
import features.proxy.server.usecase.ProxyServerCopyTextType
import features.proxy.server.usecase.proxyServerCopyText
import features.proxy.server.validation.rememberProxyServerValidationMessageResolver
import features.settings.currentTunnelMemoryPssKb
import features.settings.formatTunnelMemory
import app.proxyServerIdFromOutboundTag
import app.navigation.TrafficConfigEditorSection
import ui.clipboard.setPlainText
import java.net.URI

private const val ProxyServerEditResultKey = "proxy-server-edit-result"

private val ProxyHomePresentationStateSaver = Saver<ProxyHomePresentationState, List<Any?>>(
    save = { state ->
        listOf(state.selectedGroupId, state.searchQuery, state.searchVisible)
    },
    restore = { values ->
        ProxyHomePresentationState(
            selectedGroupId = values.getOrNull(0) as? String,
            searchQuery = values.getOrNull(1) as? String ?: "",
            searchVisible = values.getOrNull(2) as? Boolean ?: false,
        )
    },
)

@Composable
fun ProxyServerListPage(
    padding: PaddingValues,
    floatingNavigationBottomInset: Dp = 0.dp,
) {
    val isWideScreen = LocalIsWideScreen.current
    val scrollToTopRequest = LocalProxyPageScrollToTopRequest.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
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
    val haptics = LocalAppHaptics.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val serviceRestartMutex = remember { Mutex() }
    val allSubscriptionsUpdateMutex = remember { Mutex() }

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
    var homePresentation by rememberSaveable(stateSaver = ProxyHomePresentationStateSaver) {
        mutableStateOf(
            ProxyHomePresentationState(
                selectedGroupId = initialSelectedGroupId.toString(),
            ),
        )
    }
    val selectedGroupId = homePresentation.selectedGroupId
        ?.toIntOrNull()
        ?: initialSelectedGroupId
    val searchValue = homePresentation.searchQuery

    fun updateHomePresentation(action: ProxyHomePresentationAction) {
        homePresentation = homePresentation.reduce(action)
    }
    var serviceOperationInProgress by remember { mutableStateOf(false) }
    var pendingProxyServerDeletion by remember { mutableStateOf<ProxyServerState?>(null) }
    var pendingSubscriptionGroupDeletion by remember { mutableStateOf<SubscriptionGroupState?>(null) }
    var pendingToolDeletion by remember { mutableStateOf<ProxyServerListToolAction?>(null) }
    var qrCodeDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editingSubscriptionGroupId by rememberSaveable { mutableStateOf<Int?>(null) }
    var creatingSubscriptionGroup by rememberSaveable { mutableStateOf(false) }
    var creatingManualGroup by rememberSaveable { mutableStateOf(false) }
    var selectingGroupMemberForServer by remember { mutableStateOf<ProxyServerState?>(null) }
    var pingingGroupIds by remember { mutableStateOf(emptySet<Int>()) }
    var refreshingSubscriptionGroupIds by remember { mutableStateOf(emptySet<Int>()) }
    var activeGlobalLatencyJob by remember { mutableStateOf<Job?>(null) }
    var activeGlobalLatencyRunId by remember { mutableStateOf<Long?>(null) }
    var latencyRunCounter by remember { mutableStateOf(0L) }
    val activeGroupLatencyJobs = remember { mutableStateMapOf<Int, Job>() }
    val activeGroupLatencyRunIds = remember { mutableStateMapOf<Int, Long>() }
    fun hasActiveLatencyTests(): Boolean =
        stateStore.state.value.proxyServers.any { it.isTestingLatency } ||
            activeGlobalLatencyJob?.isActive == true ||
            activeGroupLatencyJobs.values.any { it.isActive }

    fun allocateLatencyRunId(): Long {
        latencyRunCounter += 1L
        return latencyRunCounter
    }
    val servers = proxyListState.proxyServers
    val editingSubscriptionGroup = editingSubscriptionGroupId?.let { groupId ->
        proxyListState.subscriptionGroups.firstOrNull { group -> group.id == groupId }
    }
    val selectedServerId = proxyListState.selectedProxyServerId
    val selectedServer = servers.firstOrNull { server -> server.id == selectedServerId }
    val proxyRunning = proxyListState.proxyRunning
    val context = LocalContext.current.applicationContext
    val tunnelController = remember(context, proxyEngine, proxyServiceUseCase, stateStore, updateAppState) {
        AndroidTunnelController.forApp(
            context = context,
            proxyEngine = proxyEngine,
            proxyServiceUseCase = proxyServiceUseCase,
            readState = { stateStore.state.value },
            updateState = updateAppState,
        )
    }
    val activeTunnelSample by produceActiveTunnelRuntimeSample(context, proxyRunning)
    val activeOutboundTag = activeTunnelSample?.outboundTag
    val allGroupName = stringResource(R.string.proxy_server_list_all)
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val autoBalancerGroupName = stringResource(R.string.proxy_server_list_auto_balancers)
    val unknownGroupName = stringResource(R.string.common_unknown_group)
    val invalidSubscriptionUrlMessage = stringResource(R.string.subscription_invalid_url)
    val messages = proxyServerListMessages()
    val latestMessages = rememberUpdatedState(messages)
    val validationMessageOf = rememberProxyServerValidationMessageResolver()
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

    fun toggleProxyFromConnectionPanel() {
        haptics.vpnToggle()
        ProxyServerLatencyTracker.cancelAll()
        activeGlobalLatencyJob?.cancel()
        activeGlobalLatencyJob = null
        activeGlobalLatencyRunId = null
        activeGroupLatencyJobs.values.forEach { it.cancel() }
        activeGroupLatencyJobs.clear()
        activeGroupLatencyRunIds.clear()
        pingingGroupIds = emptySet()
        runProxyServiceOperation {
            val currentState = stateStore.state.value
            val snapshot = tunnelController.snapshot()
            val result = when (snapshot.phase) {
                TunnelPhase.Connected -> tunnelController.disconnect()
                TunnelPhase.Failed -> Result.failure(
                    IllegalStateException(snapshot.failure?.message ?: "Failed to read Android VPN state"),
                )

                else -> {
                    val activeServer = currentState.proxyServers
                        .firstOrNull { server -> server.id == currentState.selectedProxyServerId }
                        ?: selectedServer
                    if (activeServer == null) {
                        tipNotifier.show(messages.selectServerFirst)
                        return@runProxyServiceOperation
                    }
                    tunnelController.connect(TunnelConnectRequest(activeServer.id.toString()))
                }
            }
            result.onSuccess {
                val currentPhase = tunnelController.snapshot().phase
                tipNotifier.show(
                    if (currentPhase == TunnelPhase.Connected) messages.serviceStarted else messages.serviceStopped,
                )
            }.onFailure { error ->
                updateAppState { state -> state.copy(proxyRunning = false) }
                tipNotifier.showError(error, messages.serviceStopped)
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
        observeHomeServerSelection(stateStore.state) { serverId ->
            restartProxyServiceSilently(serverId)
        }.join()
    }

    fun cancelAllLatencyTests() {
        ProxyServerLatencyTracker.cancelAll()
        activeGlobalLatencyJob?.cancel()
        activeGlobalLatencyJob = null
        activeGlobalLatencyRunId = null
        activeGroupLatencyJobs.values.forEach { it.cancel() }
        activeGroupLatencyJobs.clear()
        activeGroupLatencyRunIds.clear()
        pingingGroupIds = emptySet()
        services.appScope.launch {
            tipNotifier.show(latestMessages.value.latencyCancelled)
        }
    }

    fun startProxyServerLatencyTest(
        targetServers: List<ProxyServerState>,
        mode: ProxyServerLatencyTestMode,
        doneTemplate: String,
        showSingleResult: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ): Job {
        val latencyMessages = latestMessages.value
        val runId = allocateLatencyRunId()
        activeGlobalLatencyRunId = runId
        val job = runProxyServerLatencyTest(
            targetServers = targetServers,
            mode = mode,
            doneTemplate = doneTemplate,
            showSingleResult = showSingleResult,
            scope = services.appScope,
            stateStore = stateStore,
            updateAppState = updateAppState,
            proxyLatencyTester = proxyLatencyTester,
            tipNotifier = tipNotifier,
            noTestableServersMessage = latencyMessages.noTestableServers,
            latencyResultTemplate = latencyMessages.latencyResultTemplate,
            latencyFailedMessage = latencyMessages.latencyFailed,
            onFinished = {
                if (activeGlobalLatencyRunId == runId) {
                    activeGlobalLatencyJob = null
                    activeGlobalLatencyRunId = null
                }
                onFinished?.invoke()
            },
        )
        if (activeGlobalLatencyRunId == runId) activeGlobalLatencyJob = job
        return job
    }

    fun testProxyServerLatency(
        targetServers: List<ProxyServerState>,
        mode: ProxyServerLatencyTestMode,
        doneTemplate: String,
        showSingleResult: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ): Job? {
        if (hasActiveLatencyTests()) {
            services.appScope.launch { tipNotifier.show(latestMessages.value.pingInProgress) }
            return null
        }
        return startProxyServerLatencyTest(
            targetServers = targetServers,
            mode = mode,
            doneTemplate = doneTemplate,
            showSingleResult = showSingleResult,
            onFinished = onFinished,
        )
    }

    suspend fun updateSubscription(groupId: Int) {
        val group = stateStore.state.value.subscriptionGroups.firstOrNull { it.id == groupId }
            ?: return
        refreshingSubscriptionGroupIds = refreshingSubscriptionGroupIds + groupId
        try {
            val result = updateSubscriptions(
                groups = listOf(group),
                subscriptionFetcher = subscriptionFetcher,
                fetchOptions = { subscription ->
                    stateStore.state.value.toSubscriptionFetchOptions(subscription)
                },
            )
            if (result.updates.isNotEmpty()) {
                val nextState = withContext(Dispatchers.Default) {
                    stateStore.state.value.withUpdatedSubscriptionServers(
                        updates = result.updates,
                        updatedAtMillis = result.updatedAtMillis,
                    )
                }
                updateAppState { nextState }
            }
            tipNotifier.show(
                subscriptionUpdateMessage(
                    result = result,
                    successTemplate = messages.subscriptionUpdateResultTemplate,
                    failedTemplate = messages.subscriptionUpdateResultWithFailedTemplate,
                ),
            )
        } finally {
            refreshingSubscriptionGroupIds = refreshingSubscriptionGroupIds - groupId
        }
    }

    suspend fun updateAllSubscriptions() {
        allSubscriptionsUpdateMutex.withLock {
            services.appScope.launch(Dispatchers.IO) {
                runCatching {
                    services.appUpdateCheckCoordinator.checkLatestRelease()
                }
            }

            val subscriptionGroups = stateStore.state.value.subscriptionGroups.updatableSubscriptionGroups()
            if (subscriptionGroups.isEmpty()) {
                tipNotifier.show(messages.noSubscriptionUpdates)
                return@withLock
            }
            val updatingIds = subscriptionGroups.mapTo(mutableSetOf()) { it.id }
            refreshingSubscriptionGroupIds = refreshingSubscriptionGroupIds + updatingIds
            try {
                val result = updateSubscriptions(
                    groups = subscriptionGroups,
                    subscriptionFetcher = subscriptionFetcher,
                    fetchOptions = { group -> stateStore.state.value.toSubscriptionFetchOptions(group) },
                )
                if (result.updates.isNotEmpty()) {
                    val nextState = withContext(Dispatchers.Default) {
                        stateStore.state.value.withUpdatedSubscriptionServers(
                            updates = result.updates,
                            updatedAtMillis = result.updatedAtMillis,
                        )
                    }
                    updateAppState { nextState }
                }
                tipNotifier.show(
                    subscriptionUpdateMessage(
                        result = result,
                        successTemplate = messages.subscriptionUpdateResultTemplate,
                        failedTemplate = messages.subscriptionUpdateResultWithFailedTemplate,
                    )
                )
            } finally {
                refreshingSubscriptionGroupIds = refreshingSubscriptionGroupIds - updatingIds
            }
        }
    }

    fun pingSubscription(groupId: Int) {
        if (pingingGroupIds.contains(groupId)) {
            activeGroupLatencyJobs.remove(groupId)?.cancel()
            activeGroupLatencyRunIds.remove(groupId)
            pingingGroupIds = pingingGroupIds - groupId
            services.appScope.launch {
                tipNotifier.show(latestMessages.value.latencyCancelled)
            }
            return
        }
        if (hasActiveLatencyTests()) {
            services.appScope.launch { tipNotifier.show(latestMessages.value.pingInProgress) }
            return
        }
        val mode = resolveLatencyTestMode(proxyListState.subscriptionPingMode)
        val doneTemplate = resolveLatencyDoneTemplate(
            mode = mode,
            latencyDoneTemplate = latestMessages.value.latencyDoneTemplate,
            realConnectionDoneTemplate = latestMessages.value.realConnectionDoneTemplate,
        )
        pingingGroupIds = pingingGroupIds + groupId
        val runId = allocateLatencyRunId()
        activeGroupLatencyRunIds[groupId] = runId
        val job = runProxyServerLatencyTest(
            targetServers = servers.filter { server -> server.groupId == groupId },
            mode = mode,
            doneTemplate = doneTemplate,
            showSingleResult = false,
            scope = services.appScope,
            stateStore = stateStore,
            updateAppState = updateAppState,
            proxyLatencyTester = proxyLatencyTester,
            tipNotifier = tipNotifier,
            noTestableServersMessage = latestMessages.value.noTestableServers,
            latencyResultTemplate = latestMessages.value.latencyResultTemplate,
            latencyFailedMessage = latestMessages.value.latencyFailed,
            onFinished = {
                if (activeGroupLatencyRunIds[groupId] == runId) {
                    activeGroupLatencyJobs.remove(groupId)
                    activeGroupLatencyRunIds.remove(groupId)
                    pingingGroupIds = pingingGroupIds - groupId
                }
            },
        )
        if (activeGroupLatencyRunIds[groupId] == runId) activeGroupLatencyJobs[groupId] = job
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

    fun deleteSubscriptionGroup(group: SubscriptionGroupState) {
        if (group.builtIn) return
        if (serviceOperationInProgress) return
        val groupName = group.name

        fun removeGroup(stopResult: ProxyServiceResult.Success? = null): Boolean {
            var deleted = false
            updateAppState { state ->
                val nextGroups = state.subscriptionGroups.filterNot { it.id == group.id }
                if (nextGroups.size == state.subscriptionGroups.size) {
                    stopResult?.let { result ->
                        state.copy(
                            proxyRunning = result.proxyRunning,
                            localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                        )
                    } ?: state
                } else {
                    deleted = true
                    val nextServers = state.proxyServers.filterNot { it.groupId == group.id }
                    val selectedProxyServerId = if (nextServers.any { it.id == state.selectedProxyServerId }) {
                        state.selectedProxyServerId
                    } else {
                        nextServers.firstOrNull()?.id ?: 0
                    }
                    state.copy(
                        subscriptionGroups = nextGroups,
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
        val groupServerIds = stateSnapshot.proxyServers.filter { it.groupId == group.id }.map { it.id }.toSet()
        val isCurrentRunningServerInGroup = stateSnapshot.proxyRunning && groupServerIds.contains(stateSnapshot.selectedProxyServerId)

        if (!isCurrentRunningServerInGroup) {
            if (removeGroup()) {
                services.appScope.launch {
                    tipNotifier.show(messages.deletedTemplate.formatTemplate("name" to groupName))
                }
            }
            return
        }

        runProxyServiceOperation {
            when (val stopResult = proxyServiceUseCase.stop(stateStore.state.value.runMode)) {
                is ProxyServiceResult.Success -> {
                    if (removeGroup(stopResult)) {
                        tipNotifier.show(messages.deletedTemplate.formatTemplate("name" to groupName))
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

    ProxyServerEditResultHandler(
        navigator = navigator,
        resultKey = ProxyServerEditResultKey,
        messages = messages,
        updateAppState = updateAppState,
        tipNotifier = tipNotifier,
        onSelectedGroupIdChange = { groupId ->
            updateHomePresentation(ProxyHomePresentationAction.SelectGroup(groupId.toString()))
        },
    )

    val groupState = proxyServerListGroups(
        state = proxyListState,
        selectedGroupId = selectedGroupId,
        searchValue = searchValue,
        allGroupName = allGroupName,
        defaultGroupName = defaultGroupName,
        autoBalancerGroupName = autoBalancerGroupName,
    )
    val homeServerSummaryFormatter = rememberProxyServerListItemTextFormatter(
        groupNames = groupState.groupNames,
        unknownGroupName = unknownGroupName,
    )
    var measuredTunnelSnapshot by remember { mutableStateOf(TunnelSnapshot()) }
    LaunchedEffect(tunnelController, proxyRunning, serviceOperationInProgress) {
        do {
            measuredTunnelSnapshot = runCatching { tunnelController.snapshot() }
                .getOrElse { failure ->
                    TunnelSnapshot(
                        phase = TunnelPhase.Failed,
                        failure = TunnelFailure(
                            code = "android_tunnel_snapshot_failed",
                            message = failure.message ?: "Could not read Android VPN state.",
                            recoverable = true,
                        ),
                    )
                }
            if (!serviceOperationInProgress) break
            kotlinx.coroutines.delay(250)
        } while (true)
    }
    val tunnelSnapshot = measuredTunnelSnapshot
    val tunnelMemoryKb by produceState(
        initialValue = 0L,
        context,
        proxyRunning,
        proxyListState.showTunnelMemoryOnHome,
    ) {
        while (true) {
            value = if (proxyRunning && proxyListState.showTunnelMemoryOnHome) {
                context.currentTunnelMemoryPssKb()
            } else {
                0L
            }
            kotlinx.coroutines.delay(3_000)
        }
    }
    val runtimeOutboundMetric = if (
        proxyRunning && proxyListState.showTunnelMemoryOnHome && tunnelMemoryKb > 0L
    ) {
        formatTunnelMemory(tunnelMemoryKb)
    } else {
        null
    }
    val directName = stringResource(R.string.routing_outbound_direct)
    val blockName = stringResource(R.string.routing_outbound_block)
    val activeTunnelTitle = remember(appState, activeTunnelSample, directName, blockName) {
        appState.activeTunnelTargetDisplayName(
            runtime = activeTunnelSample?.runtime,
            activeOutboundTag = activeTunnelSample?.outboundTag,
            directName = directName,
            blockName = blockName,
        )
    }
    val homeInput = remember(
        appState,
        proxyListState,
        groupState,
        tunnelSnapshot,
        serviceOperationInProgress,
        refreshingSubscriptionGroupIds,
        pingingGroupIds,
        homeServerSummaryFormatter,
        runtimeOutboundMetric,
        activeTunnelTitle,
    ) {
        appState.toProxyHomeInput(
            groupState = groupState,
            tunnelSnapshot = tunnelSnapshot,
            tunnelBusy = serviceOperationInProgress,
            isTestingLatency = proxyListState.proxyServers.any { it.isTestingLatency },
            refreshingSubscriptionGroupIds = refreshingSubscriptionGroupIds,
            pingingSubscriptionGroupIds = pingingGroupIds,
            presentationFormatter = homeServerSummaryFormatter,
            runtimeOutboundMetric = runtimeOutboundMetric,
        ).let { input ->
            if (proxyRunning) input.copy(selectedServerTitle = activeTunnelTitle) else input
        }
    }
    val latestHomeInput = rememberUpdatedState(homeInput)
    val homeStoreReference = remember { mutableStateOf<ProxyHomeStore?>(null) }
    val latencyCoordinator = remember {
        ProxyHomeLatencyCoordinator(
            getSubscriptionPingMode = { stateStore.state.value.subscriptionPingMode },
            getLatencyDoneTemplate = { latestMessages.value.latencyDoneTemplate },
            getRealConnectionDoneTemplate = { latestMessages.value.realConnectionDoneTemplate },
            getAllServers = { stateStore.state.value.proxyServers },
            getGroupMemberIds = { groupId ->
                latestHomeInput.value.groups.firstOrNull { it.id == groupId }?.serverIds
            },
            isHostBusy = { hasActiveLatencyTests() },
            launchTest = { targets, mode, template, onFinished ->
                startProxyServerLatencyTest(
                    targetServers = targets,
                    mode = mode,
                    doneTemplate = template,
                    showSingleResult = false,
                    onFinished = onFinished,
                )
            },
            onCancel = {
                cancelAllLatencyTests()
            },
        )
    }
    val latestEffectHandler = rememberUpdatedState(
        ProxyHomeEffectHandler { effect ->
            try {
                when (effect) {
                    ProxyHomeEffect.ToggleTunnel -> toggleProxyFromConnectionPanel()
                    is ProxyHomeEffect.SelectServer -> {
                        val serverId = effect.serverId.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid server ID."))
                        val server = stateStore.state.value.proxyServers.firstOrNull { it.id == serverId }
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Proxy server no longer exists."))
                        if (serverId != stateStore.state.value.selectedProxyServerId) haptics.serverSelected()
                        stateStore.proxyServerRepository.select(serverId)
                        val strategy = server.server as? StrategyGroup
                        if (strategy?.strategy == StrategyGroupConstants.TYPE_SELECT) {
                            selectingGroupMemberForServer = server
                        }
                    }
                    is ProxyHomeEffect.TestServer -> latencyCoordinator.testVisibleServers(listOf(effect.serverId))
                    is ProxyHomeEffect.TestVisibleServers -> latencyCoordinator.testVisibleServers(effect.serverIds)
                    is ProxyHomeEffect.TestGroup -> latencyCoordinator.testGroup(effect.groupId)
                    ProxyHomeEffect.CancelLatencyTests -> latencyCoordinator.cancel()
                    is ProxyHomeEffect.RefreshSubscription -> {
                        val groupId = effect.id.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid subscription ID."))
                        updateSubscription(groupId)
                    }
                    ProxyHomeEffect.RefreshAllSubscriptions -> updateAllSubscriptions()
                    is ProxyHomeEffect.PingSubscription -> {
                        val groupId = effect.id.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid subscription ID."))
                        pingSubscription(groupId)
                    }
                    is ProxyHomeEffect.ToggleSubscriptionEnabled -> {
                        val groupId = effect.id.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid subscription ID."))
                        updateAppState { state ->
                            state.copy(
                                subscriptionGroups = state.subscriptionGroups.map { group ->
                                    if (group.id == groupId) group.copy(enabled = !group.enabled) else group
                                },
                            )
                        }
                    }
                    is ProxyHomeEffect.AddServer -> {
                        val action = when (effect.kind) {
                            ProxyHomeServerKind.Http -> ProxyServerListAddAction.HTTP
                            ProxyHomeServerKind.Vmess -> ProxyServerListAddAction.VMess
                            ProxyHomeServerKind.Vless -> ProxyServerListAddAction.VLESS
                            ProxyHomeServerKind.Trojan -> ProxyServerListAddAction.Trojan
                            ProxyHomeServerKind.Shadowsocks -> ProxyServerListAddAction.Shadowsocks
                            ProxyHomeServerKind.Socks -> ProxyServerListAddAction.Socks
                            ProxyHomeServerKind.Hysteria2 -> ProxyServerListAddAction.Hysteria2
                            ProxyHomeServerKind.Wireguard -> ProxyServerListAddAction.Wireguard
                            ProxyHomeServerKind.AmneziaWg -> ProxyServerListAddAction.AmneziaWg
                            ProxyHomeServerKind.OlcRtc -> ProxyServerListAddAction.OlcRtc
                            ProxyHomeServerKind.StrategyGroup -> ProxyServerListAddAction.StrategyGroup
                            ProxyHomeServerKind.ChainProxy -> ProxyServerListAddAction.ChainProxy
                            ProxyHomeServerKind.Custom -> ProxyServerListAddAction.Custom
                        }
                        handleProxyServerListAddAction(
                            action = action,
                            groupState = groupState,
                            proxyListState = proxyListState,
                            stateStore = stateStore,
                            updateAppState = updateAppState,
                            navigator = navigator,
                            qrScanner = qrScanner,
                            proxyServerImportFileUseCase = proxyServerImportFileUseCase,
                            subscriptionFetcher = subscriptionFetcher,
                            clipboard = clipboard,
                            tipNotifier = tipNotifier,
                            scope = scope,
                            backgroundScope = services.appScope,
                            messages = messages,
                            resultKey = ProxyServerEditResultKey,
                        )
                    }
                    ProxyHomeEffect.AddSubscription -> {
                        editingSubscriptionGroupId = null
                        creatingSubscriptionGroup = true
                        creatingManualGroup = false
                    }
                    is ProxyHomeEffect.ImportServers -> {
                        val action = when (effect.source) {
                            ProxyHomeImportSource.QrCode -> ProxyServerListAddAction.ScanQrCode
                            ProxyHomeImportSource.Clipboard -> ProxyServerListAddAction.Clipboard
                            ProxyHomeImportSource.File -> ProxyServerListAddAction.File
                            ProxyHomeImportSource.ManualInput -> return@ProxyHomeEffectHandler Result.failure(
                                IllegalArgumentException("Android does not provide manual text import."),
                            )
                        }
                        handleProxyServerListAddAction(
                            action = action,
                            groupState = groupState,
                            proxyListState = proxyListState,
                            stateStore = stateStore,
                            updateAppState = updateAppState,
                            navigator = navigator,
                            qrScanner = qrScanner,
                            proxyServerImportFileUseCase = proxyServerImportFileUseCase,
                            subscriptionFetcher = subscriptionFetcher,
                            clipboard = clipboard,
                            tipNotifier = tipNotifier,
                            scope = scope,
                            backgroundScope = services.appScope,
                            messages = messages,
                            resultKey = ProxyServerEditResultKey,
                        )
                    }
                    is ProxyHomeEffect.EditServer -> {
                        val server = effect.id.toIntOrNull()?.let { id ->
                            stateStore.state.value.proxyServers.firstOrNull { it.id == id }
                        } ?: return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Proxy server no longer exists."))
                        val sourceTrafficConfigId = (server.server as? StrategyGroup)?.sourceTrafficConfigId
                        if (sourceTrafficConfigId != null) {
                            navigator.push(
                                app.navigation.Route.TrafficConfigSection(
                                    trafficConfigId = sourceTrafficConfigId,
                                    section = TrafficConfigEditorSection.ProxyGroups,
                                ),
                            )
                        } else {
                            navigator.navigateForResult(
                                route = app.navigation.Route.ProxyServerEditor(
                                    ps = server.server,
                                    serverId = server.id,
                                    groupId = server.groupId,
                                    returnGroupId = selectedGroupId,
                                    resultKey = ProxyServerEditResultKey,
                                ),
                                requestKey = ProxyServerEditResultKey,
                            )
                        }
                    }
                    is ProxyHomeEffect.DeleteServer -> {
                        val server = effect.id.toIntOrNull()?.let { id ->
                            stateStore.state.value.proxyServers.firstOrNull { it.id == id }
                        } ?: return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Proxy server no longer exists."))
                        requestProxyServerDeletion(server)
                    }
                    is ProxyHomeEffect.ShowServerQr,
                    is ProxyHomeEffect.CopyServer -> {
                        val serverId = when (effect) {
                            is ProxyHomeEffect.ShowServerQr -> effect.id
                            is ProxyHomeEffect.CopyServer -> effect.id
                            else -> error("Unreachable copy effect")
                        }.toIntOrNull() ?: return@ProxyHomeEffectHandler Result.failure(
                            IllegalArgumentException("Invalid server ID."),
                        )
                        val server = stateStore.state.value.proxyServers.firstOrNull { it.id == serverId }
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Proxy server no longer exists."))
                        val format = when (effect) {
                            is ProxyHomeEffect.ShowServerQr -> ProxyHomeCopyFormat.QrCode
                            is ProxyHomeEffect.CopyServer -> effect.format
                            else -> error("Unreachable copy effect")
                        }
                        val issues = server.server.validateBasic()
                        if (issues.isNotEmpty()) {
                            tipNotifier.show(validationMessageOf(issues.first()))
                        } else {
                            val type = if (format == ProxyHomeCopyFormat.FullJson) {
                                ProxyServerCopyTextType.FullJson
                            } else {
                                ProxyServerCopyTextType.Url
                            }
                            when (val result = server.proxyServerCopyText(appState = stateStore.state.value, type = type)) {
                                is ProxyServerCopyTextResult.Success -> {
                                    if (format == ProxyHomeCopyFormat.QrCode) {
                                        qrCodeDialogState = server.server.getInfo().remarks to result.text
                                    } else {
                                        clipboard.setPlainText(result.text)
                                        tipNotifier.show(messages.copied)
                                    }
                                }
                                ProxyServerCopyTextResult.Unsupported -> tipNotifier.show(messages.unsupported)
                                ProxyServerCopyTextResult.InvalidConfig -> tipNotifier.show(messages.configInvalid)
                            }
                        }
                    }
                    is ProxyHomeEffect.EditSubscription -> {
                        val groupId = effect.id.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid subscription ID."))
                        if (stateStore.state.value.subscriptionGroups.none { it.id == groupId }) {
                            return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Subscription no longer exists."))
                        }
                        editingSubscriptionGroupId = groupId
                    }
                    is ProxyHomeEffect.EditGroup -> {
                        val requestedGroupId = effect.groupId
                        if (requestedGroupId == null) {
                            editingSubscriptionGroupId = null
                            creatingSubscriptionGroup = false
                            creatingManualGroup = true
                        } else {
                            val groupId = requestedGroupId.toIntOrNull()
                                ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid group ID."))
                            if (stateStore.state.value.subscriptionGroups.none { it.id == groupId }) {
                                return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Group no longer exists."))
                            }
                            editingSubscriptionGroupId = groupId
                        }
                    }
                    is ProxyHomeEffect.DeleteGroup -> {
                        val groupId = effect.groupId.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid group ID."))
                        val group = stateStore.state.value.subscriptionGroups.firstOrNull { it.id == groupId }
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Group no longer exists."))
                        if (group.builtIn) return@ProxyHomeEffectHandler Result.failure(
                            IllegalArgumentException("Built-in groups cannot be deleted."),
                        )
                        if (proxyListState.enableDeletionConfirmation) {
                            pendingSubscriptionGroupDeletion = group
                        } else {
                            deleteSubscriptionGroup(group)
                            if (selectedGroupId == group.id) {
                                updateHomePresentation(
                                    ProxyHomePresentationAction.SelectGroup(DefaultSubscriptionGroupId.toString()),
                                )
                            }
                        }
                    }
                    is ProxyHomeEffect.MoveGroup -> {
                        val groupId = effect.groupId.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid group ID."))
                        updateAppState { state -> state.withMovedSubscriptionGroup(groupId, effect.offset) }
                    }
                    is ProxyHomeEffect.MoveServer -> {
                        val serverId = effect.serverId.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid server ID."))
                        val orderedIds = homeStoreReference.value?.uiState?.value?.servers
                            ?.mapNotNull { it.id.toIntOrNull() }
                            ?: groupState.currentFilteredServers.map { it.id }
                        val fromIndex = orderedIds.indexOf(serverId)
                        val toId = orderedIds.getOrNull(fromIndex + effect.offset)
                            ?: return@ProxyHomeEffectHandler Result.success(Unit)
                        updateAppState { state ->
                            val fromBackingIndex = state.proxyServers.indexOfFirst { it.id == serverId }
                            val toBackingIndex = state.proxyServers.indexOfFirst { it.id == toId }
                            if (fromBackingIndex < 0 || toBackingIndex < 0) {
                                state
                            } else {
                                state.copy(
                                    proxyServers = state.proxyServers.toMutableList().also { items ->
                                        val moved = items[fromBackingIndex]
                                        items[fromBackingIndex] = items[toBackingIndex]
                                        items[toBackingIndex] = moved
                                    },
                                )
                            }
                        }
                    }
                    is ProxyHomeEffect.OpenStrategyMemberPicker -> {
                        val serverId = effect.serverId.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid server ID."))
                        val server = stateStore.state.value.proxyServers.firstOrNull { it.id == serverId }
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Proxy server no longer exists."))
                        if (server.server !is StrategyGroup) return@ProxyHomeEffectHandler Result.failure(
                            IllegalArgumentException("This server is not a strategy group."),
                        )
                        selectingGroupMemberForServer = server
                    }
                    is ProxyHomeEffect.SelectStrategyMember -> {
                        val serverId = effect.serverId.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid server ID."))
                        val memberId = effect.memberId.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid member ID."))
                        val target = stateStore.state.value.proxyServers.firstOrNull { it.id == serverId }
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Proxy server no longer exists."))
                        if (target.server !is StrategyGroup) return@ProxyHomeEffectHandler Result.failure(
                            IllegalArgumentException("This server is not a strategy group."),
                        )
                        updateAppState { state ->
                            state.copy(
                                proxyServers = state.proxyServers.map { candidate ->
                                    if (candidate.id == serverId && candidate.server is StrategyGroup) {
                                        candidate.copy(server = candidate.server.copy(selectedMemberId = memberId))
                                    } else {
                                        candidate
                                    }
                                },
                            )
                        }
                        if (stateStore.state.value.proxyRunning) {
                            runProxyServiceOperation {
                                val currentState = stateStore.state.value
                                val currentSelected = currentState.proxyServers.firstOrNull {
                                    it.id == currentState.selectedProxyServerId
                                }
                                proxyServiceUseCase.restart(state = currentState, selectedServer = currentSelected)
                            }
                        }
                    }
                    is ProxyHomeEffect.SetSort -> {
                        val sort = when (effect.mode) {
                            ProxyHomeSortMode.Default -> ProxyServerListSortDefault
                            ProxyHomeSortMode.Name -> ProxyServerListSortName
                            ProxyHomeSortMode.Latency -> ProxyServerListSortLatency
                        }
                        updateAppState { state -> state.copy(proxyServerListSort = sort) }
                    }
                    is ProxyHomeEffect.RunServerTool -> {
                        val action = when (effect.tool) {
                            ProxyHomeServerTool.RestartService -> ProxyServerListToolAction.RestartService
                            ProxyHomeServerTool.UpdateSubscriptions -> ProxyServerListToolAction.UpdateSubscriptions
                            ProxyHomeServerTool.DeleteDuplicateServers -> ProxyServerListToolAction.DeleteDuplicateServers
                            ProxyHomeServerTool.DeleteInvalidServers -> ProxyServerListToolAction.DeleteInvalidServers
                            ProxyHomeServerTool.DeleteAllServers -> ProxyServerListToolAction.DeleteAllServers
                        }
                        if (action.isDeletion && proxyListState.enableDeletionConfirmation) {
                            pendingToolDeletion = action
                        } else {
                            handleProxyServerListToolAction(
                                action = action,
                                groupState = groupState,
                                selectedServer = stateStore.state.value.proxyServers.firstOrNull {
                                    it.id == stateStore.state.value.selectedProxyServerId
                                },
                                proxyListState = proxyListState,
                                stateStore = stateStore,
                                updateAppState = updateAppState,
                                subscriptionFetcher = subscriptionFetcher,
                                proxyServiceUseCase = proxyServiceUseCase,
                                clipboard = clipboard,
                                tipNotifier = tipNotifier,
                                scope = scope,
                                backgroundScope = services.appScope,
                                messages = messages,
                                serviceOperationInProgress = serviceOperationInProgress,
                                runProxyServiceOperation = ::runProxyServiceOperation,
                                onTestProxyServerLatency = { targets, mode, template, single ->
                                    testProxyServerLatency(targets, mode, template, single)
                                },
                            )
                        }
                    }
                    is ProxyHomeEffect.OpenExternalLink -> {
                        val safeUri = requireSafeAndroidExternalUri(effect.url)
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(safeUri.toASCIIString()))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                Result.success(Unit)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
        },
    )
    val androidEffectBridge = rememberUpdatedState(
        ProxyHomeAndroidEffectBridge(
            onToggleTunnel = { latestEffectHandler.value.handle(ProxyHomeEffect.ToggleTunnel) },
            onSelectServer = { serverId ->
                latestEffectHandler.value.handle(ProxyHomeEffect.SelectServer(serverId))
            },
            latencyCoordinator = latencyCoordinator,
            onOtherEffect = { effect -> latestEffectHandler.value.handle(effect) },
        ),
    )
    val homeStore = remember(stateStore) {
        ProxyHomeStore(
            initialInput = homeInput,
            initialPresentation = ProxyHomePresentation(
                selectedGroupId = homePresentation.selectedGroupId,
                searchQuery = homePresentation.searchQuery,
                isSearchVisible = homePresentation.searchVisible,
            ),
            scope = scope,
            effectHandler = ProxyHomeEffectHandler { effect -> androidEffectBridge.value.handle(effect) },
        )
    }
    androidx.compose.runtime.SideEffect { homeStoreReference.value = homeStore }
    LaunchedEffect(homeStore, homeInput) { homeStore.updateInput(homeInput) }
    val homeUiState by homeStore.uiState.collectAsState()
    LaunchedEffect(homeStore, homeUiState.selectedGroupId, homeUiState.searchQuery, homeUiState.isSearchVisible) {
        val nextPresentation = ProxyHomePresentationState(
            selectedGroupId = homeUiState.selectedGroupId,
            searchQuery = homeUiState.searchQuery,
            searchVisible = homeUiState.isSearchVisible,
        )
        if (homePresentation != nextPresentation) homePresentation = nextPresentation
    }
    val homeContentPadding = pageContentPaddingWithCutout(
        innerPadding = PaddingValues(0.dp),
        outerPadding = padding,
        isWideScreen = isWideScreen,
    )

    Column(
        modifier = Modifier.fillMaxSize().pageWindowPadding(padding),
    ) {
        features.updater.ui.AppUpdateBanner()
        features.routing.ui.UnappliedRulesWarningNotification(
            unappliedRules = proxyListState.unappliedRoutingRules,
            onDismiss = { updateAppState { state -> state.copy(unappliedRoutingRules = emptyList()) } },
        )
        ProxyHomeScreen(
            store = homeStore,
            modifier = Modifier.weight(1f),
            contentPadding = homeContentPadding,
            floatingNavigationBottomInset = if (isWideScreen) {
                0.dp
            } else {
                maxOf(homeContentPadding.calculateBottomPadding(), floatingNavigationBottomInset)
            },
        )
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
    pendingSubscriptionGroupDeletion?.let { group ->
        val isManual = group.builtIn || group.url.isBlank()
        DeleteConfirmationDialog(
            show = true,
            title = if (isManual) {
                stringResource(R.string.deletion_confirmation_delete_group)
            } else {
                stringResource(R.string.deletion_confirmation_delete_subscription_group)
            },
            onDismissRequest = { pendingSubscriptionGroupDeletion = null },
            onConfirm = {
                pendingSubscriptionGroupDeletion = null
                deleteSubscriptionGroup(group)
                if (selectedGroupId == group.id) {
                    updateHomePresentation(
                        ProxyHomePresentationAction.SelectGroup(DefaultSubscriptionGroupId.toString()),
                    )
                }
            },
        )
    }
    pendingToolDeletion?.let { action ->
        DeleteConfirmationDialog(
            show = true,
            title = stringResource(action.deletionConfirmationTitleResId),
            onDismissRequest = { pendingToolDeletion = null },
            onConfirm = {
                pendingToolDeletion = null
                handleProxyServerListToolAction(
                    action = action,
                    groupState = groupState,
                    selectedServer = stateStore.state.value.proxyServers.firstOrNull {
                        it.id == stateStore.state.value.selectedProxyServerId
                    },
                    proxyListState = proxyListState,
                    stateStore = stateStore,
                    updateAppState = updateAppState,
                    subscriptionFetcher = subscriptionFetcher,
                    proxyServiceUseCase = proxyServiceUseCase,
                    clipboard = clipboard,
                    tipNotifier = tipNotifier,
                    scope = scope,
                    backgroundScope = services.appScope,
                    messages = messages,
                    serviceOperationInProgress = serviceOperationInProgress,
                    runProxyServiceOperation = ::runProxyServiceOperation,
                    onTestProxyServerLatency = { targets, mode, template, single ->
                        testProxyServerLatency(targets, mode, template, single)
                    },
                )
            },
        )
    }
    qrCodeDialogState?.let { (title, text) ->
        ProxyServerQrCodeDialog(
            title = title,
            text = text,
            onDismissRequest = { qrCodeDialogState = null },
        )
    }
    val isManualGroup = creatingManualGroup || (editingSubscriptionGroup?.let { it.builtIn || it.url.isBlank() } ?: false)
    SubscriptionGroupEditorDialog(
        show = creatingSubscriptionGroup || creatingManualGroup || editingSubscriptionGroup != null,
        group = editingSubscriptionGroup,
        isManualGroup = isManualGroup,
        nextGroupId = maxOf(
            appState.nextSubscriptionGroupId,
            (proxyListState.subscriptionGroups.maxOfOrNull { it.id } ?: 0) + 1,
        ),
        onDismissRequest = {
            editingSubscriptionGroupId = null
            creatingSubscriptionGroup = false
            creatingManualGroup = false
        },
        onDismissFinished = {},
        onSave = { group, isNew ->
            val wasUrlBlank = editingSubscriptionGroup?.url.isNullOrBlank()
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
                    subscriptionGroups = if (isNew) {
                        state.subscriptionGroups.filterNot { current -> current.id == group.id } + group
                    } else {
                        state.subscriptionGroups.map { current ->
                            if (current.id == group.id) group else current
                        }
                    },
                    nextSubscriptionGroupId = maxOf(state.nextSubscriptionGroupId, group.id + 1),
                    proxyServers = updatedServers,
                )
            }
            editingSubscriptionGroupId = null
            creatingSubscriptionGroup = false
            creatingManualGroup = false
            if ((isNew || wasUrlBlank) && group.url.isNotBlank() && group.enabled) {
                scope.launch { updateSubscription(group.id) }
            }
        },
        onDelete = { group ->
            editingSubscriptionGroupId = null
            creatingSubscriptionGroup = false
            creatingManualGroup = false
            deleteSubscriptionGroup(group)
            if (selectedGroupId == group.id) {
                updateHomePresentation(
                    ProxyHomePresentationAction.SelectGroup(DefaultSubscriptionGroupId.toString()),
                )
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
                    val serverImpl = serverState.server
                    if (serverState.id == targetGroup.id && serverImpl is StrategyGroup) {
                        val updatedStrategy = serverImpl.copy(selectedMemberId = memberId)
                        serverState.copy(server = updatedStrategy)
                    } else {
                        serverState
                    }
                }
                state.copy(proxyServers = updatedServers)
            }
            if (proxyRunning) {
                runProxyServiceOperation {
                    val currentState = stateStore.state.value
                    val currentSelected = currentState.proxyServers.firstOrNull { it.id == currentState.selectedProxyServerId }
                    proxyServiceUseCase.restart(
                        state = currentState,
                        selectedServer = currentSelected,
                    )
                }
            }
        },
    )
}
