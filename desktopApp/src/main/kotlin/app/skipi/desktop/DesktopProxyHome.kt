// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomeGroupKind
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
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeEffectHandler
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomePresentation
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyGroupSummary
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyServerSummary
import app.skipi.app.home.ProxySubscriptionSummary
import app.skipi.ui.home.ProxyHomeScreen
import app.skipi.ui.home.rememberSaveableProxyHomePresentation
import app.skipi.ui.components.DeleteConfirmationDialog
import app.skipi.ui.home.dialogs.SkipiAddSourceDialog
import app.skipi.ui.home.dialogs.SkipiAddSourceMode
import app.skipi.ui.home.dialogs.SkipiImportDialog
import app.skipi.ui.home.dialogs.SkipiSubscriptionEditData
import app.skipi.ui.home.dialogs.SkipiSubscriptionEditDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_add
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_name
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
import app.skipi.ui.resources.subscription_delete
import app.skipi.ui.resources.proxy_group_select_active_server
import app.skipi.ui.resources.proxy_editor_strategy_group_no_servers
import app.skipi.ui.server.editor.GroupMemberChoice
import app.skipi.ui.server.editor.SkipiSelectGroupMemberDialog
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.resolveStrategyGroupMembers
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.extractLeadingCountryFlagOrNull
import features.proxy.server.model.getTransportDisplay
import features.proxy.server.model.encodePersistedProxyServer
import features.proxy.server.model.getUrlOrNull
import features.proxy.server.model.stripLeadingCountryFlag
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
import platform.TunnelPhase
import platform.TunnelSnapshot

/**
 * Desktop adaptation of ProxyServerListPage using the shared adaptive ProxyHomeScreenContent.
 */
