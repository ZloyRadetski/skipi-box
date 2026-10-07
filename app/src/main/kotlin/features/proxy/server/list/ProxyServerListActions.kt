// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.ui.platform.Clipboard
import app.AppState
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationActionOutcome
import app.skipi.app.model.ProxyServerRecord as SharedProxyServerRecord
import app.skipi.app.proxy.ProxyServerRecord as CollectionProxyServerRecord
import app.skipi.app.proxy.importProxyServerRecords
import app.ProxyServerListState
import app.ProxyServerState
import app.modes.ProxyServerListSortDefault
import app.R
import features.proxy.server.model.StrategyGroup
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName
import app.navigation.Navigator
import app.navigation.Route
import data.AndroidAppStateStore
import engine.proxy.latency.ProxyServerLatencyTestMode
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import features.proxy.server.usecase.ProxyServerImportFileUseCase
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.createProxyServer
import features.proxy.server.usecase.deleteDuplicateServersInGroup
import features.proxy.server.usecase.deleteInvalidServersInGroup
import features.proxy.server.usecase.importProxyServersFromText
import features.proxy.server.usecase.updatableSubscriptionGroups
import features.proxy.server.usecase.applyProxySubscriptionUpdates
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.SubscriptionInstallConfigUseCase
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import features.subscription.runtime.AndroidSubscriptionFetcher
import features.subscription.subscriptionInstallMessage
import features.subscription.usecase.subscriptionUpdateMessage
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import features.subscription.toSubscriptionInstallConfigOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ui.clipboard.getPlainText
import ui.feedback.AndroidToastTipNotifier
import ui.text.formatTemplate

internal val ProxyServerListToolAction.isDeletion: Boolean
    get() = when (this) {
        ProxyServerListToolAction.DeleteDuplicateServers,
        ProxyServerListToolAction.DeleteInvalidServers,
        ProxyServerListToolAction.DeleteAllServers,
        -> true

        else -> false
    }

internal val ProxyServerListToolAction.deletionConfirmationTitleResId: Int
    get() = when (this) {
        ProxyServerListToolAction.DeleteDuplicateServers -> R.string.proxy_server_list_delete_duplicates
        ProxyServerListToolAction.DeleteInvalidServers -> R.string.proxy_server_list_delete_invalid
        ProxyServerListToolAction.DeleteAllServers -> R.string.proxy_server_list_delete_all
        else -> error("Deletion confirmation is only available for deletion actions")
    }

internal fun handleProxyServerListAddAction(
    action: ProxyServerListAddAction,
    groupState: ProxyServerListGroups,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    navigator: Navigator,
    qrScanner: suspend () -> String?,
    proxyServerImportFileUseCase: ProxyServerImportFileUseCase,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
    resultKey: String,
) {
    when (action) {
        ProxyServerListAddAction.ScanQrCode -> {
            scope.launch {
                runCatching { qrScanner() }
                    .onSuccess { scanText ->
                        if (scanText.isNullOrBlank()) return@onSuccess
                        importProxyServersInBackground(
                            text = scanText,
                            source = ProxyServerImportSource.QrCode,
                            groupState = groupState,
                            stateStore = stateStore,
                            subscriptionFetcher = subscriptionFetcher,
                            updateAppState = updateAppState,
                            tipNotifier = tipNotifier,
                            backgroundScope = backgroundScope,
                            messages = messages,
                        )
                    }
                    .onFailure { error -> tipNotifier.showError(error) }
            }
        }

        ProxyServerListAddAction.Clipboard -> {
            scope.launch {
                val text = clipboard.getPlainText().orEmpty()
                importProxyServersInBackground(
                    text = text,
                    source = ProxyServerImportSource.Clipboard,
                    groupState = groupState,
                    stateStore = stateStore,
                    subscriptionFetcher = subscriptionFetcher,
                    updateAppState = updateAppState,
                    tipNotifier = tipNotifier,
                    backgroundScope = backgroundScope,
                    messages = messages,
                )
            }
        }

        ProxyServerListAddAction.File -> {
            scope.launch {
                runCatching { proxyServerImportFileUseCase.readText() }
                    .onSuccess { text ->
                        text?.let {
                            importProxyServersInBackground(
                                text = it,
                                source = ProxyServerImportSource.File,
                                groupState = groupState,
                                stateStore = stateStore,
                                subscriptionFetcher = subscriptionFetcher,
                                updateAppState = updateAppState,
                                tipNotifier = tipNotifier,
                                backgroundScope = backgroundScope,
                                messages = messages,
                            )
                        }
                    }
                    .onFailure { error -> tipNotifier.showError(error) }
            }
        }

        else -> {
            navigator.navigateForResult(
                route = Route.ProxyServerEditor(
                    ps = createProxyServer(action),
                    serverId = null,
                    groupId = if (action == ProxyServerListAddAction.StrategyGroup) {
                        AutoBalancerGroupId
                    } else {
                        // A server created from the add menu is always a manual
                        // server. It must not become part of the subscription the
                        // user happened to be viewing when they pressed Add.
                        DefaultSubscriptionGroupId
                    },
                    returnGroupId = if (action == ProxyServerListAddAction.StrategyGroup) {
                        AutoBalancerGroupId
                    } else {
                        DefaultSubscriptionGroupId
                    },
                    resultKey = resultKey,
                ),
                requestKey = resultKey,
            )
        }
    }
}

