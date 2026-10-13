// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeDisplayOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeEffectHandler
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomePresentation
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.proxyHomeSortModeFromAndroidValue
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyServerSummary
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.subscription.SubscriptionEditorSaveResult
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.createProxyServerEditorDraft
import app.skipi.ui.home.ProxyHomeScreen
import app.skipi.ui.home.rememberSaveableProxyHomePresentation
import app.skipi.ui.components.DeleteConfirmationDialog
import app.skipi.ui.home.dialogs.SkipiAddSourceDialog
import app.skipi.ui.home.dialogs.SkipiAddSourceMode
import app.skipi.ui.home.dialogs.SkipiImportDialog
import app.skipi.ui.subscription.SubscriptionGroupEditorDialog
import app.skipi.ui.subscription.SubscriptionGroupUiState
import app.skipi.ui.subscription.toSubscriptionGroupUiState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_unknown_group
import app.skipi.ui.resources.proxy_editor_strategy_group_all_groups
import app.skipi.ui.resources.proxy_editor_strategy_group_least_load
import app.skipi.ui.resources.proxy_editor_strategy_group_least_ping
import app.skipi.ui.resources.proxy_editor_strategy_group_random
import app.skipi.ui.resources.proxy_editor_strategy_group_round_robin
import app.skipi.ui.resources.proxy_editor_strategy_group_select
import app.skipi.ui.resources.proxy_server_list_chain_proxy_summary
import app.skipi.ui.resources.proxy_server_list_strategy_group_summary
import app.skipi.ui.resources.proxy_server_list_strategy_group_summary_with_filter
import app.skipi.ui.resources.proxy_server_list_delete_all
import app.skipi.ui.resources.proxy_server_list_delete_duplicates
import app.skipi.ui.resources.proxy_server_list_delete_invalid
import app.skipi.ui.resources.subscription_delete
import app.skipi.ui.resources.subscription_default_group
import app.skipi.ui.resources.subscription_invalid_url
import app.skipi.ui.resources.proxy_group_select_active_server
import app.skipi.ui.resources.proxy_editor_strategy_group_no_servers
import app.skipi.ui.server.editor.GroupMemberChoice
import app.skipi.ui.server.editor.SkipiSelectGroupMemberDialog
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.resolveStrategyGroupMembers
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.extractLeadingCountryFlagOrNull
import features.proxy.server.model.encodePersistedProxyServer
import features.proxy.server.model.getUrlOrNull
import features.proxy.server.model.stripLeadingCountryFlag
import features.subscription.DefaultSubscriptionExpiryReminders
import features.proxy.server.presentation.ProxyServerPresentationFormatter
import features.proxy.server.presentation.ProxyServerPresentationLabels
import features.proxy.server.presentation.ProxyServerPresentationNode
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.launch
import platform.TunnelPhase
import platform.TunnelSnapshot

/**
 * Desktop adaptation of ProxyServerListPage using the shared adaptive ProxyHomeScreenContent.
 */
