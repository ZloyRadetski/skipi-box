// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.TunnelPhase
import platform.TunnelSnapshot

enum class ProxyHomeConnectionMode {
    Classic,
    Compact,
}

data class ProxyHomeDisplayOptions(
    val connectionMode: ProxyHomeConnectionMode = ProxyHomeConnectionMode.Classic,
    val pinConnectionPanel: Boolean = false,
    val classicFloatingPowerButton: Boolean = false,
    val requestedColumns: Int = 1,
    val showAllGroup: Boolean = false,
    val showTunnelMemory: Boolean = false,
    /** Optional host customizations, encoded using Compose's packed ARGB Long representation. */
    val latencyFastColor: Long? = null,
    val latencyMediumColor: Long? = null,
    val latencySlowColor: Long? = null,
    val latencyErrorColor: Long? = null,
)

/**
 * Host-owned snapshot used to render Home. The host remains the source of truth for tunnel,
 * server, group, subscription, and persisted preference data.
 */
data class ProxyHomeInput(
    val tunnelSnapshot: TunnelSnapshot = TunnelSnapshot(),
    val selectedServerId: String? = null,
    val selectedServerTitle: String = "",
    val activeProfileName: String? = null,
    val canToggleTunnel: Boolean = false,
    val tunnelBusy: Boolean = false,
    val groups: List<ProxyGroupSummary> = emptyList(),
    /** Raw host order; shared Home applies [sortMode] using canonical [ProxyServerSummary.sortKey]. */
    val servers: List<ProxyServerSummary> = emptyList(),
    val isTestingLatency: Boolean = false,
    val statusMessage: String? = null,
    val isStatusError: Boolean = false,
    val sortMode: ProxyHomeSortMode = ProxyHomeSortMode.Default,
    val searchEnabled: Boolean = true,
    val subscriptionSwipeEnabled: Boolean = true,
    val displayOptions: ProxyHomeDisplayOptions = ProxyHomeDisplayOptions(),
    val runtimeOutboundMetric: String? = null,
    val availableActions: Set<ProxyHomeActionId> = emptySet(),
    val availableImportSources: Set<ProxyHomeImportSource> = emptySet(),
    val availableCopyFormats: Set<ProxyHomeCopyFormat> = emptySet(),
    val availableServerKinds: Set<ProxyHomeServerKind> = emptySet(),
    val availableServerTools: Set<ProxyHomeServerTool> = emptySet(),
)

/** Local, presentation-only state that should survive host snapshot refreshes. */
data class ProxyHomePresentation(
    val selectedGroupId: String? = null,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val collapsedSubscriptionGroupIds: Set<String> = emptySet(),
)

/** Reduces Home presentation actions without host or Compose dependencies. */
fun reduceProxyHomePresentation(
    presentation: ProxyHomePresentation,
    action: ProxyHomeAction,
    searchEnabled: Boolean = true,
): ProxyHomePresentation = when (action) {
    is ProxyHomeAction.SelectGroup -> presentation.copy(selectedGroupId = action.groupId)
    is ProxyHomeAction.SetSearchQuery -> presentation.copy(searchQuery = action.query)
    is ProxyHomeAction.SetSearchVisible -> presentation.copy(isSearchVisible = action.visible && searchEnabled)
    else -> presentation
}

data class ProxyServerSummary(
    val id: String,
    val title: String,
    val address: String,
    val protocol: String,
    val selected: Boolean = false,
    val flag: String? = null,
    val transport: String? = null,
    val latencyMs: Long? = null,
    val latencyError: Boolean = false,
    val latencyTesting: Boolean = false,
    val canTest: Boolean = true,
    val availableCopyFormats: Set<ProxyHomeCopyFormat> = emptySet(),
    val isStrategyGroup: Boolean = false,
    val groupId: String? = null,
    val searchText: String? = null,
    val sortKey: String? = null,
    val latencyErrorText: String? = null,
)

enum class ProxyHomeGroupKind {
    All,
    Manual,
    Subscription,
    AutoBalancer,
    Other,
}

data class ProxyGroupSummary(
    val id: String,
    val title: String,
    val serverCount: Int,
    val enabled: Boolean,
    /** Null means membership is derived from [ProxyServerSummary.groupId]. */
    val serverIds: Set<String>? = null,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
    val canMove: Boolean = false,
    /** Subscription data has one owner: its group summary. */
    val subscription: ProxySubscriptionSummary? = null,
    val kind: ProxyHomeGroupKind = ProxyHomeGroupKind.Other,
)