private fun importProxyServersInBackground(
    text: String,
    source: ProxyServerImportSource,
    groupState: ProxyServerListGroups,
    stateStore: AndroidAppStateStore,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    updateAppState: ((AppState) -> AppState) -> Unit,
    tipNotifier: AndroidToastTipNotifier,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
) {
    backgroundScope.launch {
        runCatching {
            if (
                installSubscriptionFromText(
                    text = text,
                    stateStore = stateStore,
                    subscriptionFetcher = subscriptionFetcher,
                    tipNotifier = tipNotifier,
                    messages = messages,
                )
            ) {
                return@runCatching
            }
            val appState = stateStore.state.value
            importProxyServers(
                text = text,
                source = source,
                groupState = groupState,
                stateStore = stateStore,
                subscriptionFetcher = subscriptionFetcher,
                sendDeviceHeaders = appState.enableSubscriptionDeviceHeaders,
                fetchTimeoutSeconds = appState.subscriptionFetchTimeoutSeconds,
                tipNotifier = tipNotifier,
                messages = messages,
            )
        }.onFailure { error -> tipNotifier.showError(error) }
    }
}

private suspend fun installSubscriptionFromText(
    text: String,
    stateStore: AndroidAppStateStore,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    tipNotifier: AndroidToastTipNotifier,
    messages: ProxyServerListMessages,
): Boolean {
    val config = text.toSubscriptionInstallConfigOrNull() ?: return false
    runCatching {
        SubscriptionInstallConfigUseCase(
            stateStore = stateStore,
            subscriptionFetcher = subscriptionFetcher,
        ).install(config)
    }.onSuccess { result ->
        tipNotifier.show(
            subscriptionInstallMessage(
                result = result,
                existingUrlTemplate = messages.subscriptionInstallExistingUrlTemplate,
                successTemplate = messages.subscriptionUpdateResultTemplate,
                failedTemplate = messages.subscriptionUpdateResultWithFailedTemplate,
            ),
        )
    }.onFailure { error ->
        tipNotifier.showError(error)
    }
    return true
}

