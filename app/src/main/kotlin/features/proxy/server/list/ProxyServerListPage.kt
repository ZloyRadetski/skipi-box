// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import features.proxy.server.usecase.ProxyServerLatencyTracker
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.restartProxyServiceAfterSelection
import features.proxy.server.usecase.runProxyServerLatencyTest
import features.proxy.server.usecase.updatableSubscriptionGroups
import features.proxy.server.usecase.applyProxySubscriptionUpdates
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
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.moveProxyHomeItem
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationActionOutcome
import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeEffectHandler
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomePresentation
import app.skipi.app.home.ProxyHomeQrPayload
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.reduceProxyHomePresentation
import app.skipi.ui.home.ProxyHomeScreen
import app.skipi.ui.home.rememberSaveableProxyHomePresentation
import app.skipi.ui.home.rememberSaveableProxyHomeDialogsState
import data.repository.toSubscriptionRecord
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
    var homePresentation by rememberSaveableProxyHomePresentation(
        ProxyHomePresentation(selectedGroupId = initialSelectedGroupId.toString()),
    )
    val selectedGroupId = homePresentation.selectedGroupId
        ?.toIntOrNull()
        ?: initialSelectedGroupId
    val searchValue = homePresentation.searchQuery

    fun updateHomePresentation(action: ProxyHomeAction) {
        homePresentation = reduceProxyHomePresentation(homePresentation, action)
    }
    var serviceOperationInProgress by remember { mutableStateOf(false) }
    var homeDialogs by rememberSaveableProxyHomeDialogsState<
        ProxyServerState,
        SubscriptionGroupState,
        ProxyServerListToolAction,
        Int,
    >()
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
    val editingSubscriptionGroup = homeDialogs.editingSubscriptionGroupId?.let { groupId ->
        proxyListState.subscriptionGroups.firstOrNull { group -> group.id == groupId }
    }
    val selectedServerId = proxyListState.selectedProxyServerId
    val selectedServer = servers.firstOrNull { server -> server.id == selectedServerId }
    val proxyRunning = proxyListState.proxyRunning
    val context = LocalContext.current.applicationContext
    val tunnelController = services.tunnelController
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
                applyProxySubscriptionUpdates(
                    stateStore = stateStore,
                    updates = result.updates,
                    updatedAtMillis = result.updatedAtMillis,
                    updateAppState = updateAppState,
                )
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
                    applyProxySubscriptionUpdates(
                        stateStore = stateStore,
                        updates = result.updates,
                        updatedAtMillis = result.updatedAtMillis,
                        updateAppState = updateAppState,
                    )
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
            if (stateStore.currentState.proxyServers.none { it.id == server.id }) return false
            services.sharedApplicationStore.dispatch(SharedApplicationAction.RemoveProxyServer(server.id))
            if (stopResult != null) {
                updateAppState { state ->
                    state.copy(
                        proxyRunning = stopResult.proxyRunning,
                        localProxyPort = stopResult.appState?.localProxyPort ?: state.localProxyPort,
                    )
                }
            }
            return true
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
            homeDialogs = homeDialogs.copy(pendingServerDeletion = server)
        } else {
            deleteProxyServer(server)
        }
    }

    fun deleteSubscriptionGroup(group: SubscriptionGroupState) {
        if (group.builtIn) return
        if (serviceOperationInProgress) return
        val groupName = group.name

        suspend fun removeGroup(stopResult: ProxyServiceResult.Success? = null): Boolean {
            if (stateStore.currentState.subscriptionGroups.none { it.id == group.id && !it.builtIn }) return false
            try {
                services.sharedApplicationStore.subscriptionEditorController.remove(group.id)
            } catch (failure: kotlinx.coroutines.CancellationException) {
                throw failure
            } catch (failure: Exception) {
                tipNotifier.showError(failure)
                return false
            }
            if (stopResult != null) {
                updateAppState { state ->
                    state.copy(
                        proxyRunning = stopResult.proxyRunning,
                        localProxyPort = stopResult.appState?.localProxyPort ?: state.localProxyPort,
                    )
                }
            }
            if (selectedGroupId == group.id) {
                updateHomePresentation(ProxyHomeAction.SelectGroup(DefaultSubscriptionGroupId.toString()))
            }
            tipNotifier.show(messages.deletedTemplate.formatTemplate("name" to groupName))
            return true
        }

        val stateSnapshot = stateStore.state.value
        val groupServerIds = stateSnapshot.proxyServers.filter { it.groupId == group.id }.map { it.id }.toSet()
        val isCurrentRunningServerInGroup = stateSnapshot.proxyRunning && groupServerIds.contains(stateSnapshot.selectedProxyServerId)

        if (!isCurrentRunningServerInGroup) {
            services.appScope.launch { removeGroup() }
            return
        }

        runProxyServiceOperation {
            when (val stopResult = proxyServiceUseCase.stop(stateStore.state.value.runMode)) {
                is ProxyServiceResult.Success -> {
                    removeGroup(stopResult)
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
        sharedApplicationStore = services.sharedApplicationStore,
        tipNotifier = tipNotifier,
        onSelectedGroupIdChange = { groupId ->
            updateHomePresentation(ProxyHomeAction.SelectGroup(groupId.toString()))
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
                        val selectionResult = services.sharedApplicationStore.dispatchAndAwait(
                            SharedApplicationAction.SelectProxyServer(serverId),
                        )
                        when (val outcome = selectionResult.outcome) {
                            SharedApplicationActionOutcome.Completed -> Unit
                            is SharedApplicationActionOutcome.Rejected ->
                                return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException(outcome.reason))
                            is SharedApplicationActionOutcome.Failed ->
                                return@ProxyHomeEffectHandler Result.failure(IllegalStateException(outcome.reason))
                        }
                        val strategy = server.server as? StrategyGroup
                        if (strategy?.strategy == StrategyGroupConstants.TYPE_SELECT) {
                            homeDialogs = homeDialogs.copy(selectingGroupMemberForServer = server)
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
                        services.appScope.launch {
                            val current = stateStore.state.value.subscriptionGroups.firstOrNull { it.id == groupId }
                                ?: return@launch
                            services.sharedApplicationStore.subscriptionEditorController.setEnabled(
                                subscriptionId = groupId,
                                enabled = !current.enabled,
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
                        homeDialogs = homeDialogs.copy(editingSubscriptionGroupId = null)
                        homeDialogs = homeDialogs.copy(creatingSubscriptionGroup = true)
                        homeDialogs = homeDialogs.copy(creatingManualGroup = false)
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
                                        homeDialogs = homeDialogs.copy(qrPayload = ProxyHomeQrPayload(server.server.getInfo().remarks, result.text))
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
                        homeDialogs = homeDialogs.copy(editingSubscriptionGroupId = groupId)
                    }
                    is ProxyHomeEffect.EditGroup -> {
                        val requestedGroupId = effect.groupId
                        if (requestedGroupId == null) {
                            homeDialogs = homeDialogs.copy(editingSubscriptionGroupId = null)
                            homeDialogs = homeDialogs.copy(creatingSubscriptionGroup = false)
                            homeDialogs = homeDialogs.copy(creatingManualGroup = true)
                        } else {
                            val groupId = requestedGroupId.toIntOrNull()
                                ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid group ID."))
                            if (stateStore.state.value.subscriptionGroups.none { it.id == groupId }) {
                                return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Group no longer exists."))
                            }
                            homeDialogs = homeDialogs.copy(editingSubscriptionGroupId = groupId)
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
                            homeDialogs = homeDialogs.copy(pendingSubscriptionDeletion = group)
                        } else {
                            deleteSubscriptionGroup(group)
                            if (selectedGroupId == group.id) {
                                updateHomePresentation(
                                    ProxyHomeAction.SelectGroup(DefaultSubscriptionGroupId.toString()),
                                )
                            }
                        }
                    }
                    is ProxyHomeEffect.MoveGroup -> {
                        val groupId = effect.groupId.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid group ID."))
                        services.appScope.launch {
                            services.sharedApplicationStore.subscriptionEditorController.move(
                                groupId = groupId,
                                offset = effect.offset,
                                fixedGroupId = DefaultSubscriptionGroupId,
                            )
                        }
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
                        services.sharedApplicationStore.dispatch(
                            SharedApplicationAction.UpdateProxyServers { current ->
                                moveProxyHomeItem(
                                    items = current,
                                    fromId = serverId,
                                    toId = toId,
                                    idOf = { it.id },
                                )
                            },
                        )
                    }
                    is ProxyHomeEffect.OpenStrategyMemberPicker -> {
                        val serverId = effect.serverId.toIntOrNull()
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalArgumentException("Invalid server ID."))
                        val server = stateStore.state.value.proxyServers.firstOrNull { it.id == serverId }
                            ?: return@ProxyHomeEffectHandler Result.failure(IllegalStateException("Proxy server no longer exists."))
                        if (server.server !is StrategyGroup) return@ProxyHomeEffectHandler Result.failure(
                            IllegalArgumentException("This server is not a strategy group."),
                        )
                        homeDialogs = homeDialogs.copy(selectingGroupMemberForServer = server)
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
                        val actionResult = services.sharedApplicationStore.dispatchAndAwait(
                            SharedApplicationAction.UpdateProxyServers { current ->
                                current.map { candidate ->
                                    val strategyGroup = candidate.server as? StrategyGroup
                                    if (candidate.id == serverId && strategyGroup != null) {
                                        candidate.copy(server = strategyGroup.copy(selectedMemberId = memberId))
                                    } else {
                                        candidate
                                    }
                                }
                            },
                        )
                        if (actionResult.outcome is SharedApplicationActionOutcome.Completed &&
                            stateStore.state.value.proxyRunning
                        ) {
                            runProxyServiceOperation {
                                val currentState = stateStore.state.value
                                val currentSelected = currentState.proxyServers.firstOrNull { it.id == currentState.selectedProxyServerId }
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
                            homeDialogs = homeDialogs.copy(pendingToolDeletion = action)
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
            initialPresentation = homePresentation,
            scope = scope,
            effectHandler = ProxyHomeEffectHandler { effect -> androidEffectBridge.value.handle(effect) },
        )
    }
    androidx.compose.runtime.SideEffect { homeStoreReference.value = homeStore }
    LaunchedEffect(homeStore, homeInput) { homeStore.updateInput(homeInput) }
    val homeUiState by homeStore.uiState.collectAsState()
    LaunchedEffect(homeStore, homeUiState.selectedGroupId, homeUiState.searchQuery, homeUiState.isSearchVisible) {
        val nextPresentation = ProxyHomePresentation(
            selectedGroupId = homeUiState.selectedGroupId,
            searchQuery = homeUiState.searchQuery,
            isSearchVisible = homeUiState.isSearchVisible,
            collapsedSubscriptionGroupIds = homeUiState.pages
                .filterNot { page -> page.subscriptionExpanded }
                .mapTo(linkedSetOf()) { page -> page.group.id },
        )
        if (homePresentation != nextPresentation) homePresentation = nextPresentation
    }
    val homeContentPadding = pageContentPaddingWithCutout(
        innerPadding = PaddingValues(0.dp),
        outerPadding = padding,
        isWideScreen = isWideScreen,
    )
    // The nested Scaffold may provide no top padding in edge-to-edge mode.
    val topSystemInset = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .asPaddingValues()
        .calculateTopPadding()
    val topToolbarPadding = maxOf(padding.calculateTopPadding(), topSystemInset).let { inset ->
        if (isWideScreen) inset else inset.coerceAtMost(40.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topToolbarPadding)
            .pageWindowPadding(padding),
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
            topContentPadding = 0.dp,
            containerColor = Color.Transparent,
            floatingNavigationBottomInset = if (isWideScreen) {
                0.dp
            } else {
                maxOf(homeContentPadding.calculateBottomPadding(), floatingNavigationBottomInset)
            },
        )
    }

    homeDialogs.pendingServerDeletion?.let { server ->
        DeleteConfirmationDialog(
            show = true,
            title = stringResource(R.string.deletion_confirmation_delete_proxy_server),
            onDismissRequest = { homeDialogs = homeDialogs.copy(pendingServerDeletion = null) },
            onConfirm = {
                homeDialogs = homeDialogs.copy(pendingServerDeletion = null)
                deleteProxyServer(server)
            },
        )
    }
    homeDialogs.pendingSubscriptionDeletion?.let { group ->
        val isManual = group.builtIn || group.url.isBlank()
        DeleteConfirmationDialog(
            show = true,
            title = if (isManual) {
                stringResource(R.string.deletion_confirmation_delete_group)
            } else {
                stringResource(R.string.deletion_confirmation_delete_subscription_group)
            },
            onDismissRequest = { homeDialogs = homeDialogs.copy(pendingSubscriptionDeletion = null) },
            onConfirm = {
                homeDialogs = homeDialogs.copy(pendingSubscriptionDeletion = null)
                deleteSubscriptionGroup(group)
                if (selectedGroupId == group.id) {
                    updateHomePresentation(
                        ProxyHomeAction.SelectGroup(DefaultSubscriptionGroupId.toString()),
                    )
                }
            },
        )
    }
    homeDialogs.pendingToolDeletion?.let { action ->
        DeleteConfirmationDialog(
            show = true,
            title = stringResource(action.deletionConfirmationTitleResId),
            onDismissRequest = { homeDialogs = homeDialogs.copy(pendingToolDeletion = null) },
            onConfirm = {
                homeDialogs = homeDialogs.copy(pendingToolDeletion = null)
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
    homeDialogs.qrPayload?.let { (title, text) ->
        ProxyServerQrCodeDialog(
            title = title,
            text = text,
            onDismissRequest = { homeDialogs = homeDialogs.copy(qrPayload = null) },
        )
    }
    val isManualGroup = homeDialogs.creatingManualGroup || (editingSubscriptionGroup?.let { it.builtIn || it.url.isBlank() } ?: false)
    SubscriptionGroupEditorDialog(
        show = homeDialogs.creatingSubscriptionGroup || homeDialogs.creatingManualGroup || editingSubscriptionGroup != null,
        group = editingSubscriptionGroup,
        isManualGroup = isManualGroup,
        nextGroupId = maxOf(
            appState.nextSubscriptionGroupId,
            (proxyListState.subscriptionGroups.maxOfOrNull { it.id } ?: 0) + 1,
        ),
        onDismissRequest = {
            homeDialogs = homeDialogs.copy(editingSubscriptionGroupId = null)
            homeDialogs = homeDialogs.copy(creatingSubscriptionGroup = false)
            homeDialogs = homeDialogs.copy(creatingManualGroup = false)
        },
        onDismissFinished = {},
        onSave = { group, isNew ->
            val draft = group.toSubscriptionRecord()
            homeDialogs = homeDialogs.copy(editingSubscriptionGroupId = null)
            homeDialogs = homeDialogs.copy(creatingSubscriptionGroup = false)
            homeDialogs = homeDialogs.copy(creatingManualGroup = false)
            services.appScope.launch {
                try {
                    val result = services.sharedApplicationStore.subscriptionEditorController.save(
                        draft = draft,
                        isNew = isNew,
                        refreshWhenPreviouslyManual = editingSubscriptionGroup?.url.isNullOrBlank(),
                    )
                    val saved = result.savedSubscription
                    if (result.shouldStartRefresh && saved != null) {
                        updateSubscription(saved.id)
                    }
                } catch (failure: kotlinx.coroutines.CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    tipNotifier.showError(failure)
                }
            }
        },
        onDelete = { group ->
            homeDialogs = homeDialogs.copy(editingSubscriptionGroupId = null)
            homeDialogs = homeDialogs.copy(creatingSubscriptionGroup = false)
            homeDialogs = homeDialogs.copy(creatingManualGroup = false)
            deleteSubscriptionGroup(group)
            if (selectedGroupId == group.id) {
                updateHomePresentation(
                    ProxyHomeAction.SelectGroup(DefaultSubscriptionGroupId.toString()),
                )
            }
        },
        onInvalidUrl = {
            scope.launch { tipNotifier.show(invalidSubscriptionUrlMessage) }
        },
    )
    SelectGroupMemberDialog(
        show = homeDialogs.selectingGroupMemberForServer != null,
        groupServer = homeDialogs.selectingGroupMemberForServer,
        onDismissRequest = { homeDialogs = homeDialogs.copy(selectingGroupMemberForServer = null) },
        onSelectMember = { memberId ->
            val targetGroup = homeDialogs.selectingGroupMemberForServer ?: return@SelectGroupMemberDialog
            scope.launch {
                val actionResult = services.sharedApplicationStore.dispatchAndAwait(
                    SharedApplicationAction.UpdateProxyServers { current ->
                        current.map { record ->
                            val strategyGroup = record.server as? StrategyGroup
                            if (record.id == targetGroup.id && strategyGroup != null) {
                                record.copy(server = strategyGroup.copy(selectedMemberId = memberId))
                            } else {
                                record
                            }
                        }
                    },
                )
                if (actionResult.outcome is SharedApplicationActionOutcome.Completed && proxyRunning) {
                    runProxyServiceOperation {
                        val currentState = stateStore.state.value
                        val currentSelected = currentState.proxyServers.firstOrNull { it.id == currentState.selectedProxyServerId }
                        proxyServiceUseCase.restart(state = currentState, selectedServer = currentSelected)
                    }
                }
            }
        },
    )
}