data class ProxySubscriptionSummary(
    val id: String,
    val title: String,
    val serverCount: Int,
    val enabled: Boolean,
    val refreshing: Boolean,
    val usedBytes: Long = 0,
    val totalBytes: Long? = null,
    val updateIntervalHours: String? = null,
    val expireAtSeconds: Long? = null,
    val description: String? = null,
    val announcement: String? = null,
    val announcementUrl: String? = null,
    val supportUrl: String? = null,
    val siteUrl: String? = null,
    val lastUpdatedAtMillis: Long? = null,
    /** A latency check is actively running for this subscription's servers. */
    val pinging: Boolean = false,
    /** Repeating the ping action cancels this subscription's active latency check. */
    val canCancelPing: Boolean = false,
)

enum class ProxyConnectionPhase {
    Connected,
    Connecting,
    Disconnected,
}

/** A platform-neutral local failure code; the shared UI supplies the localized text. */
enum class ProxyHomeLocalError {
    Unavailable,
    VariantUnavailable,
    OperationFailed,
}

enum class ProxyHomeSortMode {
    Default,
    Name,
    Latency,
}

enum class ProxyHomeServerKind {
    Http,
    Vmess,
    Vless,
    Trojan,
    Shadowsocks,
    Socks,
    Hysteria2,
    Wireguard,
    AmneziaWg,
    OlcRtc,
    StrategyGroup,
    ChainProxy,
    Custom,
}

enum class ProxyHomeImportSource {
    ManualInput,
    QrCode,
    Clipboard,
    File,
}

enum class ProxyHomeCopyFormat {
    Url,
    FullJson,
    QrCode,
}

enum class ProxyHomeServerTool {
    RestartService,
    UpdateSubscriptions,
    DeleteDuplicateServers,
    DeleteInvalidServers,
    DeleteAllServers,
}

/** Explicit capabilities supplied by a host; commands without a capability are rejected. */
enum class ProxyHomeActionId {
    ToggleTunnel,
    SelectServer,
    TestServer,
    TestVisibleServers,
    TestGroup,
    CancelLatencyTests,
    RefreshSubscription,
    RefreshAllSubscriptions,
    PingSubscription,
    ToggleSubscriptionEnabled,
    AddServer,
    AddSubscription,
    ImportServers,
    EditServer,
    DeleteServer,
    ShowServerQr,
    CopyServer,
    EditSubscription,
    EditGroup,
    DeleteGroup,
    MoveGroup,
    MoveServer,
    OpenStrategyMemberPicker,
    SelectStrategyMember,
    SetSort,
    RunServerTool,
    OpenExternalLink,
}

data class ProxyHomePageUiState(
    val group: ProxyGroupSummary,
    val servers: List<ProxyServerSummary>,
    val subscription: ProxySubscriptionSummary?,
    val subscriptionExpanded: Boolean,
)

/** UI state is derived from the authoritative host input plus local presentation state. */
data class ProxyHomeUiState(
    val tunnelSnapshot: TunnelSnapshot,
    val selectedServerId: String?,
    /** Selected server projected from the full input, even when search/group filters hide it. */
    val selectedServer: ProxyServerSummary?,
    val selectedServerTitle: String,
    val activeProfileName: String?,
    val canToggleTunnel: Boolean,
    val tunnelBusy: Boolean,
    val groups: List<ProxyGroupSummary>,
    val selectedGroupId: String?,
    val servers: List<ProxyServerSummary>,
    val subscription: ProxySubscriptionSummary?,
    val pages: List<ProxyHomePageUiState> = emptyList(),
    val isTestingLatency: Boolean,
    val searchQuery: String,
    val isSearchVisible: Boolean,
    val searchEnabled: Boolean,
    val subscriptionSwipeEnabled: Boolean,
    val displayOptions: ProxyHomeDisplayOptions = ProxyHomeDisplayOptions(),
    val sortMode: ProxyHomeSortMode,
    val runtimeOutboundMetric: String?,
    val statusMessage: String?,
    val isStatusError: Boolean,
    val localError: ProxyHomeLocalError?,
    val availableActions: Set<ProxyHomeActionId>,
    val availableImportSources: Set<ProxyHomeImportSource>,
    val availableCopyFormats: Set<ProxyHomeCopyFormat>,
    val availableServerKinds: Set<ProxyHomeServerKind>,
    val availableServerTools: Set<ProxyHomeServerTool>,
    val busyActions: Set<ProxyHomeActionId>,
    val rejectedAction: ProxyHomeActionId?,
) {
    val connectionPhase: ProxyConnectionPhase
        get() = when (tunnelSnapshot.phase) {
            TunnelPhase.Connected -> ProxyConnectionPhase.Connected
            TunnelPhase.Connecting, TunnelPhase.Disconnecting -> ProxyConnectionPhase.Connecting
            TunnelPhase.Disconnected, TunnelPhase.Failed -> ProxyConnectionPhase.Disconnected
        }

    val isRunning: Boolean get() = tunnelSnapshot.phase == TunnelPhase.Connected
    val isConnecting: Boolean get() = connectionPhase == ProxyConnectionPhase.Connecting
}