private suspend fun importProxyServers(
    text: String,
    source: ProxyServerImportSource,
    groupState: ProxyServerListGroups,
    stateStore: AndroidAppStateStore,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    sendDeviceHeaders: Boolean,
    fetchTimeoutSeconds: Int,
    tipNotifier: AndroidToastTipNotifier,
    messages: ProxyServerListMessages,
) {
    // Clipboard, QR and file imports are all explicit user additions. A
    // subscription is installed through its own flow above; anything reaching
    // this point belongs to the permanent manual-server group.
    val targetGroupId = DefaultSubscriptionGroupId
    val importResult = importProxyServersFromText(
        text = text,
        source = source,
        providerUrlFetcher = { providerUrl ->
            subscriptionFetcher.fetch(
                url = providerUrl,
                userAgent = "",
                options = AndroidSubscriptionFetchOptions(
                    sendDeviceHeaders = sendDeviceHeaders,
                    timeoutSeconds = fetchTimeoutSeconds,
                ),
            )
        },
    )
    if (importResult.servers.isNotEmpty()) {
        stateStore.proxyServerRepository.updateCatalog { catalog ->
            importProxyServerRecords(
                servers = catalog.servers.map { record ->
                    CollectionProxyServerRecord(
                        id = record.id,
                        groupId = record.sourceSubscriptionId ?: DefaultSubscriptionGroupId,
                        server = record.server,
                    )
                },
                imported = importResult.servers,
                groupId = targetGroupId,
                nextServerId = catalog.nextServerId,
                selectedServerId = catalog.selectedServerId,
            ).let { collection ->
                app.skipi.app.model.ProxyServerCatalog(
                    servers = collection.servers.map { record ->
                        SharedProxyServerRecord(
                            id = record.id,
                            server = record.server,
                            sourceSubscriptionId = record.groupId.takeIf { it != DefaultSubscriptionGroupId },
                        )
                    },
                    nextServerId = collection.nextServerId,
                    selectedServerId = collection.selectedServerId,
                )
            }
        }
    }
    tipNotifier.show(
        messages.importResultTemplate.formatTemplate(
            "serverCount" to importResult.servers.size,
        ),
    )
}

internal fun handleProxyServerListToolAction(
    action: ProxyServerListToolAction,
    groupState: ProxyServerListGroups,
    selectedServer: ProxyServerState?,
    proxyListState: ProxyServerListState,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    proxyServiceUseCase: ProxyServiceUseCase,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
    onTestProxyServerLatency: (List<ProxyServerState>, ProxyServerLatencyTestMode, String, Boolean) -> Unit,
) {
    when (action) {
        ProxyServerListToolAction.RestartService -> {
            restartSelectedProxyService(
                selectedServer = selectedServer,
                stateStore = stateStore,
                updateAppState = updateAppState,
                proxyServiceUseCase = proxyServiceUseCase,
                tipNotifier = tipNotifier,
                messages = messages,
                serviceOperationInProgress = serviceOperationInProgress,
                runProxyServiceOperation = runProxyServiceOperation,
            )
        }

        ProxyServerListToolAction.TestLatency -> {
            onTestProxyServerLatency(
                groupState.currentFilteredServers,
                ProxyServerLatencyTestMode.TcpConnect,
                messages.latencyDoneTemplate,
                false,
            )
        }

        ProxyServerListToolAction.TestRealConnection -> {
            onTestProxyServerLatency(
                groupState.currentFilteredServers,
                ProxyServerLatencyTestMode.RealConnection,
                messages.realConnectionDoneTemplate,
                false,
            )
        }

        ProxyServerListToolAction.SetSortDefault -> {
            updateAppState { state -> state.copy(proxyServerListSort = ProxyServerListSortDefault) }
        }

        ProxyServerListToolAction.SetSortName -> {
            updateAppState { state -> state.copy(proxyServerListSort = ProxyServerListSortName) }
        }

        ProxyServerListToolAction.SetSortLatency -> {
            updateAppState { state -> state.copy(proxyServerListSort = ProxyServerListSortLatency) }
        }

        ProxyServerListToolAction.UpdateSubscriptions -> {
            updateSubscriptionGroups(
                proxyListState = proxyListState,
                stateStore = stateStore,
                updateAppState = updateAppState,
                subscriptionFetcher = subscriptionFetcher,
                tipNotifier = tipNotifier,
                backgroundScope = backgroundScope,
                messages = messages,
            )
        }

        ProxyServerListToolAction.DeleteDuplicateServers -> {
            deleteDuplicateServers(
                servers = groupState.currentGroupServers,
                stateStore = stateStore,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
            )
        }

        ProxyServerListToolAction.DeleteInvalidServers -> {
            deleteInvalidServers(
                servers = groupState.currentGroupServers,
                stateStore = stateStore,
                updateAppState = updateAppState,
                proxyServiceUseCase = proxyServiceUseCase,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
                serviceOperationInProgress = serviceOperationInProgress,
                runProxyServiceOperation = runProxyServiceOperation,
            )
        }

        ProxyServerListToolAction.DeleteAllServers -> {
            deleteAllServers(
                servers = if (groupState.isAllGroupsSelected) {
                    proxyListState.proxyServers
                } else {
                    groupState.currentGroupServers
                },
                stateStore = stateStore,
                updateAppState = updateAppState,
                proxyServiceUseCase = proxyServiceUseCase,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
                serviceOperationInProgress = serviceOperationInProgress,
                runProxyServiceOperation = runProxyServiceOperation,
            )
        }
    }
}