@Composable
internal fun DesktopProxyHome(
    serverLibrary: DesktopServerLibrary,
    subscriptionLibrary: DesktopSubscriptionLibrary,
    subscriptionCatalog: SubscriptionCatalog,
    subscriptionUpdate: DesktopSubscriptionUpdate?,
    serverLink: String,
    subscriptionUrl: String,
    updatingSubscription: Boolean,
    refreshingSubscriptionIds: Set<Int> = emptySet(),
    running: Boolean,
    connecting: Boolean = false,
    canToggleTunnel: Boolean,
    tunnelMessage: String,
    serverMessage: String,
    subscriptionMessage: String,
    confirmDeletion: Boolean,
    activeProfileName: String?,
    activeTrafficConfigId: Int?,
    trafficConfigContentById: Map<Int, String> = emptyMap(),
    exportFullJson: suspend (ProxyServerEditResult) -> String,
    onSelectStrategyMember: (Int, Int) -> Unit,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    testingServerIds: Set<Int>,
    pingingSubscriptionIds: Set<Int>,
    onServerLinkChange: (String) -> Unit,
    onSubscriptionUrlChange: (String) -> Unit,
    onToggleTunnel: () -> Unit,
    onSelectServer: (Int) -> Unit,
    onDeleteServer: (Int) -> Unit,
    onSaveServer: (ProxyServerEditResult, (ProxyServerEditApplyOutcome) -> Unit) -> Unit,
    onMeasureServers: (List<Pair<Int, ProxyServer<*>>>) -> Unit,
    onPingSubscriptionServers: (Int, List<Pair<Int, ProxyServer<*>>>) -> Unit = { _, targets -> onMeasureServers(targets) },
    onUpdateSubscriptionById: (Int) -> Unit,
    onRefreshAllSubscriptions: (() -> Unit)? = null,
    onDeleteDuplicateServers: ((Set<Int>) -> Unit)? = null,
    onDeleteInvalidServers: ((Set<Int>) -> Unit)? = null,
    onRestartService: (() -> Unit)? = null,
    onDeleteAllServers: ((Set<Int>) -> Unit)? = null,
    onCancelLatencyTests: (() -> Unit)? = null,
    scheduledSubscriptionId: Int?,
    onScheduledSubscriptionConsumed: () -> Unit,
    onPrepareSubscription: suspend (DesktopSubscriptionInstallUri) -> Result<Int>,
    onImport: suspend (DesktopProxyImportInput) -> Result<String>,
    onSaveSubscriptionGroup: suspend (SubscriptionGroupUiState, Boolean, Boolean) -> SubscriptionEditorSaveResult,
    onSetSubscriptionEnabled: suspend (Int, Boolean) -> Unit,
    onDeleteSubscription: suspend (Int) -> Unit,
    contentPadding: PaddingValues,
    desktopSettings: DesktopAppSettings = DesktopAppSettings(),
    onMoveGroup: suspend (Int, Int) -> Unit,
    onMoveServer: (fromServerId: Int, toServerId: Int) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    onHomeSortModeChange: (ProxyHomeSortMode) -> Unit = {},
) {
    var addDialogVisible by remember { mutableStateOf(false) }
    var importDialogVisible by remember { mutableStateOf(false) }
    var addMode by remember { mutableStateOf(SkipiAddSourceMode.Server) }
    var localMessage by remember { mutableStateOf("") }
    var pendingServerDeletion by remember { mutableStateOf<Int?>(null) }
    var pendingSubscriptionDeletion by remember { mutableStateOf<Int?>(null) }
    var pendingToolDeletion by remember { mutableStateOf<ProxyHomeServerTool?>(null) }
    var editingServerId by remember { mutableStateOf<Int?>(null) }
    var subscriptionEditorVisible by remember { mutableStateOf(false) }
    var editingSubscriptionGroup by remember { mutableStateOf<SubscriptionGroupUiState?>(null) }
    var editingManualGroup by remember { mutableStateOf(false) }
    var editingServerModel by remember { mutableStateOf<ProxyServerEditResult?>(null) }
    var selectingMembersForServerId by remember { mutableStateOf<Int?>(null) }

    val decodedServers = remember(serverLibrary) {
        serverLibrary.servers.map { stored -> stored to stored.decode().getOrNull() }
    }
    val groupCatalog = remember(serverLibrary, subscriptionLibrary, activeTrafficConfigId, desktopSettings.enableAllProxyGroup) {
        DesktopProxyGroups.create(
            serverLibrary,
            subscriptionLibrary,
            DesktopProxyGroupOptions(
                activeTrafficConfigId = activeTrafficConfigId,
                enableAllProxyGroup = desktopSettings.enableAllProxyGroup,
            ),
        )
    }
    LaunchedEffect(scheduledSubscriptionId, updatingSubscription, subscriptionCatalog) {
        val subscriptionId = scheduledSubscriptionId ?: return@LaunchedEffect
        val subscription = subscriptionCatalog.subscriptions.firstOrNull { it.id == subscriptionId }
        if (subscription != null && subscription.url.isNotBlank() && !updatingSubscription) {
            onUpdateSubscriptionById(subscriptionId)
        }
        onScheduledSubscriptionConsumed()
    }

    val combinedStatusMessage = listOf(tunnelMessage, subscriptionMessage, serverMessage, localMessage)
        .firstOrNull { it.isNotBlank() }
    val isStatusError = combinedStatusMessage?.let { msg ->
        msg.contains("не удалось", ignoreCase = true) ||
            msg.contains("ошибка", ignoreCase = true) ||
            msg.contains("failed", ignoreCase = true) ||
            msg.contains("rejected", ignoreCase = true)
    } ?: false

    val selectedServer = decodedServers.firstOrNull { (stored, _) -> stored.id == serverLibrary.selectedServerId }?.second
    val selectedTitle = selectedServer?.getInfo()?.remarks.orEmpty().ifBlank { "Выберите сервер" }
    var sortMode by remember(desktopSettings.proxyServerListSort) {
        mutableStateOf(proxyHomeSortModeFromAndroidValue(desktopSettings.proxyServerListSort))
    }

    val unknownGroupName = stringResource(Res.string.common_unknown_group)
    val defaultGroupName = stringResource(Res.string.subscription_default_group)
    val invalidSubscriptionUrlMessage = stringResource(Res.string.subscription_invalid_url)
    val presentationLabels = ProxyServerPresentationLabels(
        unknownGroupName = unknownGroupName,
        allGroupsName = stringResource(Res.string.proxy_editor_strategy_group_all_groups),
        selectName = stringResource(Res.string.proxy_editor_strategy_group_select),
        leastPingName = stringResource(Res.string.proxy_editor_strategy_group_least_ping),
        leastLoadName = stringResource(Res.string.proxy_editor_strategy_group_least_load),
        randomName = stringResource(Res.string.proxy_editor_strategy_group_random),
        roundRobinName = stringResource(Res.string.proxy_editor_strategy_group_round_robin),
        strategyGroupSummaryTemplate = stringResource(Res.string.proxy_server_list_strategy_group_summary),
        strategyGroupSummaryWithFilterTemplate = stringResource(Res.string.proxy_server_list_strategy_group_summary_with_filter),
        chainProxySummaryTemplate = stringResource(Res.string.proxy_server_list_chain_proxy_summary),
    )
    val presentationGroupNames = remember(subscriptionLibrary.subscriptions, unknownGroupName) {
        subscriptionLibrary.subscriptions.associate { subscription ->
            subscription.id to subscription.name.ifBlank { unknownGroupName }
        }
    }
    val presentationFormatter = remember(presentationGroupNames, presentationLabels) {
        ProxyServerPresentationFormatter(presentationGroupNames, presentationLabels)
    }
    val presentationNodes = remember(decodedServers) {
        decodedServers.mapNotNull { (stored, server) ->
            server?.let { current ->
                ProxyServerPresentationNode(
                    id = stored.id,
                    groupId = stored.subscriptionId,
                    server = current,
                )
            }
        }
    }

    val homeProjection = remember(
        groupCatalog,
        decodedServers,
        subscriptionLibrary,
        serverLibrary.selectedServerId,
        latencyByServerId,
        testingServerIds,
        presentationFormatter,
        presentationNodes,
        refreshingSubscriptionIds,
        pingingSubscriptionIds,
    ) {
        projectDesktopProxyHomeProjection(
            groupCatalog = groupCatalog,
            decodedServers = decodedServers,
            subscriptionLibrary = subscriptionLibrary,
            selectedServerId = serverLibrary.selectedServerId,
            latencyByServerId = latencyByServerId,
            testingServerIds = testingServerIds,
            presentationFormatter = presentationFormatter,
            presentationNodes = presentationNodes,
            refreshingSubscriptionIds = refreshingSubscriptionIds,
            pingingSubscriptionIds = pingingSubscriptionIds,
        )
    }
    val proxyGroups = homeProjection.groups
    val allProxyServers = homeProjection.servers

    val desktopCapabilities = desktopProxyHomeActionCapabilities(
        subscriptions = subscriptionLibrary.subscriptions,
        onRefreshAllSubscriptions = onRefreshAllSubscriptions,
        onDeleteDuplicateServers = onDeleteDuplicateServers,
        onDeleteInvalidServers = onDeleteInvalidServers,
        onRestartService = onRestartService,
        onDeleteAllServers = onDeleteAllServers,
    )
    val availableActions = buildSet {
        addAll(desktopCapabilities.actions)
        addAll(
            setOf(
                ProxyHomeActionId.SelectServer,
                ProxyHomeActionId.AddServer,
                ProxyHomeActionId.AddSubscription,
                ProxyHomeActionId.ImportServers,
                ProxyHomeActionId.SetSort,
            ),
        )
        if (canToggleTunnel) add(ProxyHomeActionId.ToggleTunnel)
        if (allProxyServers.isNotEmpty()) {
            add(ProxyHomeActionId.EditServer)
            add(ProxyHomeActionId.DeleteServer)
            add(ProxyHomeActionId.CopyServer)
        }
        if (testingServerIds.isNotEmpty() && onCancelLatencyTests != null) {
            add(ProxyHomeActionId.CancelLatencyTests)
        }
        if (decodedServers.any { (_, server) -> server is StrategyGroup }) {
            add(ProxyHomeActionId.OpenStrategyMemberPicker)
            add(ProxyHomeActionId.SelectStrategyMember)
        }
        if (allProxyServers.any(ProxyServerSummary::canTest)) {
            add(ProxyHomeActionId.TestServer)
            add(ProxyHomeActionId.TestVisibleServers)
            add(ProxyHomeActionId.TestGroup)
        }
        val pingableSubscription = subscriptionLibrary.subscriptions.any { subscription ->
            subscription.url.isNotBlank() &&
            groupCatalog.group(DesktopProxyGroupIds.subscription(subscription.id))
                ?.serverIds
                ?.any { serverId ->
                    allProxyServers.firstOrNull { it.id == serverId.toString() }?.canTest == true
                } == true
        }
        if (pingableSubscription && pingingSubscriptionIds.isEmpty()) add(ProxyHomeActionId.PingSubscription)
        val hasRealSubscriptions = subscriptionLibrary.subscriptions.any { it.url.isNotBlank() }
        if (hasRealSubscriptions && !updatingSubscription) {
            add(ProxyHomeActionId.RefreshSubscription)
        }
        if (subscriptionLibrary.subscriptions.isNotEmpty()) {
            add(ProxyHomeActionId.ToggleSubscriptionEnabled)
        }
        if (subscriptionLibrary.subscriptions.isNotEmpty()) {
            add(ProxyHomeActionId.EditSubscription)
        }
        add(ProxyHomeActionId.EditGroup)
        if (subscriptionLibrary.subscriptions.isNotEmpty()) {
            add(ProxyHomeActionId.DeleteGroup)
        }
        if (subscriptionLibrary.subscriptions.size > 1) {
            add(ProxyHomeActionId.MoveGroup)
        }
        if (sortMode == ProxyHomeSortMode.Default && allProxyServers.size > 1) {
            add(ProxyHomeActionId.MoveServer)
        }
        if (Desktop.isDesktopSupported()) add(ProxyHomeActionId.OpenExternalLink)
    }

    val homeInput = ProxyHomeInput(
        tunnelSnapshot = desktopProxyHomeTunnelSnapshot(running = running, connecting = connecting),
        selectedServerId = serverLibrary.selectedServerId?.toString(),
        selectedServerTitle = selectedTitle,
        activeProfileName = activeProfileName,
        canToggleTunnel = canToggleTunnel,
        tunnelBusy = connecting,
        groups = proxyGroups,
        servers = allProxyServers,
        isTestingLatency = testingServerIds.isNotEmpty(),
        statusMessage = combinedStatusMessage,
        isStatusError = isStatusError,
        sortMode = sortMode,
        searchEnabled = desktopSettings.showServerSearch,
        subscriptionSwipeEnabled = desktopSettings.enableSubscriptionSwipe,
        displayOptions = ProxyHomeDisplayOptions(
            connectionMode = if (desktopSettings.compactHome) ProxyHomeConnectionMode.Compact else ProxyHomeConnectionMode.Classic,
            pinConnectionPanel = desktopSettings.pinConnectionPanelOnHome,
            classicFloatingPowerButton = desktopSettings.classicShowFloatingPowerButton,
            requestedColumns = desktopSettings.proxyServerListColumns,
            showAllGroup = desktopSettings.enableAllProxyGroup,
            showTunnelMemory = desktopSettings.showTunnelMemory,
        ),
        availableActions = availableActions,
        availableImportSources = setOf(
            ProxyHomeImportSource.ManualInput,
            ProxyHomeImportSource.Clipboard,
            ProxyHomeImportSource.File,
        ),
        availableCopyFormats = setOf(ProxyHomeCopyFormat.Url, ProxyHomeCopyFormat.FullJson),
        availableServerKinds = ProxyHomeServerKind.entries.toSet(),
        availableServerTools = desktopCapabilities.serverTools,
    )

    val scope = rememberCoroutineScope()
    val initialSelectedGroupId = remember { initialDesktopProxyHomeGroupId(serverLibrary) }
    var homePresentation by rememberSaveableProxyHomePresentation(
        ProxyHomePresentation(selectedGroupId = initialSelectedGroupId),
    )
    val effectContextState = remember { mutableStateOf<DesktopProxyHomeEffectContext?>(null) }
    val homeStore = remember {
        ProxyHomeStore(
            initialInput = homeInput,
            initialPresentation = homePresentation,
            scope = scope,
            effectHandler = ProxyHomeEffectHandler { effect ->
                effectContextState.value?.handle(effect)
                    ?: Result.failure(IllegalStateException("Главный экран Desktop ещё не готов к этому действию."))
            },
        )
    }
    LaunchedEffect(homeInput) {
        homeStore.updateInput(homeInput)
    }
    val homeUiState by homeStore.uiState.collectAsState()
    var pendingSavedHomeGroupId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(homeUiState.groups, pendingSavedHomeGroupId) {
        val resolution = resolvePendingDesktopProxyHomeGroupSelection(
            pendingGroupId = pendingSavedHomeGroupId,
            availableGroupIds = homeUiState.groups.mapTo(linkedSetOf()) { group -> group.id },
        )
        resolution.groupToSelect?.let { groupId ->
            homeStore.dispatch(ProxyHomeAction.SelectGroup(groupId))
        }
        pendingSavedHomeGroupId = resolution.pendingGroupId
    }
    LaunchedEffect(homeStore, homeUiState.selectedGroupId, homeUiState.searchQuery, homeUiState.isSearchVisible, homeUiState.pages) {
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
    val importFromClipboard: () -> Unit = {
        scope.launch {
            readDesktopClipboardText().onSuccess { text ->
                val install = text.trim().toDesktopSubscriptionInstallUriOrNull()
                if (install != null) {
                    onPrepareSubscription(install).fold(
                        onSuccess = { subscriptionId ->
                            onUpdateSubscriptionById(subscriptionId)
                            localMessage = "Подписка «${install.name}» добавлена."
                        },
                        onFailure = { error -> localMessage = error.message ?: "Не удалось сохранить подписку." },
                    )
                } else {
                    onImport(DesktopProxyImportInput.Clipboard(text)).fold(
                        onSuccess = { summary -> localMessage = summary },
                        onFailure = { error -> localMessage = error.message ?: "Не удалось импортировать." },
                    )
                }
            }.onFailure { error -> localMessage = error.message ?: "Не удалось прочитать буфер обмена." }
        }
    }
    val importFromFile: () -> Unit = {
        scope.launch {
            chooseDesktopImportFile().onSuccess { file ->
                if (file != null) {
                    onImport(DesktopProxyImportInput.File(file.name, file.content)).fold(
                        onSuccess = { summary -> localMessage = summary },
                        onFailure = { error -> localMessage = error.message ?: "Не удалось импортировать." },
                    )
                }
            }.onFailure { error -> localMessage = error.message ?: "Не удалось прочитать файл." }
        }
    }

    val effectContext = DesktopProxyHomeEffectContext(
        decodedServers = decodedServers,
        groupCatalog = groupCatalog,
        subscriptions = subscriptionLibrary.subscriptions,
        currentGroupServerIds = groupCatalog.select(homeUiState.selectedGroupId).serverIds.toSet(),
        orderedVisibleServerIds = homeUiState.servers.mapNotNull { server -> server.id.toIntOrNull() },
        pingingSubscriptionIds = pingingSubscriptionIds,
        updatingSubscription = updatingSubscription,
        confirmDeletion = confirmDeletion,
        onToggleTunnel = onToggleTunnel,
        onSelectServer = onSelectServer,
        onDeleteServer = onDeleteServer,
        onMeasureServers = onMeasureServers,
        onPingSubscription = onPingSubscriptionServers,
        onUpdateSubscriptionById = onUpdateSubscriptionById,
        onRefreshAllSubscriptions = onRefreshAllSubscriptions,
        onDeleteDuplicateServers = onDeleteDuplicateServers,
        onDeleteInvalidServers = onDeleteInvalidServers,
        onRestartService = onRestartService,
        onDeleteAllServers = onDeleteAllServers,
        onCancelLatencyTests = onCancelLatencyTests,
        onConfirmToolDeletion = { tool -> pendingToolDeletion = tool },
        onSetSubscriptionEnabled = { id, enabled ->
            scope.launch {
                runCatching { onSetSubscriptionEnabled(id, enabled) }
                    .onFailure { error -> localMessage = error.message ?: "Не удалось изменить группу." }
            }
            Result.success(Unit)
        },
        onOpenAdd = { mode ->
            editingServerId = null
            if (mode == SkipiAddSourceMode.Server) onServerLinkChange("") else onSubscriptionUrlChange("")
            addMode = mode
            addDialogVisible = true
        },
        onCreateServerDraft = { kind ->
            editingServerModel = createProxyServerEditorDraft(
                kind = kind,
                defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
            )
        },
        onOpenImportDialog = { importDialogVisible = true },
        onImportFromClipboard = importFromClipboard,
        onImportFromFile = importFromFile,
        onEditServer = { id ->
            decodedServers.firstOrNull { it.first.id == id }?.let { (stored, server) ->
                if (server != null) {
                    editingServerModel = ProxyServerEditResult(
                        serverId = stored.id,
                        server = server,
                        groupId = stored.subscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId,
                        returnGroupId = desktopProxyServerGroupIdForHomeGroup(homeUiState.selectedGroupId),
                    )
                }
            }
        },
        onOpenStrategyMemberPicker = { id -> selectingMembersForServerId = id },
        onSelectStrategyMember = onSelectStrategyMember,
        onDeleteServerConfirm = { id -> pendingServerDeletion = id },
        onEditSubscription = { subscription ->
            val record = subscriptionCatalog.subscriptions.firstOrNull { it.id == subscription.id }
            if (record == null) {
                localMessage = "Группа не найдена."
            } else {
                editingSubscriptionGroup = record.toSubscriptionGroupUiState(defaultGroupName)
                editingManualGroup = record.url.isBlank()
                subscriptionEditorVisible = true
            }
        },
        onSetSortMode = {
            sortMode = it
            onHomeSortModeChange(it)
        },
        onSetLocalMessage = { localMessage = it },
        sortMode = sortMode,
        onOpenEditGroup = { groupId ->
            if (groupId == null) {
                editingSubscriptionGroup = null
                editingManualGroup = true
                subscriptionEditorVisible = true
            } else {
                val subId = groupId.removePrefix("subscription:").toIntOrNull()
                val record = subId?.let { id -> subscriptionCatalog.subscriptions.firstOrNull { it.id == id } }
                if (record != null) {
                    editingSubscriptionGroup = record.toSubscriptionGroupUiState(defaultGroupName)
                    editingManualGroup = record.builtIn || record.url.isBlank()
                    subscriptionEditorVisible = true
                } else {
                    localMessage = "Группа не найдена."
                }
            }
        },
        onDeleteSubscriptionConfirm = { id -> pendingSubscriptionDeletion = id },
        onDeleteSubscription = { id ->
            requestDesktopProxyHomeSubscriptionDeletion(
                subscriptionId = id,
                selectedGroupId = homeUiState.selectedGroupId,
                requestDelete = {
                    scope.launch {
                        runCatching { onDeleteSubscription(id) }
                            .onFailure { error -> localMessage = error.message ?: "Не удалось удалить группу." }
                    }
                },
                selectGroup = { groupId -> homeStore.dispatch(ProxyHomeAction.SelectGroup(groupId)) },
            )
        },
        onMoveGroup = { id, offset ->
            scope.launch {
                runCatching { onMoveGroup(id, offset) }
                    .onFailure { error -> localMessage = error.message ?: "Не удалось переместить группу." }
            }
            Result.success(Unit)
        },
        onMoveServer = onMoveServer,
    )
    SideEffect {
        effectContextState.value = effectContext
    }

    if (editingServerModel == null) {
        ProxyHomeScreen(
            store = homeStore,
            contentPadding = contentPadding,
            topContentPadding = 40.dp,
        )
    }

    selectingMembersForServerId?.let { serverId ->
        val strategyGroup = decodedServers.firstOrNull { it.first.id == serverId }?.second as? StrategyGroup
        if (strategyGroup == null) {
            selectingMembersForServerId = null
        } else {
            val choices = remember(strategyGroup, decodedServers, trafficConfigContentById, latencyByServerId) {
                val records = decodedServers.mapNotNull { (stored, server) ->
                    server?.let {
                        ProxyServerRecord(
                            id = stored.id,
                            groupId = stored.subscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId,
                            server = it,
                        )
                    }
                }
                val resolved = resolveStrategyGroupMembers(
                    strategyGroup = strategyGroup,
                    servers = records,
                    trafficConfigContentById = trafficConfigContentById,
                    stripLeadingFlag = { value -> value.stripLeadingCountryFlag() },
                )
                val selectedId = strategyGroup.selectedMemberId ?: resolved.firstOrNull()?.id
                resolved.map { record ->
                    val info = record.server.getInfo()
                    val flag = info.remarks.extractLeadingCountryFlagOrNull()
                    val name = info.remarks.stripLeadingCountryFlag().ifBlank { info.protocol }
                    val latency = when (val result = latencyByServerId[record.id]) {
                        is DesktopServerLatencyResult.Success -> "${result.milliseconds} ms"
                        DesktopServerLatencyResult.Timeout -> "Timeout"
                        is DesktopServerLatencyResult.Error -> "Error"
                        null -> ""
                    }
                    GroupMemberChoice(
                        id = record.id,
                        displayName = name,
                        protocol = info.protocol,
                        flag = flag,
                        latency = latency,
                        selected = record.id == selectedId,
                    )
                }
            }
            SkipiSelectGroupMemberDialog(
                show = true,
                title = strategyGroup.remarks.ifBlank { stringResource(Res.string.proxy_group_select_active_server) },
                summary = stringResource(Res.string.proxy_group_select_active_server),
                members = choices,
                noMembersMessage = stringResource(Res.string.proxy_editor_strategy_group_no_servers),
                cancelLabel = stringResource(Res.string.common_cancel),
                onDismissRequest = { selectingMembersForServerId = null },
                onSelectMember = { memberId ->
                    onSelectStrategyMember(serverId, memberId)
                    selectingMembersForServerId = null
                },
            )
        }
    }

    if (addDialogVisible) {
        SkipiAddSourceDialog(
            show = addDialogVisible,
            mode = addMode,
            serverLink = serverLink,
            subscriptionUrl = subscriptionUrl,
            isEditingServer = editingServerId != null,
            isSubmitting = updatingSubscription,
            onModeChange = { mode ->
                if (mode != SkipiAddSourceMode.Server) editingServerId = null
                addMode = mode
            },
            onServerLinkChange = onServerLinkChange,
            onSubscriptionUrlChange = onSubscriptionUrlChange,
            onSaveServer = { server ->
                onSaveServer(
                    ProxyServerEditResult(
                        serverId = editingServerId,
                        server = server,
                        groupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                    ),
                ) {}
                editingServerId = null
                addDialogVisible = false
            },
            onSaveSubscription = { url ->
                val uri = DesktopSubscriptionInstallUri.parseOrNull(url)
                if (uri != null) {
                    scope.launch {
                        onPrepareSubscription(uri).fold(
                            onSuccess = { subscriptionId ->
                                onUpdateSubscriptionById(subscriptionId)
                                addDialogVisible = false
                            },
                            onFailure = { error ->
                                localMessage = error.message ?: "Не удалось подготовить подписку."
                            },
                        )
                    }
                }
            },
            onClipboardImport = importFromClipboard,
            onFileImport = importFromFile,
            onDismiss = { addDialogVisible = false },
        )
    }

    var importText by remember { mutableStateOf("") }
    if (importDialogVisible) {
        SkipiImportDialog(
            show = importDialogVisible,
            importText = importText,
            replaceExisting = false,
            showReplaceConfiguration = false,
            onImportTextChange = { importText = it },
            onReplaceExistingChange = {},
            onClipboardImport = importFromClipboard,
            onFileImport = importFromFile,
            onConfirmImport = {
                val input = DesktopProxyImportInput.Text(importText)
                scope.launch {
                    onImport(input).fold(
                        onSuccess = { summary ->
                            localMessage = summary
                            importDialogVisible = false
                            importText = ""
                        },
                        onFailure = { error ->
                            localMessage = error.message ?: "Ошибка импорта."
                        },
                    )
                }
            },
            onDismiss = { importDialogVisible = false },
        )
    }

    editingServerModel?.let { editResult ->
        DesktopProxyServerEditorHost(
            contentPadding = contentPadding,
            editResult = editResult,
            decodedServers = decodedServers,
            groupCatalog = groupCatalog,
            presentationNodes = presentationNodes,
            presentationFormatter = presentationFormatter,
            onSave = { result ->
                onSaveServer(result) { outcome ->
                    outcome.toDesktopProxyHomeGroupId()?.let { groupId ->
                        pendingSavedHomeGroupId = groupId
                    }
                }
            },
            onDismiss = { editingServerModel = null },
            onMessage = { localMessage = it },
            exportFullJson = exportFullJson,
        )
    }

    pendingServerDeletion?.let { serverId ->
        DeleteConfirmationDialog(
            show = true,
            title = stringResource(Res.string.common_delete),
            onDismissRequest = { pendingServerDeletion = null },
            onConfirm = {
                onDeleteServer(serverId)
                pendingServerDeletion = null
            },
        )
    }

    pendingSubscriptionDeletion?.let { subscriptionId ->
        DeleteConfirmationDialog(
            show = true,
            title = stringResource(Res.string.subscription_delete),
            onDismissRequest = { pendingSubscriptionDeletion = null },
            onConfirm = {
                pendingSubscriptionDeletion = null
                requestDesktopProxyHomeSubscriptionDeletion(
                    subscriptionId = subscriptionId,
                    selectedGroupId = homeUiState.selectedGroupId,
                    requestDelete = {
                        scope.launch {
                            runCatching { onDeleteSubscription(subscriptionId) }
                                .onFailure { error -> localMessage = error.message ?: "Не удалось удалить группу." }
                        }
                    },
                    selectGroup = { groupId -> homeStore.dispatch(ProxyHomeAction.SelectGroup(groupId)) },
                )
            },
        )
    }

    pendingToolDeletion?.let { tool ->
        val title = when (tool) {
            ProxyHomeServerTool.DeleteDuplicateServers -> stringResource(Res.string.proxy_server_list_delete_duplicates)
            ProxyHomeServerTool.DeleteInvalidServers -> stringResource(Res.string.proxy_server_list_delete_invalid)
            ProxyHomeServerTool.DeleteAllServers -> stringResource(Res.string.proxy_server_list_delete_all)
            else -> stringResource(Res.string.common_delete)
        }
        DeleteConfirmationDialog(
            show = true,
            title = title,
            onDismissRequest = { pendingToolDeletion = null },
            onConfirm = {
                pendingToolDeletion = null
                effectContextState.value?.confirmToolDeletion(tool)
                    ?.onFailure { error -> localMessage = error.message ?: "Не удалось выполнить действие." }
            },
        )
    }

    SubscriptionGroupEditorDialog(
        show = subscriptionEditorVisible,
        group = editingSubscriptionGroup,
        nextGroupId = subscriptionCatalog.nextSubscriptionId,
        userAgentOptions = emptyList(),
        defaultUserAgent = DefaultDesktopSubscriptionUserAgent,
        defaultExpiryReminders = DefaultSubscriptionExpiryReminders,
        confirmDeletion = confirmDeletion,
        isManualGroup = editingManualGroup,
        onDismissRequest = {
            subscriptionEditorVisible = false
            editingSubscriptionGroup = null
            editingManualGroup = false
        },
        onDismissFinished = {},
        onSave = { draft, isNew ->
            val refreshWhenPreviouslyManual = editingSubscriptionGroup?.url.isNullOrBlank()
            scope.launch {
                runCatching { onSaveSubscriptionGroup(draft, isNew, refreshWhenPreviouslyManual) }
                    .onSuccess { result ->
                        val saved = result.savedSubscription
                        if (result.shouldStartRefresh && saved != null) {
                            onUpdateSubscriptionById(saved.id)
                        } else if (saved == null) {
                            localMessage = "Группа не найдена."
                        }
                    }
                    .onFailure { error -> localMessage = error.message ?: "Не удалось сохранить группу." }
            }
        },
        onDelete = { group ->
            subscriptionEditorVisible = false
            requestDesktopProxyHomeSubscriptionDeletion(
                subscriptionId = group.id,
                selectedGroupId = homeUiState.selectedGroupId,
                requestDelete = {
                    scope.launch {
                        runCatching { onDeleteSubscription(group.id) }
                            .onFailure { error -> localMessage = error.message ?: "Не удалось удалить группу." }
                    }
                },
                selectGroup = { groupId -> homeStore.dispatch(ProxyHomeAction.SelectGroup(groupId)) },
            )
        },
        onInvalidUrl = { localMessage = invalidSubscriptionUrlMessage },
    )
}

private data class DesktopImportFile(val name: String, val content: String)

internal fun requestDesktopProxyHomeSubscriptionDeletion(
    subscriptionId: Int,
    selectedGroupId: String,
    requestDelete: () -> Unit,
    selectGroup: (String) -> Unit,
) {
    requestDelete()
    if (selectedGroupId == DesktopProxyGroupIds.subscription(subscriptionId)) {
        selectGroup(DesktopProxyGroupIds.Manual)
    }
}

private fun readDesktopClipboardText(): Result<String> = runCatching {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val text = clipboard.getData(DataFlavor.stringFlavor) as? String
        ?: error("Буфер обмена не содержит текст.")
    require(text.toByteArray(StandardCharsets.UTF_8).size <= MaxDesktopImportBytes) {
        "Данные импорта превышают ${MaxDesktopImportBytes / 1024 / 1024} МБ."
    }
    text
}

private fun chooseDesktopImportFile(): Result<DesktopImportFile?> = runCatching {
    val dialog = FileDialog(null as Frame?, "Импортировать прокси", FileDialog.LOAD)
    dialog.isVisible = true
    val fileName = dialog.file ?: return@runCatching null
    val file = Path.of(dialog.directory.orEmpty(), fileName).normalize()
    require(Files.isRegularFile(file)) { "Выбранный путь не является файлом." }
    require(Files.size(file) <= MaxDesktopImportBytes) {
        "Файл импорта превышает ${MaxDesktopImportBytes / 1024 / 1024} МБ."
    }
    DesktopImportFile(name = file.fileName.toString(), content = Files.readString(file, StandardCharsets.UTF_8))
}

private const val MaxDesktopImportBytes = 8 * 1024 * 1024

private fun openExternalLink(value: String): Result<Unit> = runCatching {
    check(Desktop.isDesktopSupported()) { "Открытие ссылки не поддерживается системой." }
    Desktop.getDesktop().browse(requireSafeDesktopExternalUri(value))
}

internal fun desktopProxyHomeTunnelSnapshot(running: Boolean, connecting: Boolean): TunnelSnapshot = TunnelSnapshot(
    phase = when {
        running -> TunnelPhase.Connected
        connecting -> TunnelPhase.Connecting
        else -> TunnelPhase.Disconnected
    },
)

internal fun desktopSubscriptionUsedBytes(uploadBytes: Long, downloadBytes: Long): Long {
    val upload = uploadBytes.coerceAtLeast(0)
    val download = downloadBytes.coerceAtLeast(0)
    return if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
}

internal class DesktopProxyHomeEffectContext(
    val decodedServers: List<Pair<DesktopStoredProxyServer, ProxyServer<*>?>> = emptyList(),
    val groupCatalog: DesktopProxyGroupCatalog = DesktopProxyGroups.create(
        DesktopServerLibrary(),
        DesktopSubscriptionLibrary(),
    ),
    val subscriptions: List<DesktopStoredSubscription> = emptyList(),
    val currentGroupServerIds: Set<Int> = emptySet(),
    val orderedVisibleServerIds: List<Int> = emptyList(),
    val pingingSubscriptionIds: Set<Int> = emptySet(),
    val updatingSubscription: Boolean = false,
    val confirmDeletion: Boolean = false,
    val sortMode: ProxyHomeSortMode = ProxyHomeSortMode.Default,
    val onToggleTunnel: () -> Unit = {},
    val onSelectServer: (Int) -> Unit = {},
    val onDeleteServer: (Int) -> Unit = {},
    val onMeasureServers: (List<Pair<Int, ProxyServer<*>>>) -> Unit = {},
    val onPingSubscription: (Int, List<Pair<Int, ProxyServer<*>>>) -> Unit = { _, targets -> onMeasureServers(targets) },
    val onUpdateSubscriptionById: (Int) -> Unit = {},
    val onRefreshAllSubscriptions: (() -> Unit)? = null,
    val onDeleteDuplicateServers: ((Set<Int>) -> Unit)? = null,
    val onDeleteInvalidServers: ((Set<Int>) -> Unit)? = null,
    val onRestartService: (() -> Unit)? = null,
    val onDeleteAllServers: ((Set<Int>) -> Unit)? = null,
    val onCancelLatencyTests: (() -> Unit)? = null,
    val onConfirmToolDeletion: ((ProxyHomeServerTool) -> Unit)? = null,
    val onSetSubscriptionEnabled: (Int, Boolean) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    val onCreateServerDraft: (ProxyHomeServerKind) -> Unit = {},
    val onOpenAdd: (SkipiAddSourceMode) -> Unit = {},
    val onOpenImportDialog: () -> Unit = {},
    val onImportFromClipboard: () -> Unit = {},
    val onImportFromFile: () -> Unit = {},
    val onEditServer: (Int) -> Unit = {},
    val onDeleteServerConfirm: (Int) -> Unit = {},
    val onEditSubscription: (DesktopStoredSubscription) -> Unit = {},
    val onSetSortMode: (ProxyHomeSortMode) -> Unit = {},
    val onSetLocalMessage: (String) -> Unit = {},
    val onOpenEditGroup: (String?) -> Unit = {},
    val onDeleteSubscriptionConfirm: (Int) -> Unit = {},
    val onDeleteSubscription: (Int) -> Unit = {},
    val onMoveGroup: (Int, Int) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    val onMoveServer: (fromServerId: Int, toServerId: Int) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    val onOpenStrategyMemberPicker: (Int) -> Unit = {},
    val onSelectStrategyMember: (Int, Int) -> Unit = { _, _ -> },
) {
    fun handle(effect: ProxyHomeEffect): Result<Unit> = when (effect) {
        ProxyHomeEffect.ToggleTunnel -> invoke(onToggleTunnel)
        is ProxyHomeEffect.SelectServer -> effect.serverId.toIntOrNull()?.let { id ->
            invoke { onSelectServer(id) }
        } ?: unsupported("Некорректный идентификатор сервера.")
        is ProxyHomeEffect.TestServer -> testServers(listOf(effect.serverId))
        is ProxyHomeEffect.TestVisibleServers -> testServers(effect.serverIds)
        is ProxyHomeEffect.TestGroup -> groupCatalog.group(effect.groupId)?.let { group ->
            testServers(group.serverIds.map(Int::toString))
        } ?: unsupported("Группа серверов не найдена.")
        is ProxyHomeEffect.PingSubscription -> effect.id.toIntOrNull()?.let { id ->
            if (pingingSubscriptionIds.isNotEmpty()) {
                unsupported("Дождитесь завершения текущей проверки подписки.")
            } else {
                val groupId = DesktopProxyGroupIds.subscription(id)
                groupCatalog.group(groupId)?.let { group -> pingSubscription(id, group.serverIds.map(Int::toString)) }
                    ?: unsupported("Группа подписки не найдена.")
            }
        } ?: unsupported("Некорректный идентификатор подписки.")
        ProxyHomeEffect.CancelLatencyTests -> onCancelLatencyTests?.let(::invoke)
            ?: unsupported("Отмена TCP-проверок не подключена в Desktop.")
        is ProxyHomeEffect.RefreshSubscription -> findSubscription(effect.id)?.let { subscription ->
            if (subscription.url.isBlank()) unsupported("Ручные группы не имеют URL для обновления.")
            else if (updatingSubscription) unsupported("Дождитесь завершения текущего обновления подписки.")
            else invoke { onUpdateSubscriptionById(subscription.id) }
        } ?: unsupported("Подписка не найдена.")
        ProxyHomeEffect.RefreshAllSubscriptions -> refreshAllSubscriptions()
        is ProxyHomeEffect.ToggleSubscriptionEnabled -> findSubscription(effect.id)?.let { subscription ->
            onSetSubscriptionEnabled(subscription.id, !subscription.enabled)
        } ?: unsupported("Подписка не найдена.")
        is ProxyHomeEffect.AddServer -> invoke { onCreateServerDraft(effect.kind) }
        ProxyHomeEffect.AddSubscription -> invoke { onOpenAdd(SkipiAddSourceMode.Subscription) }
        is ProxyHomeEffect.ImportServers -> when (effect.source) {
            ProxyHomeImportSource.ManualInput -> invoke(onOpenImportDialog)
            ProxyHomeImportSource.QrCode -> unsupported("Импорт по QR-коду не поддерживается в Desktop.")
            ProxyHomeImportSource.Clipboard -> invoke(onImportFromClipboard)
            ProxyHomeImportSource.File -> invoke(onImportFromFile)
        }
        is ProxyHomeEffect.EditServer -> effect.id.toIntOrNull()?.let { id ->
            if (decodedServers.any { it.first.id == id && it.second != null }) invoke { onEditServer(id) }
            else unsupported("Сервер не найден.")
        } ?: unsupported("Некорректный идентификатор сервера.")
        is ProxyHomeEffect.DeleteServer -> effect.id.toIntOrNull()?.let { id ->
            if (decodedServers.any { it.first.id == id }) {
                invoke { if (confirmDeletion) onDeleteServerConfirm(id) else onDeleteServer(id) }
            } else unsupported("Сервер не найден.")
        } ?: unsupported("Некорректный идентификатор сервера.")
        is ProxyHomeEffect.ShowServerQr -> unsupported("Показ QR-кода сервера не поддерживается в Desktop.")
        is ProxyHomeEffect.CopyServer -> effect.id.toIntOrNull()?.let { id ->
            copyServer(id, effect.format)
        } ?: unsupported("Некорректный идентификатор сервера.")
        is ProxyHomeEffect.EditSubscription -> findSubscription(effect.id)?.let { subscription ->
            invoke { onEditSubscription(subscription) }
        } ?: unsupported("Подписка не найдена.")
        is ProxyHomeEffect.EditGroup -> {
            val groupId = effect.groupId
            if (groupId == null) {
                invoke { onOpenEditGroup(null) }
            } else {
                val subscriptionId = groupId.removePrefix("subscription:").toIntOrNull()
                if (subscriptionId == null) {
                    unsupported("Встроенные группы нельзя редактировать.")
                } else if (subscriptions.none { it.id == subscriptionId }) {
                    unsupported("Группа не найдена.")
                } else {
                    invoke { onOpenEditGroup(groupId) }
                }
            }
        }
        is ProxyHomeEffect.DeleteGroup -> {
            val subscriptionId = effect.groupId.removePrefix("subscription:").toIntOrNull()
            if (subscriptionId == null) {
                unsupported("Встроенные группы нельзя удалять.")
            } else if (subscriptions.none { it.id == subscriptionId }) {
                unsupported("Группа не найдена.")
            } else {
                invoke {
                    if (confirmDeletion) onDeleteSubscriptionConfirm(subscriptionId)
                    else onDeleteSubscription(subscriptionId)
                }
            }
        }
        is ProxyHomeEffect.MoveGroup -> {
            val subscriptionId = effect.groupId.removePrefix("subscription:").toIntOrNull()
            if (subscriptionId == null) {
                unsupported("Встроенные группы нельзя перемещать.")
            } else {
                val index = subscriptions.indexOfFirst { it.id == subscriptionId }
                val targetIndex = index + effect.offset
                if (index == -1 || targetIndex !in subscriptions.indices || effect.offset == 0) {
                    unsupported("Невозможно переместить группу за пределы списка.")
                } else {
                    onMoveGroup(subscriptionId, effect.offset)
                }
            }
        }
        is ProxyHomeEffect.MoveServer -> {
            if (sortMode != ProxyHomeSortMode.Default) {
                unsupported("Изменение порядка серверов возможно только в стандартной сортировке.")
            } else {
                effect.serverId.toIntOrNull()?.let { id ->
                    val fromIndex = orderedVisibleServerIds.indexOf(id)
                    val toServerId = orderedVisibleServerIds.getOrNull(fromIndex + effect.offset)
                    if (fromIndex == -1 || toServerId == null || effect.offset == 0) {
                        unsupported("Невозможно переместить сервер за пределы группы.")
                    } else {
                        onMoveServer(id, toServerId)
                    }
                } ?: unsupported("Некорректный идентификатор сервера.")
            }
        }
        is ProxyHomeEffect.OpenStrategyMemberPicker -> effect.serverId.toIntOrNull()?.let { id ->
            if (decodedServers.firstOrNull { it.first.id == id }?.second is StrategyGroup) invoke { onOpenStrategyMemberPicker(id) }
            else unsupported("Группа стратегий не найдена.")
        } ?: unsupported("Некорректный идентификатор группы стратегий.")
        is ProxyHomeEffect.SelectStrategyMember -> {
            val id = effect.serverId.toIntOrNull()
            val memberId = effect.memberId.toIntOrNull()
            if (id == null || memberId == null) unsupported("Некорректный идентификатор участника стратегии.")
            else if (decodedServers.firstOrNull { it.first.id == id }?.second !is StrategyGroup) unsupported("Группа стратегий не найдена.")
            else if (decodedServers.none { it.first.id == memberId } || memberId == id) unsupported("Участник стратегии не найден.")
            else invoke { onSelectStrategyMember(id, memberId) }
        }
        is ProxyHomeEffect.SetSort -> invoke { onSetSortMode(effect.mode) }
        is ProxyHomeEffect.RunServerTool -> runServerTool(effect.tool)
        is ProxyHomeEffect.OpenExternalLink -> openExternalLink(effect.url)
    }

    private fun testServers(serverIds: Iterable<String>): Result<Unit> {
        val targets = subscriptionTestTargets(serverIds)
        if (targets.isEmpty()) {
            onSetLocalMessage("В текущей группе нет серверов для TCP-проверки.")
            return Result.success(Unit)
        }
        return invoke { onMeasureServers(targets) }
    }

    private fun pingSubscription(subscriptionId: Int, serverIds: Iterable<String>): Result<Unit> {
        val targets = subscriptionTestTargets(serverIds)
        if (targets.isEmpty()) {
            onSetLocalMessage("В текущей группе нет серверов для TCP-проверки.")
            return Result.success(Unit)
        }
        return invoke { onPingSubscription(subscriptionId, targets) }
    }

    private fun subscriptionTestTargets(serverIds: Iterable<String>): List<Pair<Int, ProxyServer<*>>> =
        serverIds.distinct().mapNotNull { id ->
            val serverId = id.toIntOrNull() ?: return@mapNotNull null
            val server = decodedServers.firstOrNull { it.first.id == serverId }?.second
            server?.takeIf { it.desktopTcpEndpointOrNull() != null }?.let { serverId to it }
        }

    private fun copyServer(serverId: Int, format: ProxyHomeCopyFormat): Result<Unit> {
        val server = decodedServers.firstOrNull { it.first.id == serverId }?.second
            ?: return unsupported("Сервер не найден.")
        val copyText = when (format) {
            ProxyHomeCopyFormat.Url -> server.getUrlOrNull()
            ProxyHomeCopyFormat.FullJson -> server.encodePersistedProxyServer()
            ProxyHomeCopyFormat.QrCode -> null
        } ?: return unsupported("Для этого сервера нельзя сформировать выбранный формат.")
        return runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(copyText), null)
            onSetLocalMessage("Данные сервера скопированы.")
        }
    }

    private fun findSubscription(id: String): DesktopStoredSubscription? =
        id.toIntOrNull()?.let { subscriptionId -> subscriptions.firstOrNull { it.id == subscriptionId } }

    /** Completes a tool after the Home confirmation dialog has been accepted. */
    fun confirmToolDeletion(tool: ProxyHomeServerTool): Result<Unit> = when (tool) {
        ProxyHomeServerTool.DeleteDuplicateServers -> deleteCurrentGroup(onDeleteDuplicateServers)
        ProxyHomeServerTool.DeleteInvalidServers -> deleteCurrentGroup(onDeleteInvalidServers)
        ProxyHomeServerTool.DeleteAllServers -> deleteCurrentGroup(onDeleteAllServers)
        else -> unsupported("Выбранная операция не требует подтверждения удаления.")
    }

    private fun runServerTool(tool: ProxyHomeServerTool): Result<Unit> = when (tool) {
        ProxyHomeServerTool.UpdateSubscriptions -> refreshAllSubscriptions()
        ProxyHomeServerTool.RestartService -> onRestartService?.let(::invoke)
            ?: unsupported("Перезапуск службы не подключён в Desktop.")
        ProxyHomeServerTool.DeleteDuplicateServers,
        ProxyHomeServerTool.DeleteInvalidServers,
        ProxyHomeServerTool.DeleteAllServers -> if (confirmDeletion) {
            onConfirmToolDeletion?.let { callback -> invoke { callback(tool) } }
                ?: unsupported("Подтверждение удаления не подключено в Desktop.")
        } else {
            when (tool) {
                ProxyHomeServerTool.DeleteDuplicateServers -> deleteCurrentGroup(onDeleteDuplicateServers)
                ProxyHomeServerTool.DeleteInvalidServers -> deleteCurrentGroup(onDeleteInvalidServers)
                ProxyHomeServerTool.DeleteAllServers -> deleteCurrentGroup(onDeleteAllServers)
                else -> error("Unreachable server tool: $tool")
            }
        }
    }

    private fun deleteCurrentGroup(callback: ((Set<Int>) -> Unit)?): Result<Unit> = callback?.let { action ->
        invoke { action(currentGroupServerIds) }
    } ?: unsupported("Очистка списка серверов не подключена в Desktop.")

    private fun refreshAllSubscriptions(): Result<Unit> {
        val callback = onRefreshAllSubscriptions
            ?: return Result.failure(
                UnsupportedOperationException("Массовое обновление подписок не подключено в Desktop."),
            )
        return invoke(callback)
    }

    private fun invoke(action: () -> Unit): Result<Unit> = runCatching(action)

    private fun unsupported(message: String): Result<Unit> = Result.failure(IllegalStateException(message))
}

/**
 * Subscription metadata is remote input. Only web pages and one plain email
 * recipient may be sent to the operating system's external URI handlers.
 */
internal fun requireSafeDesktopExternalUri(value: String): URI {
    val uri = URI(value.trim())
    when (uri.scheme?.lowercase()) {
        "https" -> {
            require(uri.host?.isNotBlank() == true) { "HTTPS ссылка должна содержать хост." }
            require(uri.userInfo.isNullOrBlank()) { "HTTPS ссылка не должна содержать учётные данные." }
        }

        "mailto" -> {
            val recipient = uri.rawSchemeSpecificPart
            require(!recipient.isNullOrBlank() && !recipient.contains('?') && !recipient.contains('#')) {
                "Mailto ссылка должна содержать один адрес без параметров."
            }
            require(SafeDesktopMailtoRecipient.matches(recipient)) {
                "Mailto ссылка содержит некорректный адрес."
            }
        }

        else -> error("Поддерживаются только HTTPS и корректные mailto ссылки.")
    }
    return uri
}

private val SafeDesktopMailtoRecipient = Regex(
    "^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+${'$'}",
)
