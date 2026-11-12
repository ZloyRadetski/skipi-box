// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeServerMove
import app.skipi.app.home.resolveProxyHomeServerDrag
import app.skipi.app.home.resolveProxyHomeServerStep
import app.skipi.ui.resources.desktop_home_action_failed
import app.skipi.ui.resources.desktop_home_builtin_group_delete
import app.skipi.ui.resources.desktop_home_builtin_group_edit
import app.skipi.ui.resources.desktop_home_builtin_group_move
import app.skipi.ui.resources.desktop_home_bulk_refresh_not_connected
import app.skipi.ui.resources.desktop_home_clear_list_not_connected
import app.skipi.ui.resources.desktop_home_clipboard_no_text
import app.skipi.ui.resources.desktop_home_clipboard_read_failed
import app.skipi.ui.resources.desktop_home_connection_test_unavailable
import app.skipi.ui.resources.desktop_home_delete_confirmation_not_connected
import app.skipi.ui.resources.desktop_home_file_read_failed
import app.skipi.ui.resources.desktop_home_format_unavailable
import app.skipi.ui.resources.desktop_home_group_change_failed
import app.skipi.ui.resources.desktop_home_group_delete_failed
import app.skipi.ui.resources.desktop_home_group_move_failed
import app.skipi.ui.resources.desktop_home_group_move_out_of_range
import app.skipi.ui.resources.desktop_home_group_not_found
import app.skipi.ui.resources.desktop_home_group_save_failed
import app.skipi.ui.resources.desktop_home_import_data_too_large
import app.skipi.ui.resources.desktop_home_import_dialog_title
import app.skipi.ui.resources.desktop_home_import_error
import app.skipi.ui.resources.desktop_home_import_failed
import app.skipi.ui.resources.desktop_home_import_file_too_large
import app.skipi.ui.resources.desktop_home_invalid_server_id
import app.skipi.ui.resources.desktop_home_invalid_strategy_group_id
import app.skipi.ui.resources.desktop_home_invalid_strategy_member_id
import app.skipi.ui.resources.desktop_home_invalid_subscription_id
import app.skipi.ui.resources.desktop_home_manual_group_no_url
import app.skipi.ui.resources.desktop_home_no_delete_confirmation
import app.skipi.ui.resources.desktop_home_no_servers_for_tcp
import app.skipi.ui.resources.desktop_home_not_ready
import app.skipi.ui.resources.desktop_home_path_not_file
import app.skipi.ui.resources.desktop_home_qr_import_unsupported
import app.skipi.ui.resources.desktop_home_qr_not_connected
import app.skipi.ui.resources.desktop_home_qr_unavailable
import app.skipi.ui.resources.desktop_home_reorder_default_sort_only
import app.skipi.ui.resources.desktop_home_restart_not_connected
import app.skipi.ui.resources.desktop_home_select_server
import app.skipi.ui.resources.desktop_home_server_data_copied
import app.skipi.ui.resources.desktop_home_server_group_not_found
import app.skipi.ui.resources.desktop_home_server_move_not_visible
import app.skipi.ui.resources.desktop_home_server_move_out_of_range
import app.skipi.ui.resources.desktop_home_server_move_same
import app.skipi.ui.resources.desktop_home_server_not_found
import app.skipi.ui.resources.desktop_home_server_params_invalid
import app.skipi.ui.resources.desktop_home_strategy_member_not_found
import app.skipi.ui.resources.desktop_home_subscription_group_not_found
import app.skipi.ui.resources.desktop_home_subscription_not_found
import app.skipi.ui.resources.desktop_home_subscription_prepare_failed
import app.skipi.ui.resources.desktop_home_subscription_save_failed
import app.skipi.ui.resources.desktop_home_tcp_cancel_unavailable
import app.skipi.ui.resources.desktop_home_wait_subscription_check
import app.skipi.ui.resources.desktop_home_wait_subscription_update
import app.skipi.ui.resources.desktop_link_https_host_required
import app.skipi.ui.resources.desktop_link_https_no_credentials
import app.skipi.ui.resources.desktop_link_mailto_invalid_address
import app.skipi.ui.resources.desktop_link_mailto_single_address
import app.skipi.ui.resources.desktop_link_unsupported_scheme
import app.skipi.ui.resources.desktop_open_link_unsupported
import app.skipi.ui.resources.desktop_status_error_markers
import app.skipi.ui.resources.desktop_strategy_group_not_found
import features.subscription.DefaultSubscriptionUserAgent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import app.skipi.app.home.ProxyHomeTunnelIp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeEffectHandler
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ConnectionClock
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomePresentation
import app.skipi.app.home.ProxyHomeQrPayload
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.proxyHomeSortModeFromAndroidValue
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.subscription.SubscriptionEditorSaveResult
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.createProxyServerEditorDraft
import app.skipi.ui.home.ProxyHomeScreen
import app.skipi.ui.home.SkipiConnectionLogPanel
import app.skipi.ui.navigation.SkipiTool
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
import app.skipi.ui.resources.proxy_server_qr_generate_failed
import app.skipi.ui.resources.proxy_server_list_strategy_group_no_members
import app.skipi.ui.resources.proxy_server_list_delete_all
import app.skipi.ui.resources.proxy_server_list_delete_duplicates
import app.skipi.ui.resources.proxy_server_list_delete_invalid
import app.skipi.ui.resources.subscription_delete
import app.skipi.ui.resources.subscription_default_group
import app.skipi.ui.resources.subscription_invalid_url
import app.skipi.ui.resources.proxy_group_select_active_server
import app.skipi.ui.resources.proxy_editor_strategy_group_no_servers
import app.skipi.ui.resources.proxy_server_list_auto_balancers
import app.skipi.ui.resources.routing_default_proxy_server
import app.skipi.ui.server.editor.GroupMemberChoice
import app.skipi.ui.server.editor.ProxyServerEditorMemberOption
import app.skipi.ui.server.editor.SkipiSelectGroupMemberDialog
import app.skipi.app.proxy.ProxyServerRow
import app.skipi.app.proxy.resolveStrategyGroupMembers
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.UrlProxyServer
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.proxy.server.model.isCompositeProxyServer
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
import kotlinx.coroutines.Job
import platform.TunnelPhase
import platform.TunnelSnapshot
import utils.runCatchingCancellable

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
    /** Process-wide, so the session time survives leaving and returning to Home. */
    connectionClock: ConnectionClock = ConnectionClock(),
    /** Null while the Home log is switched off. */
    connectionLog: DesktopConnectionLog? = null,
    tunnelIp: ProxyHomeTunnelIp? = null,
    runtimeOutboundMetric: String? = null,
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
    /** Servers whose live connection is being measured; shown as testing without becoming a bulk test. */
    connectionTestingServerIds: Set<Int> = emptySet(),
    pingingSubscriptionIds: Set<Int>,
    onServerLinkChange: (String) -> Unit,
    onSubscriptionUrlChange: (String) -> Unit,
    onToggleTunnel: () -> Unit,
    onSelectServer: (Int) -> Unit,
    onDeleteServer: (Int) -> Unit,
    onSaveServer: (ProxyServerEditResult, (ProxyServerEditApplyOutcome) -> Unit) -> Unit,
    onMeasureServers: (List<Pair<Int, ProxyServer<*>>>) -> Unit,
    onLaunchHomeLatencyTest: (List<Pair<Int, ProxyServer<*>>>, () -> Unit) -> Job,
    onPingSubscriptionServers: (Int, List<Pair<Int, ProxyServer<*>>>) -> Unit = { _, targets -> onMeasureServers(targets) },
    onUpdateSubscriptionById: (Int) -> Unit,
    onRefreshAllSubscriptions: (() -> Unit)? = null,
    onDeleteDuplicateServers: ((Set<Int>) -> Unit)? = null,
    onDeleteInvalidServers: ((Set<Int>) -> Unit)? = null,
    onRestartService: (() -> Unit)? = null,
    onDeleteAllServers: ((Set<Int>) -> Unit)? = null,
    onCancelLatencyTests: (() -> Unit)? = null,
    onTestConnection: ((Int) -> Unit)? = null,
    onPrepareSubscription: suspend (DesktopSubscriptionInstallUri) -> Result<String>,
    onImport: suspend (DesktopProxyImportInput) -> Result<String>,
    updateBanner: (@Composable () -> Unit)? = null,
    onSaveSubscriptionGroup: suspend (SubscriptionGroupUiState, Boolean, Boolean) -> SubscriptionEditorSaveResult,
    onSetSubscriptionEnabled: suspend (Int, Boolean) -> Unit,
    onDeleteSubscription: suspend (Int) -> Unit,
    contentPadding: PaddingValues,
    desktopSettings: DesktopAppSettings = DesktopAppSettings(),
    onMoveGroup: suspend (Int, Int) -> Unit,
    onMoveServer: (fromServerId: Int, toServerId: Int) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    onHomeSortModeChange: (ProxyHomeSortMode) -> Unit = {},
    onHomeLeftColumnExtraChange: (Float) -> Unit = {},
    /** Tools listed as a menu in the Home header; the sidebar lists them itself, so it passes none. */
    pinnedTools: List<SkipiTool> = emptyList(),
    onOpenPinnedTool: (SkipiTool) -> Unit = {},
) {
    var addDialogVisible by remember { mutableStateOf(false) }
    var importDialogVisible by remember { mutableStateOf(false) }
    var addMode by remember { mutableStateOf(SkipiAddSourceMode.Server) }
    var localMessage by remember { mutableStateOf("") }
    var pendingServerDeletion by remember { mutableStateOf<Int?>(null) }
    var pendingSubscriptionDeletion by remember { mutableStateOf<Int?>(null) }
    var pendingToolDeletion by remember { mutableStateOf<ProxyHomeServerTool?>(null) }
    var qrPayload by remember { mutableStateOf<ProxyHomeQrPayload?>(null) }
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
    val combinedStatusMessage = listOf(tunnelMessage, subscriptionMessage, serverMessage, localMessage)
        .firstOrNull { it.isNotBlank() }
    val errorMarkers = stringResource(Res.string.desktop_status_error_markers)
    val isStatusError = combinedStatusMessage?.let { msg ->
        desktopStatusMessageIsError(msg, errorMarkers)
    } ?: false

    val selectedServer = decodedServers.firstOrNull { (stored, _) -> stored.id == serverLibrary.selectedServerId }?.second
    val selectedTitle = selectedServer?.getInfo()?.remarks.orEmpty().ifBlank { stringResource(Res.string.desktop_home_select_server) }
    var sortMode by remember(desktopSettings.proxyServerListSort) {
        mutableStateOf(proxyHomeSortModeFromAndroidValue(desktopSettings.proxyServerListSort))
    }

    val unknownGroupName = stringResource(Res.string.common_unknown_group)
    val defaultGroupName = stringResource(Res.string.subscription_default_group)
    val allGroupsLabel = stringResource(Res.string.proxy_editor_strategy_group_all_groups)
    val autoBalancerName = stringResource(Res.string.proxy_server_list_auto_balancers)
    val defaultProxyServerTemplate = stringResource(Res.string.routing_default_proxy_server)
    val subscriptionSummaries = subscriptionCatalog.subscriptions.associate { subscription ->
        val group = subscription.toSubscriptionGroupUiState(defaultGroupName)
        group.id to group.toServerPickerSubscriptionSummary()
    }
    val invalidSubscriptionUrlMessage = stringResource(Res.string.subscription_invalid_url)
    val presentationLabels = ProxyServerPresentationLabels(
        unknownGroupName = unknownGroupName,
        allGroupsName = allGroupsLabel,
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
    val editorProxyServers = remember(decodedServers) {
        decodedServers.mapNotNull { (stored, server) ->
            server?.let { current ->
                ProxyServerRow(
                    id = stored.id,
                    groupId = stored.subscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId,
                    server = current,
                )
            }
        }
    }
    val editorInputs = remember(
        editorProxyServers,
        subscriptionCatalog,
        subscriptionSummaries,
        editingServerModel?.serverId,
        allGroupsLabel,
        defaultGroupName,
        autoBalancerName,
        defaultProxyServerTemplate,
    ) {
        projectDesktopProxyServerEditorInputs(
            proxyServers = editorProxyServers,
            subscriptionCatalog = subscriptionCatalog,
            subscriptionSummaries = subscriptionSummaries,
            allGroupsLabel = allGroupsLabel,
            defaultGroupName = defaultGroupName,
            autoBalancerName = autoBalancerName,
            defaultProxyServerTemplate = defaultProxyServerTemplate,
            excludedServerId = editingServerModel?.serverId,
        )
    }

    val groupWithoutMembersText = stringResource(Res.string.proxy_server_list_strategy_group_no_members)
    val homeProjection = remember(
        groupCatalog,
        decodedServers,
        subscriptionLibrary,
        serverLibrary.selectedServerId,
        latencyByServerId,
        testingServerIds,
        connectionTestingServerIds,
        presentationFormatter,
        presentationNodes,
        refreshingSubscriptionIds,
        pingingSubscriptionIds,
        groupWithoutMembersText,
    ) {
        projectDesktopProxyHomeProjection(
            groupCatalog = groupCatalog,
            decodedServers = decodedServers,
            subscriptionLibrary = subscriptionLibrary,
            selectedServerId = serverLibrary.selectedServerId,
            latencyByServerId = latencyByServerId,
            testingServerIds = testingServerIds + connectionTestingServerIds,
            presentationFormatter = presentationFormatter,
            presentationNodes = presentationNodes,
            refreshingSubscriptionIds = refreshingSubscriptionIds,
            pingingSubscriptionIds = pingingSubscriptionIds,
            groupWithoutMembersText = groupWithoutMembersText,
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
    val availableActions = desktopProxyHomeAvailableActions(
        capabilityActions = desktopCapabilities.actions,
        servers = allProxyServers,
        hasStrategyGroup = decodedServers.any { (_, server) -> server is StrategyGroup },
        subscriptions = subscriptionLibrary.subscriptions,
        groupCatalog = groupCatalog,
        canToggleTunnel = canToggleTunnel,
        hasCancelableLatencyTests = testingServerIds.isNotEmpty() && onCancelLatencyTests != null,
        canTestConnection = running && onTestConnection != null,
        pingingSubscriptionIds = pingingSubscriptionIds,
        updatingSubscription = updatingSubscription,
        sortMode = sortMode,
    )

    val homeInput = ProxyHomeInput(
        tunnelSnapshot = desktopProxyHomeTunnelSnapshot(running = running, connecting = connecting),
        connectedSince = connectionClock.update(running),
        selectedServerId = serverLibrary.selectedServerId?.toString(),
        selectedServerTitle = selectedTitle,
        activeProfileName = activeProfileName,
        canToggleTunnel = canToggleTunnel,
        tunnelBusy = connecting,
        groups = proxyGroups,
        servers = allProxyServers,
        isTestingLatency = testingServerIds.isNotEmpty(),
        tunnelIp = tunnelIp,
        runtimeOutboundMetric = runtimeOutboundMetric,
        statusMessage = combinedStatusMessage,
        isStatusError = isStatusError,
        sortMode = sortMode,
        searchEnabled = desktopSettings.showServerSearch,
        // Swiping between subscriptions is a touch gesture: a value saved or restored earlier must not act on a desktop.
        subscriptionSwipeEnabled = false,
        displayOptions = desktopSettings.toProxyHomeDisplayOptions(),
        availableActions = availableActions,
        availableImportSources = setOf(
            ProxyHomeImportSource.ManualInput,
            ProxyHomeImportSource.Clipboard,
            ProxyHomeImportSource.File,
        ),
        availableCopyFormats = buildSet {
            add(ProxyHomeCopyFormat.FullJson)
            if (allProxyServers.any { server -> ProxyHomeCopyFormat.QrCode in server.availableCopyFormats }) {
                add(ProxyHomeCopyFormat.Url)
                add(ProxyHomeCopyFormat.QrCode)
            }
        },
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
                    ?: Result.failure(IllegalStateException(desktopText(Res.string.desktop_home_not_ready)))
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
                val install = text.trim().toDesktopSubscriptionInstallClipboardUriOrNull()
                if (install != null) {
                    onPrepareSubscription(install).fold(
                        onSuccess = { message -> localMessage = message },
                        onFailure = { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_subscription_save_failed) },
                    )
                } else {
                    onImport(DesktopProxyImportInput.Clipboard(text)).fold(
                        onSuccess = { summary -> localMessage = summary },
                        onFailure = { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_import_failed) },
                    )
                }
            }.onFailure { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_clipboard_read_failed) }
        }
    }
    val importFromFile: () -> Unit = {
        scope.launch {
            chooseDesktopImportFile().onSuccess { file ->
                if (file != null) {
                    onImport(DesktopProxyImportInput.File(file.name, file.content)).fold(
                        onSuccess = { summary -> localMessage = summary },
                        onFailure = { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_import_failed) },
                    )
                }
            }.onFailure { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_file_read_failed) }
        }
    }

    val currentDecodedServers by rememberUpdatedState(decodedServers)
    val currentGroupCatalog by rememberUpdatedState(groupCatalog)
    val currentTestingServerIds by rememberUpdatedState(testingServerIds)
    val currentPingingSubscriptionIds by rememberUpdatedState(pingingSubscriptionIds)
    val currentHomeLatencyLauncher by rememberUpdatedState(onLaunchHomeLatencyTest)
    val currentLatencyCancel by rememberUpdatedState(onCancelLatencyTests)
    val homeLatencyCoordinator = remember {
        DesktopProxyHomeLatencyCoordinator(
            getAllTargets = {
                currentDecodedServers.mapNotNull { (stored, server) ->
                    server
                        ?.takeIf { it.canBePingedOnDesktop() }
                        ?.let { stored.id to it }
                }
            },
            targetId = { target -> target.first.toString() },
            getGroupMemberIds = { groupId ->
                currentGroupCatalog.group(groupId)?.serverIds?.mapTo(linkedSetOf()) { it.toString() }
            },
            isHostBusy = {
                currentTestingServerIds.isNotEmpty() || currentPingingSubscriptionIds.isNotEmpty()
            },
            launchTest = { targets, onFinished -> currentHomeLatencyLauncher(targets, onFinished) },
            onCancel = { currentLatencyCancel?.invoke() },
        )
    }

    val effectContext = DesktopProxyHomeEffectContext(
        decodedServers = decodedServers,
        groupCatalog = groupCatalog,
        subscriptions = subscriptionLibrary.subscriptions,
        currentGroupServerIds = groupCatalog.select(homeUiState.selectedGroupId).serverIds.toSet(),
        orderedVisibleServerIds = homeUiState.servers.mapNotNull { server -> server.id.toIntOrNull() },
        selectedServerId = serverLibrary.selectedServerId,
        pingingSubscriptionIds = pingingSubscriptionIds,
        updatingSubscription = updatingSubscription,
        confirmDeletion = confirmDeletion,
        onToggleTunnel = onToggleTunnel,
        onSelectServer = onSelectServer,
        onDeleteServer = onDeleteServer,
        onMeasureServers = onMeasureServers,
        onTestVisibleServers = homeLatencyCoordinator::testVisibleServers,
        onTestGroup = homeLatencyCoordinator::testGroup,
        onPingSubscription = onPingSubscriptionServers,
        onUpdateSubscriptionById = onUpdateSubscriptionById,
        onRefreshAllSubscriptions = onRefreshAllSubscriptions,
        onDeleteDuplicateServers = onDeleteDuplicateServers,
        onDeleteInvalidServers = onDeleteInvalidServers,
        onRestartService = onRestartService,
        onDeleteAllServers = onDeleteAllServers,
        onCancelLatencyTests = { homeLatencyCoordinator.cancel() },
        onTestConnection = onTestConnection,
        onConfirmToolDeletion = { tool -> pendingToolDeletion = tool },
        onSetSubscriptionEnabled = { id, enabled ->
            scope.launch {
                runCatchingCancellable { onSetSubscriptionEnabled(id, enabled) }
                    .onFailure { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_group_change_failed) }
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
                localMessage = desktopText(Res.string.desktop_home_group_not_found)
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
                    localMessage = desktopText(Res.string.desktop_home_group_not_found)
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
                        runCatchingCancellable { onDeleteSubscription(id) }
                            .onFailure { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_group_delete_failed) }
                    }
                },
                selectGroup = { groupId -> homeStore.dispatch(ProxyHomeAction.SelectGroup(groupId)) },
            )
        },
        onMoveGroup = { id, offset ->
            scope.launch {
                runCatchingCancellable { onMoveGroup(id, offset) }
                    .onFailure { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_group_move_failed) }
            }
            Result.success(Unit)
        },
        onMoveServer = onMoveServer,
        onShowServerQr = { payload ->
            qrPayload = payload
            Result.success(Unit)
        },
    )
    SideEffect {
        effectContextState.value = effectContext
    }

    if (editingServerModel == null) {
        Column(androidx.compose.ui.Modifier.fillMaxSize()) {
            updateBanner?.invoke()
            ProxyHomeScreen(
                store = homeStore,
                modifier = androidx.compose.ui.Modifier.weight(1f),
                contentPadding = contentPadding,
                // The floating bar overlays the list (as on Android) instead of cutting it off.
                floatingNavigationBottomInset = contentPadding.calculateBottomPadding(),
                topContentPadding = DesktopProxyHomeTopContentPadding,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                leftColumnExtra = androidx.compose.ui.unit.Dp(desktopSettings.homeLeftColumnExtraDp),
                onLeftColumnExtraChange = { extra -> onHomeLeftColumnExtraChange(extra.value) },
                pinnedTools = pinnedTools,
                onOpenPinnedTool = onOpenPinnedTool,
                wideSidePanel = connectionLog?.let { log ->
                    { panelModifier ->
                        val logState by log.state.collectAsState()
                        SkipiConnectionLogPanel(
                            state = logState,
                            connected = running,
                            formatTime = ::formatDesktopConnectionLogTime,
                            onHeldChange = log::setHeld,
                            onCopy = { host -> copyToClipboard(host) },
                            modifier = panelModifier,
                        )
                    }
                },
            )
        }
    }

    qrPayload?.let { payload ->
        DesktopProxyServerQrCodeDialog(
            title = payload.title,
            text = payload.text,
            generationFailedMessage = stringResource(Res.string.proxy_server_qr_generate_failed),
            onDismissRequest = { qrPayload = null },
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
                        ProxyServerRow(
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
                            onSuccess = { message ->
                                localMessage = message
                                addDialogVisible = false
                            },
                            onFailure = { error ->
                                localMessage = error.message ?: desktopText(Res.string.desktop_home_subscription_prepare_failed)
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
                            localMessage = error.message ?: desktopText(Res.string.desktop_home_import_error)
                        },
                    )
                }
            },
            onDismiss = { importDialogVisible = false },
        )
    }

    editingServerModel?.let { editResult ->
        val memberOptions = remember(decodedServers, presentationNodes, presentationFormatter, editResult.serverId, unknownGroupName) {
            decodedServers.mapNotNull { (stored, candidate) ->
                val member = candidate ?: return@mapNotNull null
                if (stored.id == editResult.serverId || member.isCompositeProxyServer() || member is Custom && !member.canBeUsedInGeneratedProxyPlan()) {
                    return@mapNotNull null
                }
                val node = presentationNodes.firstOrNull { it.id == stored.id }
                ProxyServerEditorMemberOption(
                    id = stored.id,
                    label = node?.let { presentationFormatter.displayOf(it, presentationNodes).summary }
                        ?: member.getInfo().remarks.ifBlank { unknownGroupName },
                )
            }
        }
        DesktopProxyServerEditorHost(
            contentPadding = contentPadding,
            editResult = editResult,
            groupOptions = editorInputs.groupOptions,
            memberOptions = memberOptions,
            memberGroups = editorInputs.memberGroups,
            confirmDeletion = confirmDeletion,
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
                            runCatchingCancellable { onDeleteSubscription(subscriptionId) }
                                .onFailure { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_group_delete_failed) }
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
                    ?.onFailure { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_action_failed) }
            },
        )
    }

    SubscriptionGroupEditorDialog(
        show = subscriptionEditorVisible,
        group = editingSubscriptionGroup,
        nextGroupId = subscriptionCatalog.nextSubscriptionId,
        userAgentOptions = emptyList(),
        defaultUserAgent = DefaultSubscriptionUserAgent,
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
                runCatchingCancellable { onSaveSubscriptionGroup(draft, isNew, refreshWhenPreviouslyManual) }
                    .onSuccess { result ->
                        val saved = result.savedSubscription
                        if (result.shouldStartRefresh && saved != null) {
                            onUpdateSubscriptionById(saved.id)
                        } else if (saved == null) {
                            localMessage = desktopText(Res.string.desktop_home_group_not_found)
                        }
                    }
                    .onFailure { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_group_save_failed) }
            }
        },
        onDelete = { group ->
            subscriptionEditorVisible = false
            requestDesktopProxyHomeSubscriptionDeletion(
                subscriptionId = group.id,
                selectedGroupId = homeUiState.selectedGroupId,
                requestDelete = {
                    scope.launch {
                        runCatchingCancellable { onDeleteSubscription(group.id) }
                            .onFailure { error -> localMessage = error.message ?: desktopText(Res.string.desktop_home_group_delete_failed) }
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
    selectedGroupId: String?,
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
        ?: error(desktopText(Res.string.desktop_home_clipboard_no_text))
    require(text.toByteArray(StandardCharsets.UTF_8).size <= MaxDesktopImportBytes) {
        desktopText(Res.string.desktop_home_import_data_too_large, MaxDesktopImportBytes / 1024 / 1024)
    }
    text
}

private fun chooseDesktopImportFile(): Result<DesktopImportFile?> = runCatching {
    val dialog = FileDialog(null as Frame?, desktopText(Res.string.desktop_home_import_dialog_title), FileDialog.LOAD)
    dialog.isVisible = true
    val fileName = dialog.file ?: return@runCatching null
    val file = Path.of(dialog.directory.orEmpty(), fileName).normalize()
    require(Files.isRegularFile(file)) { desktopText(Res.string.desktop_home_path_not_file) }
    require(Files.size(file) <= MaxDesktopImportBytes) {
        desktopText(Res.string.desktop_home_import_file_too_large, MaxDesktopImportBytes / 1024 / 1024)
    }
    DesktopImportFile(name = file.fileName.toString(), content = Files.readString(file, StandardCharsets.UTF_8))
}

private const val MaxDesktopImportBytes = 8 * 1024 * 1024

private fun openExternalLink(value: String): Result<Unit> = runCatching {
    check(Desktop.isDesktopSupported()) { desktopText(Res.string.desktop_open_link_unsupported) }
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
    val selectedServerId: Int? = null,
    val pingingSubscriptionIds: Set<Int> = emptySet(),
    val updatingSubscription: Boolean = false,
    val confirmDeletion: Boolean = false,
    val sortMode: ProxyHomeSortMode = ProxyHomeSortMode.Default,
    val onToggleTunnel: () -> Unit = {},
    val onSelectServer: (Int) -> Unit = {},
    val onDeleteServer: (Int) -> Unit = {},
    val onMeasureServers: (List<Pair<Int, ProxyServer<*>>>) -> Unit = {},
    val onTestVisibleServers: ((List<String>) -> Result<Unit>)? = null,
    val onTestGroup: ((String) -> Result<Unit>)? = null,
    val onPingSubscription: (Int, List<Pair<Int, ProxyServer<*>>>) -> Unit = { _, targets -> onMeasureServers(targets) },
    val onUpdateSubscriptionById: (Int) -> Unit = {},
    val onRefreshAllSubscriptions: (() -> Unit)? = null,
    val onDeleteDuplicateServers: ((Set<Int>) -> Unit)? = null,
    val onDeleteInvalidServers: ((Set<Int>) -> Unit)? = null,
    val onRestartService: (() -> Unit)? = null,
    val onDeleteAllServers: ((Set<Int>) -> Unit)? = null,
    val onCancelLatencyTests: (() -> Unit)? = null,
    val onTestConnection: ((Int) -> Unit)? = null,
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
    val onShowServerQr: ((ProxyHomeQrPayload) -> Result<Unit>)? = null,
    val onOpenStrategyMemberPicker: (Int) -> Unit = {},
    val onSelectStrategyMember: (Int, Int) -> Unit = { _, _ -> },
) {
    fun handle(effect: ProxyHomeEffect): Result<Unit> = when (effect) {
        ProxyHomeEffect.ToggleTunnel -> invoke(onToggleTunnel)
        is ProxyHomeEffect.SelectServer -> effect.serverId.toIntOrNull()?.let { id ->
            if (id == selectedServerId) Result.success(Unit) else invoke { onSelectServer(id) }
        } ?: unsupported(desktopText(Res.string.desktop_home_invalid_server_id))
        is ProxyHomeEffect.TestServer -> testVisibleServers(listOf(effect.serverId))
        is ProxyHomeEffect.TestConnection -> effect.serverId.toIntOrNull()?.let { id ->
            onTestConnection?.let { test -> invoke { test(id) } }
                ?: unsupported(desktopText(Res.string.desktop_home_connection_test_unavailable))
        } ?: unsupported(desktopText(Res.string.desktop_home_invalid_server_id))
        is ProxyHomeEffect.TestVisibleServers -> testVisibleServers(effect.serverIds)
        is ProxyHomeEffect.TestGroup -> onTestGroup?.let { callback ->
            runCatching { callback(effect.groupId) }.getOrElse { Result.failure(it) }
        } ?: groupCatalog.group(effect.groupId)?.let { group ->
            testServers(group.serverIds.map(Int::toString))
        } ?: unsupported(desktopText(Res.string.desktop_home_server_group_not_found))
        is ProxyHomeEffect.PingSubscription -> effect.id.toIntOrNull()?.let { id ->
            if (pingingSubscriptionIds.isNotEmpty()) {
                unsupported(desktopText(Res.string.desktop_home_wait_subscription_check))
            } else {
                val groupId = DesktopProxyGroupIds.subscription(id)
                groupCatalog.group(groupId)?.let { group -> pingSubscription(id, group.serverIds.map(Int::toString)) }
                    ?: unsupported(desktopText(Res.string.desktop_home_subscription_group_not_found))
            }
        } ?: unsupported(desktopText(Res.string.desktop_home_invalid_subscription_id))
        ProxyHomeEffect.CancelLatencyTests -> onCancelLatencyTests?.let(::invoke)
            ?: unsupported(desktopText(Res.string.desktop_home_tcp_cancel_unavailable))
        is ProxyHomeEffect.RefreshSubscription -> findSubscription(effect.id)?.let { subscription ->
            if (subscription.url.isBlank()) unsupported(desktopText(Res.string.desktop_home_manual_group_no_url))
            else if (updatingSubscription) unsupported(desktopText(Res.string.desktop_home_wait_subscription_update))
            else invoke { onUpdateSubscriptionById(subscription.id) }
        } ?: unsupported(desktopText(Res.string.desktop_home_subscription_not_found))
        ProxyHomeEffect.RefreshAllSubscriptions -> refreshAllSubscriptions()
        is ProxyHomeEffect.ToggleSubscriptionEnabled -> findSubscription(effect.id)?.let { subscription ->
            onSetSubscriptionEnabled(subscription.id, !subscription.enabled)
        } ?: unsupported(desktopText(Res.string.desktop_home_subscription_not_found))
        is ProxyHomeEffect.AddServer -> invoke { onCreateServerDraft(effect.kind) }
        ProxyHomeEffect.AddSubscription -> invoke { onOpenAdd(SkipiAddSourceMode.Subscription) }
        is ProxyHomeEffect.ImportServers -> when (effect.source) {
            ProxyHomeImportSource.ManualInput -> invoke(onOpenImportDialog)
            ProxyHomeImportSource.QrCode -> unsupported(desktopText(Res.string.desktop_home_qr_import_unsupported))
            ProxyHomeImportSource.Clipboard -> invoke(onImportFromClipboard)
            ProxyHomeImportSource.File -> invoke(onImportFromFile)
        }
        is ProxyHomeEffect.EditServer -> effect.id.toIntOrNull()?.let { id ->
            if (decodedServers.any { it.first.id == id && it.second != null }) invoke { onEditServer(id) }
            else unsupported(desktopText(Res.string.desktop_home_server_not_found))
        } ?: unsupported(desktopText(Res.string.desktop_home_invalid_server_id))
        is ProxyHomeEffect.DeleteServer -> effect.id.toIntOrNull()?.let { id ->
            if (decodedServers.any { it.first.id == id }) {
                invoke { if (confirmDeletion) onDeleteServerConfirm(id) else onDeleteServer(id) }
            } else unsupported(desktopText(Res.string.desktop_home_server_not_found))
        } ?: unsupported(desktopText(Res.string.desktop_home_invalid_server_id))
        is ProxyHomeEffect.ShowServerQr -> showServerQr(effect.id)
        is ProxyHomeEffect.CopyServer -> effect.id.toIntOrNull()?.let { id ->
            if (effect.format == ProxyHomeCopyFormat.QrCode) showServerQr(effect.id)
            else copyServer(id, effect.format)
        } ?: unsupported(desktopText(Res.string.desktop_home_invalid_server_id))
        is ProxyHomeEffect.EditSubscription -> findSubscription(effect.id)?.let { subscription ->
            invoke { onEditSubscription(subscription) }
        } ?: unsupported(desktopText(Res.string.desktop_home_subscription_not_found))
        is ProxyHomeEffect.EditGroup -> {
            val groupId = effect.groupId
            if (groupId == null) {
                invoke { onOpenEditGroup(null) }
            } else {
                val subscriptionId = groupId.removePrefix("subscription:").toIntOrNull()
                if (subscriptionId == null) {
                    unsupported(desktopText(Res.string.desktop_home_builtin_group_edit))
                } else if (subscriptions.none { it.id == subscriptionId }) {
                    unsupported(desktopText(Res.string.desktop_home_group_not_found))
                } else {
                    invoke { onOpenEditGroup(groupId) }
                }
            }
        }
        is ProxyHomeEffect.DeleteGroup -> {
            val subscriptionId = effect.groupId.removePrefix("subscription:").toIntOrNull()
            if (subscriptionId == null) {
                unsupported(desktopText(Res.string.desktop_home_builtin_group_delete))
            } else if (subscriptions.none { it.id == subscriptionId }) {
                unsupported(desktopText(Res.string.desktop_home_group_not_found))
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
                unsupported(desktopText(Res.string.desktop_home_builtin_group_move))
            } else {
                val index = subscriptions.indexOfFirst { it.id == subscriptionId }
                val targetIndex = index + effect.offset
                if (index == -1 || targetIndex !in subscriptions.indices || effect.offset == 0) {
                    unsupported(desktopText(Res.string.desktop_home_group_move_out_of_range))
                } else {
                    onMoveGroup(subscriptionId, effect.offset)
                }
            }
        }
        is ProxyHomeEffect.MoveServer -> effect.serverId.toIntOrNull()?.let { id ->
            performMove(resolveProxyHomeServerStep(sortMode, orderedVisibleServerIds, id, effect.offset))
        } ?: unsupported(desktopText(Res.string.desktop_home_invalid_server_id))
        is ProxyHomeEffect.ReorderServer -> {
            val fromServerId = effect.fromServerId.toIntOrNull()
            val toServerId = effect.toServerId.toIntOrNull()
            if (fromServerId == null || toServerId == null) {
                // Reordering only makes sense in the stored order, which is the first thing the refusal names.
                if (sortMode != ProxyHomeSortMode.Default) {
                    performMove(ProxyHomeServerMove.NotDefaultSort)
                } else {
                    unsupported(desktopText(Res.string.desktop_home_invalid_server_id))
                }
            } else {
                performMove(resolveProxyHomeServerDrag(sortMode, orderedVisibleServerIds, fromServerId, toServerId))
            }
        }
        is ProxyHomeEffect.OpenStrategyMemberPicker -> effect.serverId.toIntOrNull()?.let { id ->
            if (decodedServers.firstOrNull { it.first.id == id }?.second is StrategyGroup) invoke { onOpenStrategyMemberPicker(id) }
            else unsupported(desktopText(Res.string.desktop_strategy_group_not_found))
        } ?: unsupported(desktopText(Res.string.desktop_home_invalid_strategy_group_id))
        is ProxyHomeEffect.SelectStrategyMember -> {
            val id = effect.serverId.toIntOrNull()
            val memberId = effect.memberId.toIntOrNull()
            if (id == null || memberId == null) unsupported(desktopText(Res.string.desktop_home_invalid_strategy_member_id))
            else if (decodedServers.firstOrNull { it.first.id == id }?.second !is StrategyGroup) unsupported(desktopText(Res.string.desktop_strategy_group_not_found))
            else if (decodedServers.none { it.first.id == memberId } || memberId == id) unsupported(desktopText(Res.string.desktop_home_strategy_member_not_found))
            else invoke { onSelectStrategyMember(id, memberId) }
        }
        is ProxyHomeEffect.SetSort -> invoke { onSetSortMode(effect.mode) }
        is ProxyHomeEffect.RunServerTool -> runServerTool(effect.tool)
        is ProxyHomeEffect.OpenExternalLink -> openExternalLink(effect.url)
    }


    private fun performMove(move: ProxyHomeServerMove): Result<Unit> = when (move) {
        is ProxyHomeServerMove.Swap -> onMoveServer(move.fromId, move.toId)
        ProxyHomeServerMove.NotDefaultSort ->
            unsupported(desktopText(Res.string.desktop_home_reorder_default_sort_only))
        ProxyHomeServerMove.OutOfRange -> unsupported(desktopText(Res.string.desktop_home_server_move_out_of_range))
        ProxyHomeServerMove.SameServer -> unsupported(desktopText(Res.string.desktop_home_server_move_same))
        ProxyHomeServerMove.NotVisible -> unsupported(desktopText(Res.string.desktop_home_server_move_not_visible))
    }
    private fun testServers(serverIds: Iterable<String>): Result<Unit> {
        val targets = subscriptionTestTargets(serverIds)
        if (targets.isEmpty()) {
            onSetLocalMessage(desktopText(Res.string.desktop_home_no_servers_for_tcp))
            return Result.success(Unit)
        }
        return invoke { onMeasureServers(targets) }
    }

    private fun testVisibleServers(serverIds: List<String>): Result<Unit> = onTestVisibleServers?.let { callback ->
        runCatching { callback(serverIds) }.getOrElse { Result.failure(it) }
    } ?: testServers(serverIds)

    private fun pingSubscription(subscriptionId: Int, serverIds: Iterable<String>): Result<Unit> {
        val targets = subscriptionTestTargets(serverIds)
        if (targets.isEmpty()) {
            onSetLocalMessage(desktopText(Res.string.desktop_home_no_servers_for_tcp))
            return Result.success(Unit)
        }
        return invoke { onPingSubscription(subscriptionId, targets) }
    }

    private fun subscriptionTestTargets(serverIds: Iterable<String>): List<Pair<Int, ProxyServer<*>>> =
        serverIds.distinct().mapNotNull { id ->
            val serverId = id.toIntOrNull() ?: return@mapNotNull null
            val server = decodedServers.firstOrNull { it.first.id == serverId }?.second
            server?.takeIf { it.canBePingedOnDesktop() }?.let { serverId to it }
        }

    private fun copyServer(serverId: Int, format: ProxyHomeCopyFormat): Result<Unit> {
        val server = decodedServers.firstOrNull { it.first.id == serverId }?.second
            ?: return unsupported(desktopText(Res.string.desktop_home_server_not_found))
        val copyText = when (format) {
            ProxyHomeCopyFormat.Url -> server.getUrlOrNull()
            ProxyHomeCopyFormat.FullJson -> server.encodePersistedProxyServer()
            ProxyHomeCopyFormat.QrCode -> null
        } ?: return unsupported(desktopText(Res.string.desktop_home_format_unavailable))
        return runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(copyText), null)
            onSetLocalMessage(desktopText(Res.string.desktop_home_server_data_copied))
        }
    }

    private fun showServerQr(serverIdValue: String): Result<Unit> {
        val serverId = serverIdValue.toIntOrNull()
            ?: return unsupported(desktopText(Res.string.desktop_home_invalid_server_id))
        val server = decodedServers.firstOrNull { (stored, _) -> stored.id == serverId }?.second
            ?: return unsupported(desktopText(Res.string.desktop_home_server_not_found))
        if (server !is UrlProxyServer<*>) {
            return unsupported(desktopText(Res.string.desktop_home_qr_unavailable))
        }
        if (server.validateBasic().isNotEmpty()) {
            return unsupported(desktopText(Res.string.desktop_home_server_params_invalid))
        }
        val text = runCatching { server.getUrlOrNull() }.getOrNull()
            ?: return unsupported(desktopText(Res.string.desktop_home_qr_unavailable))
        val callback = onShowServerQr
            ?: return unsupported(desktopText(Res.string.desktop_home_qr_not_connected))
        return runCatching {
            callback(ProxyHomeQrPayload(title = server.getInfo().remarks, text = text))
        }.getOrElse { error -> Result.failure(error) }
    }

    private fun findSubscription(id: String): DesktopStoredSubscription? =
        id.toIntOrNull()?.let { subscriptionId -> subscriptions.firstOrNull { it.id == subscriptionId } }

    /** Completes a tool after the Home confirmation dialog has been accepted. */
    fun confirmToolDeletion(tool: ProxyHomeServerTool): Result<Unit> = when (tool) {
        ProxyHomeServerTool.DeleteDuplicateServers -> deleteCurrentGroup(onDeleteDuplicateServers)
        ProxyHomeServerTool.DeleteInvalidServers -> deleteCurrentGroup(onDeleteInvalidServers)
        ProxyHomeServerTool.DeleteAllServers -> deleteCurrentGroup(onDeleteAllServers)
        else -> unsupported(desktopText(Res.string.desktop_home_no_delete_confirmation))
    }

    private fun runServerTool(tool: ProxyHomeServerTool): Result<Unit> = when (tool) {
        ProxyHomeServerTool.UpdateSubscriptions -> refreshAllSubscriptions()
        ProxyHomeServerTool.RestartService -> onRestartService?.let(::invoke)
            ?: unsupported(desktopText(Res.string.desktop_home_restart_not_connected))
        ProxyHomeServerTool.DeleteDuplicateServers,
        ProxyHomeServerTool.DeleteInvalidServers,
        ProxyHomeServerTool.DeleteAllServers -> if (confirmDeletion) {
            onConfirmToolDeletion?.let { callback -> invoke { callback(tool) } }
                ?: unsupported(desktopText(Res.string.desktop_home_delete_confirmation_not_connected))
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
    } ?: unsupported(desktopText(Res.string.desktop_home_clear_list_not_connected))

    private fun refreshAllSubscriptions(): Result<Unit> {
        val callback = onRefreshAllSubscriptions
            ?: return Result.failure(
                UnsupportedOperationException(desktopText(Res.string.desktop_home_bulk_refresh_not_connected)),
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
            require(uri.host?.isNotBlank() == true) { desktopText(Res.string.desktop_link_https_host_required) }
            require(uri.userInfo.isNullOrBlank()) { desktopText(Res.string.desktop_link_https_no_credentials) }
        }

        "mailto" -> {
            val recipient = uri.rawSchemeSpecificPart
            require(!recipient.isNullOrBlank() && !recipient.contains('?') && !recipient.contains('#')) {
                desktopText(Res.string.desktop_link_mailto_single_address)
            }
            require(SafeDesktopMailtoRecipient.matches(recipient)) {
                desktopText(Res.string.desktop_link_mailto_invalid_address)
            }
        }

        else -> error(desktopText(Res.string.desktop_link_unsupported_scheme))
    }
    return uri
}

private val SafeDesktopMailtoRecipient = Regex(
    "^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+${'$'}",
)