private fun restartSelectedProxyService(
    selectedServer: ProxyServerState?,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    if (serviceOperationInProgress) return
    runProxyServiceOperation {
        when (
            val result = proxyServiceUseCase.restart(
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
                tipNotifier.show(messages.serviceRestarted)
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
}

private fun updateSubscriptionGroups(
    proxyListState: ProxyServerListState,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    tipNotifier: AndroidToastTipNotifier,
    backgroundScope: CoroutineScope,
    messages: ProxyServerListMessages,
) {
    val subscriptionGroups = proxyListState.subscriptionGroups.updatableSubscriptionGroups()
    backgroundScope.launch {
        if (subscriptionGroups.isEmpty()) {
            tipNotifier.show(messages.noSubscriptionUpdates)
            return@launch
        }
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
            ),
        )
    }
}

private fun deleteInvalidServers(
    servers: List<ProxyServerState>,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    val currentGroupServerIds = servers.map { server -> server.id }.toSet()
    val previewResult = stateStore.state.value.proxyServers.deleteInvalidServersInGroup(currentGroupServerIds)
    deleteServersByIds(
        serverIds = previewResult.removedServerIds,
        stateStore = stateStore,
        updateAppState = updateAppState,
        proxyServiceUseCase = proxyServiceUseCase,
        tipNotifier = tipNotifier,
        scope = scope,
        deletedTemplate = messages.invalidServersDeletedTemplate,
        emptyMessage = messages.noInvalidServers,
        serviceStoppedMessage = messages.serviceStopped,
        serviceOperationInProgress = serviceOperationInProgress,
        runProxyServiceOperation = runProxyServiceOperation,
    )
}

private fun deleteAllServers(
    servers: List<ProxyServerState>,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    deleteServersByIds(
        serverIds = servers.map { server -> server.id }.toSet(),
        stateStore = stateStore,
        updateAppState = updateAppState,
        proxyServiceUseCase = proxyServiceUseCase,
        tipNotifier = tipNotifier,
        scope = scope,
        deletedTemplate = messages.allServersDeletedTemplate,
        emptyMessage = messages.noServersToDelete,
        serviceStoppedMessage = messages.serviceStopped,
        serviceOperationInProgress = serviceOperationInProgress,
        runProxyServiceOperation = runProxyServiceOperation,
    )
}

private fun deleteServersByIds(
    serverIds: Set<Int>,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    proxyServiceUseCase: ProxyServiceUseCase,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    deletedTemplate: String,
    emptyMessage: String,
    serviceStoppedMessage: String,
    serviceOperationInProgress: Boolean,
    runProxyServiceOperation: (suspend () -> Unit) -> Unit,
) {
    val stateSnapshot = stateStore.state.value
    val existingServerIds = stateSnapshot.proxyServers
        .asSequence()
        .map { server -> server.id }
        .filter { serverId -> serverId in serverIds }
        .toSet()
    if (existingServerIds.isEmpty()) {
        scope.launch { tipNotifier.show(emptyMessage) }
        return
    }

    suspend fun notifyDeleted() {
        tipNotifier.show(
            if (existingServerIds.isNotEmpty()) {
                deletedTemplate.formatTemplate("count" to existingServerIds.size)
            } else {
                emptyMessage
            },
        )
    }

    suspend fun notifyDeleteError(error: Throwable) {
        tipNotifier.showError(error)
    }

    fun applyDeleteAndNotify() {
        scope.launch {
            val result = stateStore.sharedApplicationStore.dispatchAndAwait(
                SharedApplicationAction.RemoveProxyServers(serverIds = existingServerIds),
            )
            when (val outcome = result.outcome) {
                SharedApplicationActionOutcome.Completed -> {
                    if (stateSnapshot.selectedProxyServerId in existingServerIds) {
                        updateAppState { state -> state.copy(proxyRunning = false) }
                    }
                    notifyDeleted()
                }

                is SharedApplicationActionOutcome.Rejected -> {
                    notifyDeleteError(IllegalArgumentException(outcome.reason))
                }

                is SharedApplicationActionOutcome.Failed -> {
                    notifyDeleteError(IllegalStateException(outcome.reason))
                }
            }
        }
    }

    val selectedServerWillBeDeleted = stateSnapshot.selectedProxyServerId in existingServerIds
    if (!stateSnapshot.proxyRunning || !selectedServerWillBeDeleted) {
        applyDeleteAndNotify()
        return
    }
    if (serviceOperationInProgress) return

    runProxyServiceOperation {
        deleteProxyServersAfterStoppingService(
            stopService = { proxyServiceUseCase.stop(stateStore.state.value.runMode) },
            dispatchDelete = {
                stateStore.sharedApplicationStore.dispatchAndAwait(
                    SharedApplicationAction.RemoveProxyServers(serverIds = existingServerIds),
                ).outcome
            },
            applyStopState = { stopResult ->
                updateAppState { state ->
                    state.copy(
                        proxyRunning = stopResult.proxyRunning,
                        localProxyPort = stopResult.appState?.localProxyPort ?: state.localProxyPort,
                    )
                }
            },
            onDeleted = {
                updateAppState { state -> state.copy(proxyRunning = false) }
                notifyDeleted()
            },
            onDeleteError = { error -> notifyDeleteError(error) },
            onStopFailed = { error ->
                updateAppState { state -> state.copy(proxyRunning = false) }
                tipNotifier.showError(error, serviceStoppedMessage)
            },
        )
    }
}

private fun deleteDuplicateServers(
    servers: List<ProxyServerState>,
    stateStore: AndroidAppStateStore,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
) {
    val currentGroupServerIds = servers.map { server -> server.id }.toSet()
    val preview = stateStore.currentState.proxyServers.deleteDuplicateServersInGroup(
        currentGroupServerIds = currentGroupServerIds,
        selectedProxyServerId = stateStore.currentState.selectedProxyServerId,
    )
    val retainedIds = preview.servers.mapTo(hashSetOf()) { it.id }
    val removedIds = stateStore.currentState.proxyServers.map { it.id }.filterNot { it in retainedIds }.toSet()
    scope.launch {
        if (removedIds.isNotEmpty()) {
            val result = stateStore.sharedApplicationStore.dispatchAndAwait(
                SharedApplicationAction.RemoveProxyServers(serverIds = removedIds),
            )
            when (val outcome = result.outcome) {
                SharedApplicationActionOutcome.Completed -> Unit
                is SharedApplicationActionOutcome.Rejected -> {
                    tipNotifier.showError(IllegalArgumentException(outcome.reason))
                    return@launch
                }

                is SharedApplicationActionOutcome.Failed -> {
                    tipNotifier.showError(IllegalStateException(outcome.reason))
                    return@launch
                }
            }
        }
        tipNotifier.show(
            if (removedIds.isNotEmpty()) {
                messages.duplicatesDeletedTemplate.formatTemplate("count" to removedIds.size)
            } else {
                messages.noDuplicates
            },
        )
    }
}