/** A single user intent from the Home surface. */
sealed interface ProxyHomeAction {
    data class SelectGroup(val groupId: String) : ProxyHomeAction
    data class SetSearchQuery(val query: String) : ProxyHomeAction
    data class SetSearchVisible(val visible: Boolean) : ProxyHomeAction
    data class ToggleSubscriptionExpanded(val groupId: String) : ProxyHomeAction
    data object DismissMessage : ProxyHomeAction

    data object ToggleTunnel : ProxyHomeAction
    data class SelectServer(val serverId: String) : ProxyHomeAction
    data class TestServer(val serverId: String) : ProxyHomeAction
    data class TestVisibleServers(val serverIds: List<String>) : ProxyHomeAction
    /** Compatibility intent; the store resolves it against its current filtered server list. */
    data object TestAllVisibleServers : ProxyHomeAction
    data class TestGroup(val groupId: String) : ProxyHomeAction
    data object CancelLatencyTests : ProxyHomeAction
    data class RefreshSubscription(val id: String) : ProxyHomeAction
    data object RefreshAllSubscriptions : ProxyHomeAction
    data class PingSubscription(val id: String) : ProxyHomeAction
    data class ToggleSubscriptionEnabled(val id: String) : ProxyHomeAction
    data class AddServer(val kind: ProxyHomeServerKind) : ProxyHomeAction
    data object AddSubscription : ProxyHomeAction
    data class ImportServers(val source: ProxyHomeImportSource) : ProxyHomeAction
    data class EditServer(val id: String) : ProxyHomeAction
    data class DeleteServer(val id: String) : ProxyHomeAction
    data class ShowServerQr(val id: String) : ProxyHomeAction
    data class CopyServer(val id: String, val format: ProxyHomeCopyFormat) : ProxyHomeAction
    data class EditSubscription(val id: String) : ProxyHomeAction
    data class EditGroup(val groupId: String?) : ProxyHomeAction
    data class DeleteGroup(val groupId: String) : ProxyHomeAction
    data class MoveGroup(val groupId: String, val offset: Int) : ProxyHomeAction
    data class MoveServer(val serverId: String, val offset: Int) : ProxyHomeAction
    data class OpenStrategyMemberPicker(val serverId: String) : ProxyHomeAction
    data class SelectStrategyMember(val serverId: String, val memberId: String) : ProxyHomeAction
    data class SetSort(val mode: ProxyHomeSortMode) : ProxyHomeAction
    data class RunServerTool(val tool: ProxyHomeServerTool) : ProxyHomeAction
    data class OpenExternalLink(val url: String) : ProxyHomeAction
}

/** Platform work requested by [ProxyHomeAction]; UI and store never dispatch host callbacks twice. */
sealed interface ProxyHomeEffect {
    data object ToggleTunnel : ProxyHomeEffect
    data class SelectServer(val serverId: String) : ProxyHomeEffect
    data class TestServer(val serverId: String) : ProxyHomeEffect
    data class TestVisibleServers(val serverIds: List<String>) : ProxyHomeEffect
    data class TestGroup(val groupId: String) : ProxyHomeEffect
    data object CancelLatencyTests : ProxyHomeEffect
    data class RefreshSubscription(val id: String) : ProxyHomeEffect
    data object RefreshAllSubscriptions : ProxyHomeEffect
    data class PingSubscription(val id: String) : ProxyHomeEffect
    data class ToggleSubscriptionEnabled(val id: String) : ProxyHomeEffect
    data class AddServer(val kind: ProxyHomeServerKind) : ProxyHomeEffect
    data object AddSubscription : ProxyHomeEffect
    data class ImportServers(val source: ProxyHomeImportSource) : ProxyHomeEffect
    data class EditServer(val id: String) : ProxyHomeEffect
    data class DeleteServer(val id: String) : ProxyHomeEffect
    data class ShowServerQr(val id: String) : ProxyHomeEffect
    data class CopyServer(val id: String, val format: ProxyHomeCopyFormat) : ProxyHomeEffect
    data class EditSubscription(val id: String) : ProxyHomeEffect
    data class EditGroup(val groupId: String?) : ProxyHomeEffect
    data class DeleteGroup(val groupId: String) : ProxyHomeEffect
    data class MoveGroup(val groupId: String, val offset: Int) : ProxyHomeEffect
    data class MoveServer(val serverId: String, val offset: Int) : ProxyHomeEffect
    data class OpenStrategyMemberPicker(val serverId: String) : ProxyHomeEffect
    data class SelectStrategyMember(val serverId: String, val memberId: String) : ProxyHomeEffect
    data class SetSort(val mode: ProxyHomeSortMode) : ProxyHomeEffect
    data class RunServerTool(val tool: ProxyHomeServerTool) : ProxyHomeEffect
    data class OpenExternalLink(val url: String) : ProxyHomeEffect
}