@Composable
internal fun DesktopProxyHome(
    serverLibrary: DesktopServerLibrary,
    subscriptionLibrary: DesktopSubscriptionLibrary,
    subscriptionUpdate: DesktopSubscriptionUpdate?,
    serverLink: String,
    subscriptionUrl: String,
    updatingSubscription: Boolean,
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
    exportFullJson: suspend (Int, ProxyServer<*>) -> String,
    onSelectStrategyMember: (Int, Int) -> Unit,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    testingServerIds: Set<Int>,
    pingingSubscriptionIds: Set<Int>,
    onServerLinkChange: (String) -> Unit,
    onSubscriptionUrlChange: (String) -> Unit,
    onToggleTunnel: () -> Unit,
    onSelectServer: (Int) -> Unit,
    onDeleteServer: (Int) -> Unit,
    onAddServer: (ProxyServer<*>) -> Unit,
    onUpdateServer: (Int, ProxyServer<*>) -> Unit,
    onMeasureServers: (List<Pair<Int, ProxyServer<*>>>) -> Unit,
    onPingSubscriptionServers: (Int, List<Pair<Int, ProxyServer<*>>>) -> Unit = { _, targets -> onMeasureServers(targets) },
    onUpdateSubscription: () -> Unit,
    scheduledSubscriptionId: Int?,
    onScheduledSubscriptionConsumed: () -> Unit,
    onPrepareSubscription: (DesktopSubscriptionInstallUri) -> Result<Unit>,
    onImport: (DesktopProxyImportInput) -> Result<String>,
    onUpdateSubscriptionProvider: (Int, DesktopSubscriptionProviderEdit) -> Result<Unit>,
    onDeleteSubscription: (Int) -> Unit,
    contentPadding: PaddingValues,
    desktopSettings: DesktopAppSettings = DesktopAppSettings(),
    onAddManualGroup: (String) -> Result<Unit> = { Result.success(Unit) },
    onMoveGroup: (Int, Int) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    onMoveServer: (Int, Int) -> Result<Unit> = { _, _ -> Result.success(Unit) },
) {
    var addDialogVisible by remember { mutableStateOf(false) }
    var importDialogVisible by remember { mutableStateOf(false) }
    var addMode by remember { mutableStateOf(SkipiAddSourceMode.Server) }
    var localMessage by remember { mutableStateOf("") }
    var pendingServerDeletion by remember { mutableStateOf<Int?>(null) }
    var pendingSubscriptionDeletion by remember { mutableStateOf<Int?>(null) }
    var editingServerId by remember { mutableStateOf<Int?>(null) }
    var editingSubscriptionProvider by remember { mutableStateOf<DesktopStoredSubscription?>(null) }
    var editSubscriptionError by remember { mutableStateOf<String?>(null) }
    var editingGroupDraft by remember { mutableStateOf<DesktopGroupDialogDraft?>(null) }
    var editingGroupError by remember { mutableStateOf<String?>(null) }
    var editingServerModel by remember { mutableStateOf<Pair<Int, ProxyServer<*>>?>(null) }
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
    LaunchedEffect(scheduledSubscriptionId, updatingSubscription) {
        val subscriptionId = scheduledSubscriptionId ?: return@LaunchedEffect
        val subscription = subscriptionLibrary.subscriptions.firstOrNull { it.id == subscriptionId }
        if (subscription != null && subscription.url.isNotBlank() && !updatingSubscription) {
            onSubscriptionUrlChange(subscription.url)
            onUpdateSubscription()
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
    var sortMode by remember { mutableStateOf(ProxyHomeSortMode.Default) }

    val unknownGroupName = stringResource(Res.string.common_unknown_group)
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

    val proxyGroups = remember(groupCatalog.groups, subscriptionLibrary, updatingSubscription, subscriptionUrl, pingingSubscriptionIds) {
        groupCatalog.groups.map { group ->
            val subscription = group.id.removePrefix("subscription:").toIntOrNull()
                ?.let { id -> subscriptionLibrary.subscriptions.firstOrNull { it.id == id } }
            val isPersistedGroup = subscription != null
            val mappedKind = when (group.kind) {
                DesktopProxyGroupKind.All -> ProxyHomeGroupKind.All
                DesktopProxyGroupKind.Manual -> ProxyHomeGroupKind.Manual
                DesktopProxyGroupKind.Subscription -> ProxyHomeGroupKind.Subscription
                DesktopProxyGroupKind.AutoBalancer -> ProxyHomeGroupKind.AutoBalancer
            }
            ProxyGroupSummary(
                id = group.id,
                title = group.title,
                serverCount = group.serverCount,
                enabled = group.enabled,
                serverIds = group.serverIds.map(Int::toString).toSet(),
                canEdit = isPersistedGroup,
                canDelete = isPersistedGroup,
                canMove = isPersistedGroup,
                kind = mappedKind,
                subscription = if (subscription != null && subscription.url.isNotBlank()) {
                    ProxySubscriptionSummary(
                        id = subscription.id.toString(),
                        title = subscription.name,
                        serverCount = group.serverCount,
                        enabled = subscription.enabled,
                        refreshing = updatingSubscription && subscriptionUrl == subscription.url,
                        pinging = subscription.id in pingingSubscriptionIds,
                        updateIntervalHours = subscription.updateInterval,
                        usedBytes = desktopSubscriptionUsedBytes(
                            subscription.metadata.trafficUploadBytes,
                            subscription.metadata.trafficDownloadBytes,
                        ),
                        totalBytes = subscription.metadata.trafficTotalBytes.takeIf { it >= 0 },
                        expireAtSeconds = subscription.metadata.trafficExpireAtSeconds.takeIf { it > 0 },
                        description = subscription.metadata.description.takeIf(String::isNotBlank),
                        announcement = subscription.metadata.announce.takeIf(String::isNotBlank),
                        announcementUrl = subscription.metadata.announceUrl.takeIf(String::isNotBlank),
                        supportUrl = subscription.metadata.supportUrl.takeIf(String::isNotBlank)
                            ?: subscription.metadata.supportEmail.takeIf(String::isNotBlank)?.let { "mailto:$it" },
                        siteUrl = subscription.metadata.profileWebPageUrl.takeIf(String::isNotBlank),
                        lastUpdatedAtMillis = subscription.metadata.lastUpdatedAtMillis.takeIf { it > 0 },
                    )
                } else null,
            )
        }
    }

    val allProxyServers = remember(
        decodedServers,
        groupCatalog,
        serverLibrary.selectedServerId,
        latencyByServerId,
        testingServerIds,
        presentationFormatter,
        presentationNodes,
    ) {
        decodedServers.mapNotNull { (stored, server) ->
            server?.let { current ->
                val info = current.getInfo()
                val (flag, title) = splitFlagAndTitle(info.remarks)
                val groupId = groupCatalog.groups.firstOrNull { group ->
                    group.kind != DesktopProxyGroupKind.All && stored.id in group.serverIds
                }?.id
                val latencyResult = latencyByServerId[stored.id]
                ProxyServerSummary(
                    id = stored.id.toString(),
                    title = title,
                    address = presentationNodes.firstOrNull { node -> node.id == stored.id }
                        ?.let { node -> presentationFormatter.displayOf(node, presentationNodes).summary }
                        ?: info.address,
                    protocol = info.protocol,
                    transport = current.getTransportDisplay(),
                    flag = flag,
                    selected = stored.id == serverLibrary.selectedServerId,
                    latencyMs = (latencyResult as? DesktopServerLatencyResult.Success)?.milliseconds,
                    latencyTesting = stored.id in testingServerIds,
                    latencyError = latencyResult is DesktopServerLatencyResult.Error || latencyResult is DesktopServerLatencyResult.Timeout,
                    canTest = current.desktopTcpEndpointOrNull() != null,
                    availableCopyFormats = buildSet {
                        add(ProxyHomeCopyFormat.FullJson)
                        if (current.getUrlOrNull() != null) add(ProxyHomeCopyFormat.Url)
                    },
                    isStrategyGroup = current is StrategyGroup,
                    groupId = groupId,
                    searchText = listOf(info.remarks, info.address, info.protocol).joinToString(" "),
                    sortKey = info.remarks.ifBlank { title },
                )
            }
        }
    }

    val availableActions = buildSet {
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
        if (subscriptionLibrary.subscriptions.isNotEmpty() && !updatingSubscription) {
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
        availableServerKinds = setOf(ProxyHomeServerKind.Custom),
        availableServerTools = emptySet(),
    )

    val scope = rememberCoroutineScope()
    var homePresentation by rememberSaveableProxyHomePresentation(
        ProxyHomePresentation(selectedGroupId = groupCatalog.defaultGroupId),
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
        readDesktopClipboardText().onSuccess { text ->
            val install = text.trim().toDesktopSubscriptionInstallUriOrNull()
            if (install != null) {
                onPrepareSubscription(install).fold(
                    onSuccess = {
                        onSubscriptionUrlChange(install.url)
                        onUpdateSubscription()
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
    val importFromFile: () -> Unit = {
        chooseDesktopImportFile().onSuccess { file ->
            if (file != null) {
                onImport(DesktopProxyImportInput.File(file.name, file.content)).fold(
                    onSuccess = { summary -> localMessage = summary },
                    onFailure = { error -> localMessage = error.message ?: "Не удалось импортировать." },
                )
            }
        }.onFailure { error -> localMessage = error.message ?: "Не удалось прочитать файл." }
    }

    val effectContext = DesktopProxyHomeEffectContext(
        decodedServers = decodedServers,
        groupCatalog = groupCatalog,
        subscriptions = subscriptionLibrary.subscriptions,
        pingingSubscriptionIds = pingingSubscriptionIds,
        updatingSubscription = updatingSubscription,
        confirmDeletion = confirmDeletion,
        onToggleTunnel = onToggleTunnel,
        onSelectServer = onSelectServer,
        onDeleteServer = onDeleteServer,
        onMeasureServers = onMeasureServers,
        onPingSubscription = onPingSubscriptionServers,
        onSubscriptionUrlChange = onSubscriptionUrlChange,
        onUpdateSubscription = onUpdateSubscription,
        onUpdateSubscriptionProvider = onUpdateSubscriptionProvider,
        onOpenAdd = { mode ->
            editingServerId = null
            if (mode == SkipiAddSourceMode.Server) onServerLinkChange("") else onSubscriptionUrlChange("")
            addMode = mode
            addDialogVisible = true
        },
        onOpenImportDialog = { importDialogVisible = true },
        onImportFromClipboard = importFromClipboard,
        onImportFromFile = importFromFile,
        onEditServer = { id ->
            decodedServers.firstOrNull { it.first.id == id }?.let { (stored, server) ->
                if (server != null) editingServerModel = stored.id to server
            }
        },
        onOpenStrategyMemberPicker = { id -> selectingMembersForServerId = id },
        onSelectStrategyMember = onSelectStrategyMember,
        onDeleteServerConfirm = { id -> pendingServerDeletion = id },
        onEditSubscription = { subscription ->
            editSubscriptionError = null
            editingSubscriptionProvider = subscription
        },
        onSetSortMode = { sortMode = it },
        onSetLocalMessage = { localMessage = it },
        sortMode = sortMode,
        onOpenEditGroup = { groupId ->
            editingGroupError = null
            if (groupId == null) {
                editingGroupDraft = DesktopGroupDialogDraft(subscriptionId = null, name = "")
            } else {
                val subId = groupId.removePrefix("subscription:").toIntOrNull()
                val sub = subId?.let { id -> subscriptionLibrary.subscriptions.firstOrNull { it.id == id } }
                if (sub != null) {
                    editingGroupDraft = DesktopGroupDialogDraft(subscriptionId = sub.id, name = sub.name)
                }
            }
        },
        onDeleteSubscriptionConfirm = { id -> pendingSubscriptionDeletion = id },
        onDeleteSubscription = onDeleteSubscription,
        onMoveGroup = onMoveGroup,
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
                editingServerId?.let { serverId -> onUpdateServer(serverId, server) } ?: onAddServer(server)
                editingServerId = null
                addDialogVisible = false
            },
            onSaveSubscription = { url ->
                val uri = DesktopSubscriptionInstallUri.parseOrNull(url)
                if (uri != null) {
                    onPrepareSubscription(uri).fold(
                        onSuccess = {
                            onUpdateSubscription()
                            addDialogVisible = false
                        },
                        onFailure = { error ->
                            localMessage = error.message ?: "Не удалось подготовить подписку."
                        },
                    )
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
                onImport(DesktopProxyImportInput.Text(importText)).fold(
                    onSuccess = { summary ->
                        localMessage = summary
                        importDialogVisible = false
                        importText = ""
                    },
                    onFailure = { error ->
                        localMessage = error.message ?: "Ошибка импорта."
                    },
                )
            },
            onDismiss = { importDialogVisible = false },
        )
    }

    editingSubscriptionProvider?.let { subscription ->
        SkipiSubscriptionEditDialog(
            show = true,
            errorMessage = editSubscriptionError,
            onDraftChanged = { editSubscriptionError = null },
            initialData = SkipiSubscriptionEditData(
                name = subscription.name,
                url = subscription.url,
                userAgent = subscription.userAgent,
                updateInterval = subscription.updateInterval,
                ageSecretKey = subscription.ageSecretKey,
                updateViaProxy = subscription.updateViaProxy,
                autoOverrideRules = subscription.autoOverrideRules,
                enabled = subscription.enabled,
            ),
            onSave = { draft ->
                editSubscriptionError = null
                val result = onUpdateSubscriptionProvider(
                    subscription.id,
                    subscription.toProviderEdit().copy(
                        name = draft.name,
                        url = draft.url,
                        userAgent = draft.userAgent,
                        updateInterval = draft.updateInterval,
                        ageSecretKey = draft.ageSecretKey,
                        updateViaProxy = draft.updateViaProxy,
                        autoOverrideRules = draft.autoOverrideRules,
                        enabled = draft.enabled,
                    ),
                )
                result.fold(
                    onSuccess = {
                        editSubscriptionError = null
                        editingSubscriptionProvider = null
                    },
                    onFailure = { failure ->
                        editSubscriptionError = failure.message?.takeIf(String::isNotBlank)
                            ?: "Не удалось сохранить параметры подписки."
                    },
                )
            },
            onDelete = {
                editSubscriptionError = null
                editingSubscriptionProvider = null
                pendingSubscriptionDeletion = subscription.id
            },
            onDismiss = {
                editSubscriptionError = null
                editingSubscriptionProvider = null
            },
        )
    }

    editingServerModel?.let { (serverId, server) ->
        DesktopProxyServerEditorHost(
            contentPadding = contentPadding,
            serverId = serverId,
            original = server,
            decodedServers = decodedServers,
            groupCatalog = groupCatalog,
            presentationNodes = presentationNodes,
            presentationFormatter = presentationFormatter,
            onSave = onUpdateServer,
            onDismiss = { editingServerModel = null },
            onMessage = { localMessage = it },
            exportFullJson = { exportFullJson(serverId, it) },
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
                onDeleteSubscription(subscriptionId)
                pendingSubscriptionDeletion = null
            },
        )
    }

    editingGroupDraft?.let { draft ->
        var nameText by remember(draft) { mutableStateOf(draft.name) }
        val isNew = draft.subscriptionId == null
        val titleText = if (isNew) "Новая группа" else "Редактировать группу"
        val confirmText = if (isNew) stringResource(Res.string.common_add) else stringResource(Res.string.common_save)
        AlertDialog(
            onDismissRequest = {
                editingGroupDraft = null
                editingGroupError = null
            },
            title = {
                Text(titleText)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = {
                            nameText = it
                            editingGroupError = null
                        },
                        label = { Text(stringResource(Res.string.configs_name)) },
                        singleLine = true,
                        isError = editingGroupError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    editingGroupError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = nameText.trim()
                        if (trimmed.isBlank()) {
                            editingGroupError = "Имя группы не может быть пустым."
                            return@TextButton
                        }
                        if (isNew) {
                            onAddManualGroup(trimmed).fold(
                                onSuccess = {
                                    editingGroupDraft = null
                                    editingGroupError = null
                                },
                                onFailure = { err ->
                                    editingGroupError = err.message ?: "Не удалось создать группу."
                                },
                            )
                        } else {
                            val sub = subscriptionLibrary.subscriptions.firstOrNull { it.id == draft.subscriptionId }
                            if (sub != null) {
                                onUpdateSubscriptionProvider(
                                    sub.id,
                                    sub.toProviderEdit().copy(name = trimmed),
                                ).fold(
                                    onSuccess = {
                                        editingGroupDraft = null
                                        editingGroupError = null
                                    },
                                    onFailure = { err ->
                                        editingGroupError = err.message ?: "Не удалось сохранить изменения группы."
                                    },
                                )
                            } else {
                                editingGroupError = "Группа не найдена."
                            }
                        }
                    },
                    enabled = nameText.isNotBlank(),
                ) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingGroupDraft = null
                        editingGroupError = null
                    },
                ) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
}

internal data class DesktopGroupDialogDraft(
    val subscriptionId: Int?,
    val name: String,
)

private data class DesktopImportFile(val name: String, val content: String)

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

private fun splitFlagAndTitle(value: String): Pair<String?, String> {
    val trimmed = value.trim().ifBlank { "Без названия" }
    val prefix = trimmed.substringBefore(' ')
    val looksLikeEmoji = prefix.any(Char::isSurrogate) || prefix == "⚡"
    return if (looksLikeEmoji && prefix.length <= 5) prefix to trimmed.removePrefix(prefix).trim().ifBlank { trimmed }
    else null to trimmed
}

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
    val pingingSubscriptionIds: Set<Int> = emptySet(),
    val updatingSubscription: Boolean = false,
    val confirmDeletion: Boolean = false,
    val sortMode: ProxyHomeSortMode = ProxyHomeSortMode.Default,
    val onToggleTunnel: () -> Unit = {},
    val onSelectServer: (Int) -> Unit = {},
    val onDeleteServer: (Int) -> Unit = {},
    val onMeasureServers: (List<Pair<Int, ProxyServer<*>>>) -> Unit = {},
    val onPingSubscription: (Int, List<Pair<Int, ProxyServer<*>>>) -> Unit = { _, targets -> onMeasureServers(targets) },
    val onSubscriptionUrlChange: (String) -> Unit = {},
    val onUpdateSubscription: () -> Unit = {},
    val onUpdateSubscriptionProvider: (Int, DesktopSubscriptionProviderEdit) -> Result<Unit> = { _, _ -> Result.success(Unit) },
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
    val onMoveServer: (Int, Int) -> Result<Unit> = { _, _ -> Result.success(Unit) },
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
        ProxyHomeEffect.CancelLatencyTests -> unsupported("Отмена TCP-проверок не поддерживается в Desktop.")
        is ProxyHomeEffect.RefreshSubscription -> findSubscription(effect.id)?.let { subscription ->
            if (subscription.url.isBlank()) unsupported("Ручные группы не имеют URL для обновления.")
            else if (updatingSubscription) unsupported("Дождитесь завершения текущего обновления подписки.")
            else invoke {
                onSubscriptionUrlChange(subscription.url)
                onUpdateSubscription()
            }
        } ?: unsupported("Подписка не найдена.")
        ProxyHomeEffect.RefreshAllSubscriptions -> unsupported("Массовое обновление подписок не поддерживается в Desktop.")
        is ProxyHomeEffect.ToggleSubscriptionEnabled -> findSubscription(effect.id)?.let { subscription ->
            if (updatingSubscription) unsupported("Дождитесь завершения текущего обновления подписки.") else {
                onUpdateSubscriptionProvider(
                    subscription.id,
                    subscription.toProviderEdit().copy(enabled = !subscription.enabled),
                )
            }
        } ?: unsupported("Подписка не найдена.")
        is ProxyHomeEffect.AddServer -> invoke { onOpenAdd(SkipiAddSourceMode.Server) }
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
                    val stored = decodedServers.firstOrNull { it.first.id == id }?.first
                    if (stored == null) {
                        unsupported("Сервер не найден.")
                    } else {
                        val groupServers = decodedServers.map { it.first }.filter { it.subscriptionId == stored.subscriptionId }
                        val fromIndex = groupServers.indexOfFirst { it.id == id }
                        val toIndex = fromIndex + effect.offset
                        if (fromIndex == -1 || toIndex !in groupServers.indices || effect.offset == 0) {
                            unsupported("Невозможно переместить сервер за пределы группы.")
                        } else {
                            onMoveServer(id, effect.offset)
                        }
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

    private fun runServerTool(tool: ProxyHomeServerTool): Result<Unit> = when (tool) {
        ProxyHomeServerTool.UpdateSubscriptions -> unsupported("Общее обновление подписок не поддерживается в Desktop.")
        ProxyHomeServerTool.RestartService,
        ProxyHomeServerTool.DeleteDuplicateServers,
        ProxyHomeServerTool.DeleteInvalidServers,
        ProxyHomeServerTool.DeleteAllServers -> unsupported("Эта операция не поддерживается в Desktop.")
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