fun interface ProxyHomeEffectHandler {
    suspend fun handle(effect: ProxyHomeEffect): Result<Unit>
}

/**
 * Shared Home reducer. It owns only search/group presentation and transient operation feedback;
 * host snapshots remain authoritative for all persisted and runtime data.
 */
class ProxyHomeStore(
    initialInput: ProxyHomeInput = ProxyHomeInput(),
    initialPresentation: ProxyHomePresentation = ProxyHomePresentation(),
    private val scope: CoroutineScope,
    private val effectHandler: ProxyHomeEffectHandler,
) {
    private val input = MutableStateFlow(initialInput)
    private val presentation = MutableStateFlow(
        initialPresentation.copy(
            collapsedSubscriptionGroupIds = initialPresentation.collapsedSubscriptionGroupIds
                .filterTo(linkedSetOf()) { groupId -> initialInput.groups.any { it.id == groupId } },
        ),
    )
    private val transient = MutableStateFlow(ProxyHomeTransientState())
    private val mutableUiState = MutableStateFlow(
        createUiState(initialInput, presentation.value, transient.value),
    )

    /** State publication is synchronous so a gesture always observes the latest host snapshot. */
    val uiState: StateFlow<ProxyHomeUiState> = mutableUiState.asStateFlow()

    /** Host bridge only. Call from a collector/effect, never directly during composition. */
    fun updateInput(value: ProxyHomeInput) {
        val oldValue = input.value
        if (oldValue.statusMessage != value.statusMessage) {
            transient.update {
                it.copy(
                    localError = null,
                    localErrorMessage = null,
                    dismissedSourceMessage = null,
                    rejectedAction = null,
                )
            }
        }
        val currentPresentation = presentation.value
        val validGroupIds = value.groups.mapTo(hashSetOf()) { it.id }
        val prunedCollapsed = currentPresentation.collapsedSubscriptionGroupIds.filterTo(linkedSetOf()) { it in validGroupIds }
        val nextSelectedGroupId = if (currentPresentation.selectedGroupId != null && currentPresentation.selectedGroupId !in validGroupIds) {
            resolveSelectedGroupId(
                source = value,
                requestedGroupId = null,
                selectedServerId = value.selectedServerId,
            )
        } else {
            currentPresentation.selectedGroupId
        }
        if (nextSelectedGroupId != currentPresentation.selectedGroupId ||
            prunedCollapsed != currentPresentation.collapsedSubscriptionGroupIds
        ) {
            presentation.value = currentPresentation.copy(
                selectedGroupId = nextSelectedGroupId,
                collapsedSubscriptionGroupIds = prunedCollapsed,
            )
        }
        input.value = value
        refreshUiState()
    }

    /** Dispatches one gesture. Platform work is invoked at most once through the effect handler. */
    fun dispatch(action: ProxyHomeAction) {
        when (action) {
            is ProxyHomeAction.SelectGroup -> selectGroup(action.groupId)
            is ProxyHomeAction.SetSearchQuery -> updatePresentation { current ->
                reduceProxyHomePresentation(current, action)
            }
            is ProxyHomeAction.SetSearchVisible -> {
                updatePresentation { current ->
                    reduceProxyHomePresentation(current, action, searchEnabled = input.value.searchEnabled)
                }
            }
            is ProxyHomeAction.ToggleSubscriptionExpanded -> toggleSubscriptionExpanded(action.groupId)
            ProxyHomeAction.DismissMessage -> dismissMessage()
            else -> dispatchEffect(action)
        }
    }

    private fun selectGroup(groupId: String) {
        val currentInput = input.value
        if (currentInput.groups.none { it.id == groupId }) {
            updateTransient { it.copy(rejectedAction = null) }
            return
        }
        updatePresentation { current ->
            reduceProxyHomePresentation(current, ProxyHomeAction.SelectGroup(groupId))
        }
    }

    private fun toggleSubscriptionExpanded(groupId: String) {
        if (input.value.groups.none { it.id == groupId }) return
        updatePresentation { current ->
            val nextCollapsed = if (groupId in current.collapsedSubscriptionGroupIds) {
                current.collapsedSubscriptionGroupIds - groupId
            } else {
                current.collapsedSubscriptionGroupIds + groupId
            }
            current.copy(collapsedSubscriptionGroupIds = nextCollapsed)
        }
    }

    private fun dismissMessage() {
        val sourceMessage = input.value.statusMessage
        updateTransient {
            it.copy(
                localError = null,
                localErrorMessage = null,
                rejectedAction = null,
                dismissedSourceMessage = sourceMessage ?: it.dismissedSourceMessage,
            )
        }
    }

    private fun dispatchEffect(action: ProxyHomeAction) {
        val actionId = action.actionId()
        // Mirror the UI's busy gate at the store boundary. Tunnel toggles remain reentrant so a
        // second Power intent can reverse an in-flight reconnect, as the Desktop host supports.
        if (actionId != ProxyHomeActionId.ToggleTunnel && actionId in transient.value.actionCounts) return
        if (actionId !in input.value.availableActions) {
            updateTransient {
                it.copy(
                    localError = ProxyHomeLocalError.Unavailable,
                    localErrorMessage = null,
                    rejectedAction = actionId,
                )
            }
            return
        }
        if (!action.isAvailableVariant(input.value)) {
            updateTransient {
                it.copy(
                    localError = ProxyHomeLocalError.VariantUnavailable,
                    localErrorMessage = null,
                    rejectedAction = actionId,
                )
            }
            return
        }

        val currentUi = uiState.value
        val effect = action.toEffect(currentUi)
        updateTransient {
            it.withActionStarted(actionId).copy(
                localError = null,
                localErrorMessage = null,
                rejectedAction = null,
            )
        }

        scope.launch {
            try {
                val result = effectHandler.handle(effect)
                result.exceptionOrNull()?.let { failure ->
                    val platformMessage = failure.message?.takeIf(String::isNotBlank)
                    updateTransient {
                        it.copy(
                            localError = if (platformMessage == null) ProxyHomeLocalError.OperationFailed else null,
                            localErrorMessage = platformMessage,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                val platformMessage = failure.message?.takeIf(String::isNotBlank)
                updateTransient {
                    it.copy(
                        localError = if (platformMessage == null) ProxyHomeLocalError.OperationFailed else null,
                        localErrorMessage = platformMessage,
                    )
                }
            } finally {
                updateTransient { it.withActionFinished(actionId) }
            }
        }
    }

    private fun updatePresentation(transform: (ProxyHomePresentation) -> ProxyHomePresentation) {
        presentation.update(transform)
        refreshUiState()
    }

    private fun updateTransient(transform: (ProxyHomeTransientState) -> ProxyHomeTransientState) {
        transient.update(transform)
        refreshUiState()
    }

    private fun refreshUiState() {
        mutableUiState.value = createUiState(input.value, presentation.value, transient.value)
    }

    private fun createUiState(
        source: ProxyHomeInput,
        local: ProxyHomePresentation,
        operations: ProxyHomeTransientState,
    ): ProxyHomeUiState {
        val selectedServerId = source.selectedServerId
        val selectedGroupId = resolveSelectedGroupId(
            source = source,
            requestedGroupId = local.selectedGroupId,
            selectedServerId = selectedServerId,
        )
        val visibleQuery = local.searchQuery.trim()
        val isSearchActive = source.searchEnabled && visibleQuery.isNotEmpty()

        val filteredServers = if (isSearchActive) {
            source.servers.filter { server ->
                (server.searchText ?: "${server.title} ${server.address} ${server.protocol}")
                    .contains(visibleQuery, ignoreCase = true)
            }
        } else {
            source.servers
        }

        val sortedServers = sortServers(filteredServers, source.sortMode)
        val processedServers = sortedServers.map { server ->
            server.copy(selected = server.id == selectedServerId)
        }

        val groupBuckets = HashMap<String, ArrayList<ProxyServerSummary>>(source.groups.size)
        val serverIdToExplicitGroupIds = HashMap<String, MutableList<String>>()
        val legacyGroupIdToGroupIds = HashMap<String, MutableList<String>>()

        for (group in source.groups) {
            groupBuckets[group.id] = ArrayList()
            val serverIds = group.serverIds
            if (serverIds != null) {
                for (serverId in serverIds) {
                    serverIdToExplicitGroupIds.getOrPut(serverId) { ArrayList() }.add(group.id)
                }
            } else {
                legacyGroupIdToGroupIds.getOrPut(group.id) { ArrayList() }.add(group.id)
            }
        }

        for (server in processedServers) {
            val explicitGroups = serverIdToExplicitGroupIds[server.id]
            if (explicitGroups != null) {
                for (groupId in explicitGroups) {
                    groupBuckets[groupId]?.add(server)
                }
            }
            val legacyGroups = server.groupId?.let { legacyGroupIdToGroupIds[it] }
            if (legacyGroups != null) {
                for (groupId in legacyGroups) {
                    groupBuckets[groupId]?.add(server)
                }
            }
        }

        val pages = source.groups.map { group ->
            ProxyHomePageUiState(
                group = group,
                servers = groupBuckets[group.id] ?: emptyList(),
                subscription = group.subscription,
                subscriptionExpanded = group.id !in local.collapsedSubscriptionGroupIds,
            )
        }

        val selectedPage = pages.firstOrNull { it.group.id == selectedGroupId }
        val projectionServers = selectedPage?.servers ?: if (source.groups.isEmpty()) {
            processedServers
        } else {
            emptyList()
        }
        val projectionSubscription = selectedPage?.subscription

        val effectiveStatusMessage = when {
            operations.localErrorMessage != null -> operations.localErrorMessage
            operations.localError != null -> null
            source.statusMessage == operations.dismissedSourceMessage -> null
            else -> source.statusMessage
        }
        val effectiveStatusError = operations.localError != null || operations.localErrorMessage != null ||
            (source.statusMessage != operations.dismissedSourceMessage && source.isStatusError)

        val selectedServer = source.servers.firstOrNull { it.id == selectedServerId }
        val title = when {
            selectedServerId == source.selectedServerId && source.selectedServerTitle.isNotBlank() -> source.selectedServerTitle
            selectedServer != null -> selectedServer.title
            else -> source.selectedServerTitle
        }

        val normalizedDisplayOptions = source.displayOptions.copy(
            requestedColumns = source.displayOptions.requestedColumns.coerceIn(1, 3),
        )

        return ProxyHomeUiState(
            tunnelSnapshot = source.tunnelSnapshot,
            selectedServerId = selectedServerId,
            selectedServer = selectedServer?.copy(selected = true),
            selectedServerTitle = title,
            activeProfileName = source.activeProfileName,
            canToggleTunnel = source.canToggleTunnel,
            tunnelBusy = source.tunnelBusy,
            groups = source.groups,
            selectedGroupId = selectedGroupId,
            servers = projectionServers,
            subscription = projectionSubscription,
            pages = pages,
            isTestingLatency = source.isTestingLatency,
            searchQuery = local.searchQuery,
            isSearchVisible = source.searchEnabled && local.isSearchVisible,
            searchEnabled = source.searchEnabled,
            subscriptionSwipeEnabled = source.subscriptionSwipeEnabled,
            displayOptions = normalizedDisplayOptions,
            sortMode = source.sortMode,
            runtimeOutboundMetric = source.runtimeOutboundMetric,
            statusMessage = effectiveStatusMessage,
            isStatusError = effectiveStatusError,
            localError = operations.localError,
            availableActions = source.availableActions,
            availableImportSources = source.availableImportSources,
            availableCopyFormats = source.availableCopyFormats,
            availableServerKinds = source.availableServerKinds,
            availableServerTools = source.availableServerTools,
            busyActions = operations.actionCounts.keys,
            rejectedAction = operations.rejectedAction,
        )
    }

    private fun sortServers(
        servers: List<ProxyServerSummary>,
        sortMode: ProxyHomeSortMode,
    ): List<ProxyServerSummary> = when (sortMode) {
        ProxyHomeSortMode.Default -> servers
        ProxyHomeSortMode.Name -> servers.sortedWith { a, b ->
            val keyA = a.sortKey ?: a.title
            val keyB = b.sortKey ?: b.title
            keyA.compareTo(keyB, ignoreCase = true)
        }
        ProxyHomeSortMode.Latency -> servers.sortedWith { a, b ->
            val aGroup = when {
                a.latencyMs != null -> 0
                a.latencyTesting -> 1
                else -> 2
            }
            val bGroup = when {
                b.latencyMs != null -> 0
                b.latencyTesting -> 1
                else -> 2
            }
            compareValues(aGroup, bGroup)
                .takeIf { it != 0 }
                ?: compareValues(a.latencyMs, b.latencyMs)
                    .takeIf { it != 0 }
                ?: (a.sortKey ?: a.title).compareTo(b.sortKey ?: b.title, ignoreCase = true)
        }
    }

    private fun resolveSelectedGroupId(
        source: ProxyHomeInput,
        requestedGroupId: String?,
        selectedServerId: String?,
    ): String? {
        if (source.groups.isEmpty()) return null
        if (requestedGroupId != null && source.groups.any { it.id == requestedGroupId }) return requestedGroupId

        val selectedServer = source.servers.firstOrNull { it.id == selectedServerId }
        val serverGroup = selectedServer?.groupId?.let { groupId ->
            source.groups.firstOrNull { it.id == groupId && it.contains(selectedServer) }
        }
        if (serverGroup != null) return serverGroup.id

        val containingGroup = selectedServer?.let { server ->
            source.groups.firstOrNull { it.kind != ProxyHomeGroupKind.All && it.contains(server) }
                ?: source.groups.firstOrNull { it.contains(server) }
        }
        return containingGroup?.id ?: source.groups.first().id
    }

    private fun ProxyGroupSummary.contains(server: ProxyServerSummary): Boolean =
        serverIds?.contains(server.id) ?: (server.groupId == id)

    private fun ProxyHomeAction.actionId(): ProxyHomeActionId = when (this) {
        is ProxyHomeAction.SelectGroup,
        is ProxyHomeAction.SetSearchQuery,
        is ProxyHomeAction.SetSearchVisible,
        is ProxyHomeAction.ToggleSubscriptionExpanded,
        ProxyHomeAction.DismissMessage -> error("Local actions do not have a platform capability")
        ProxyHomeAction.ToggleTunnel -> ProxyHomeActionId.ToggleTunnel
        is ProxyHomeAction.SelectServer -> ProxyHomeActionId.SelectServer
        is ProxyHomeAction.TestServer -> ProxyHomeActionId.TestServer
        is ProxyHomeAction.TestVisibleServers, ProxyHomeAction.TestAllVisibleServers -> ProxyHomeActionId.TestVisibleServers
        is ProxyHomeAction.TestGroup -> ProxyHomeActionId.TestGroup
        ProxyHomeAction.CancelLatencyTests -> ProxyHomeActionId.CancelLatencyTests
        is ProxyHomeAction.RefreshSubscription -> ProxyHomeActionId.RefreshSubscription
        ProxyHomeAction.RefreshAllSubscriptions -> ProxyHomeActionId.RefreshAllSubscriptions
        is ProxyHomeAction.PingSubscription -> ProxyHomeActionId.PingSubscription
        is ProxyHomeAction.ToggleSubscriptionEnabled -> ProxyHomeActionId.ToggleSubscriptionEnabled
        is ProxyHomeAction.AddServer -> ProxyHomeActionId.AddServer
        ProxyHomeAction.AddSubscription -> ProxyHomeActionId.AddSubscription
        is ProxyHomeAction.ImportServers -> ProxyHomeActionId.ImportServers
        is ProxyHomeAction.EditServer -> ProxyHomeActionId.EditServer
        is ProxyHomeAction.DeleteServer -> ProxyHomeActionId.DeleteServer
        is ProxyHomeAction.ShowServerQr -> ProxyHomeActionId.ShowServerQr
        is ProxyHomeAction.CopyServer -> ProxyHomeActionId.CopyServer
        is ProxyHomeAction.EditSubscription -> ProxyHomeActionId.EditSubscription
        is ProxyHomeAction.EditGroup -> ProxyHomeActionId.EditGroup
        is ProxyHomeAction.DeleteGroup -> ProxyHomeActionId.DeleteGroup
        is ProxyHomeAction.MoveGroup -> ProxyHomeActionId.MoveGroup
        is ProxyHomeAction.MoveServer -> ProxyHomeActionId.MoveServer
        is ProxyHomeAction.OpenStrategyMemberPicker -> ProxyHomeActionId.OpenStrategyMemberPicker
        is ProxyHomeAction.SelectStrategyMember -> ProxyHomeActionId.SelectStrategyMember
        is ProxyHomeAction.SetSort -> ProxyHomeActionId.SetSort
        is ProxyHomeAction.RunServerTool -> ProxyHomeActionId.RunServerTool
        is ProxyHomeAction.OpenExternalLink -> ProxyHomeActionId.OpenExternalLink
    }

    private fun ProxyHomeAction.isAvailableVariant(source: ProxyHomeInput): Boolean = when (this) {
        is ProxyHomeAction.AddServer -> this.kind in source.availableServerKinds
        is ProxyHomeAction.ImportServers -> this.source in source.availableImportSources
        is ProxyHomeAction.CopyServer -> this.format in source.availableCopyFormats &&
            source.servers.firstOrNull { it.id == this.id }?.availableCopyFormats?.contains(this.format) == true
        is ProxyHomeAction.RunServerTool -> this.tool in source.availableServerTools
        is ProxyHomeAction.OpenStrategyMemberPicker -> source.servers.any { it.id == this.serverId && it.isStrategyGroup }
        is ProxyHomeAction.SelectStrategyMember -> source.servers.any { it.id == this.serverId && it.isStrategyGroup }
        else -> true
    }

    private fun ProxyHomeAction.toEffect(state: ProxyHomeUiState): ProxyHomeEffect = when (this) {
        is ProxyHomeAction.SelectGroup,
        is ProxyHomeAction.SetSearchQuery,
        is ProxyHomeAction.SetSearchVisible,
        is ProxyHomeAction.ToggleSubscriptionExpanded,
        ProxyHomeAction.DismissMessage -> error("Local actions do not produce a platform effect")
        ProxyHomeAction.ToggleTunnel -> ProxyHomeEffect.ToggleTunnel
        is ProxyHomeAction.SelectServer -> ProxyHomeEffect.SelectServer(serverId)
        is ProxyHomeAction.TestServer -> ProxyHomeEffect.TestServer(serverId)
        is ProxyHomeAction.TestVisibleServers -> ProxyHomeEffect.TestVisibleServers(serverIds.distinct())
        ProxyHomeAction.TestAllVisibleServers -> ProxyHomeEffect.TestVisibleServers(state.servers.map { it.id })
        is ProxyHomeAction.TestGroup -> ProxyHomeEffect.TestGroup(groupId)
        ProxyHomeAction.CancelLatencyTests -> ProxyHomeEffect.CancelLatencyTests
        is ProxyHomeAction.RefreshSubscription -> ProxyHomeEffect.RefreshSubscription(id)
        ProxyHomeAction.RefreshAllSubscriptions -> ProxyHomeEffect.RefreshAllSubscriptions
        is ProxyHomeAction.PingSubscription -> ProxyHomeEffect.PingSubscription(id)
        is ProxyHomeAction.ToggleSubscriptionEnabled -> ProxyHomeEffect.ToggleSubscriptionEnabled(id)
        is ProxyHomeAction.AddServer -> ProxyHomeEffect.AddServer(kind)
        ProxyHomeAction.AddSubscription -> ProxyHomeEffect.AddSubscription
        is ProxyHomeAction.ImportServers -> ProxyHomeEffect.ImportServers(source)
        is ProxyHomeAction.EditServer -> ProxyHomeEffect.EditServer(id)
        is ProxyHomeAction.DeleteServer -> ProxyHomeEffect.DeleteServer(id)
        is ProxyHomeAction.ShowServerQr -> ProxyHomeEffect.ShowServerQr(id)
        is ProxyHomeAction.CopyServer -> ProxyHomeEffect.CopyServer(id, format)
        is ProxyHomeAction.EditSubscription -> ProxyHomeEffect.EditSubscription(id)
        is ProxyHomeAction.EditGroup -> ProxyHomeEffect.EditGroup(groupId)
        is ProxyHomeAction.DeleteGroup -> ProxyHomeEffect.DeleteGroup(groupId)
        is ProxyHomeAction.MoveGroup -> ProxyHomeEffect.MoveGroup(groupId, offset)
        is ProxyHomeAction.MoveServer -> ProxyHomeEffect.MoveServer(serverId, offset)
        is ProxyHomeAction.OpenStrategyMemberPicker -> ProxyHomeEffect.OpenStrategyMemberPicker(serverId)
        is ProxyHomeAction.SelectStrategyMember -> ProxyHomeEffect.SelectStrategyMember(serverId, memberId)
        is ProxyHomeAction.SetSort -> ProxyHomeEffect.SetSort(mode)
        is ProxyHomeAction.RunServerTool -> ProxyHomeEffect.RunServerTool(tool)
        is ProxyHomeAction.OpenExternalLink -> ProxyHomeEffect.OpenExternalLink(url)
    }
}

private data class ProxyHomeTransientState(
    val localError: ProxyHomeLocalError? = null,
    val localErrorMessage: String? = null,
    val dismissedSourceMessage: String? = null,
    val rejectedAction: ProxyHomeActionId? = null,
    val actionCounts: Map<ProxyHomeActionId, Int> = emptyMap(),
) {
    fun withActionStarted(id: ProxyHomeActionId) = copy(actionCounts = actionCounts + (id to ((actionCounts[id] ?: 0) + 1)))

    fun withActionFinished(id: ProxyHomeActionId): ProxyHomeTransientState {
        val count = actionCounts[id] ?: return this
        val nextCounts = if (count <= 1) actionCounts - id else actionCounts + (id to count - 1)
        return copy(actionCounts = nextCounts)
    }
}
