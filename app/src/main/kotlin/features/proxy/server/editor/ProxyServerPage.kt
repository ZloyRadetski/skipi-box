// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import app.collectAppState
import app.navigation.ProxyServerEditResult
import app.navigation.Route
import app.navigation.StrategyGroupMemberSelectionResult
import app.skipi.app.server.ProxyServerEditorController
import app.skipi.app.server.ProxyServerEditorSaveDecision
import app.skipi.ui.server.editor.ProxyServerEditorDialogText
import app.skipi.ui.server.editor.rememberProxyServerEditorUiState
import app.skipi.ui.server.editor.rememberSaveableProxyServerEditorDraft
import app.skipi.ui.server.editor.SkipiProxyServerEditorScreen
import features.proxy.server.display.displayName
import features.proxy.server.display.displayNameById
import features.proxy.server.display.displayNameWithGroup
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.proxy.server.model.isCompositeProxyServer
import features.proxy.server.usecase.ProxyServerCopyTextResult
import features.proxy.server.usecase.proxyServerCopyText
import features.proxy.server.validation.rememberProxyServerValidationMessageResolver
import features.subscription.DefaultSubscriptionGroupId
import kotlinx.coroutines.launch
import ui.clipboard.setPlainText
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.skipi.app.store.SharedApplicationAction
import ui.components.DeleteConfirmationDialog
import ui.text.formatTemplate

@Composable
fun ProxyServerPage(
    padding: PaddingValues,
    ps: ProxyServer<*>,
    serverId: Int? = null,
    groupId: Int? = null,
    returnGroupId: Int? = null,
    resultKey: String? = null,
) {
    val isWideScreen = LocalIsWideScreen.current
    val appState by LocalAppStateStore.current.collectAppState()
    val navigator = LocalNavigator.current
    val services = LocalAppServices.current
    val tipNotifier = services.tipNotifier
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val validationMessageOf = rememberProxyServerValidationMessageResolver()
    val copiedMessage = stringResource(R.string.common_copied)
    val unsupportedMessage = stringResource(R.string.common_unsupported)
    val invalidConfigMessage = stringResource(R.string.proxy_server_config_invalid)
    val deletedTemplate = stringResource(R.string.proxy_server_list_deleted)
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val dialogText = ProxyServerEditorDialogText(
        fullValidationTitle = stringResource(R.string.proxy_editor_full_validation_warning_title),
        fullValidationSummary = stringResource(R.string.proxy_editor_full_validation_warning_summary),
        returnEdit = stringResource(R.string.proxy_editor_return_edit),
        continueSave = stringResource(R.string.proxy_editor_continue_save),
        discardTitle = stringResource(R.string.proxy_editor_discard_changes_title),
        discardSummary = stringResource(R.string.proxy_editor_discard_changes_summary),
        discard = stringResource(R.string.proxy_editor_discard),
    )
    val unknownGroupName = stringResource(R.string.common_unknown_group)
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val allGroupsLabel = stringResource(R.string.proxy_editor_strategy_group_all_groups)
    val defaultProxyServerTemplate = stringResource(R.string.routing_default_proxy_server)

    val editorUiState = rememberProxyServerEditorUiState()
    val psEdit = rememberSaveableProxyServerEditorDraft(ps, serverId, resultKey)
    val strategyMemberResultKey = remember(serverId, resultKey) {
        "strategy-group-members-${serverId ?: resultKey ?: "draft"}"
    }
    var strategyGroupMemberIds by rememberSaveable(serverId, resultKey) {
        mutableStateOf((psEdit as? StrategyGroup)?.proxyServerIds.orEmpty())
    }
    if (psEdit is StrategyGroup) {
        LaunchedEffect(navigator, strategyMemberResultKey) {
            navigator.observeResult<StrategyGroupMemberSelectionResult>(strategyMemberResultKey).collect { result ->
                val memberIds = ProxyServerEditorController.normalizeStrategyGroupMemberIds(result.serverIds)
                strategyGroupMemberIds = memberIds
                psEdit.proxyServerIds = memberIds
                navigator.clearResult(strategyMemberResultKey)
            }
        }
    }

    fun hasChanges(): Boolean {
        if (psEdit is StrategyGroup && strategyGroupMemberIds != (ps as? StrategyGroup)?.proxyServerIds.orEmpty()) return true
        return ProxyServerEditorController.hasChanges(ps, psEdit)
    }

    fun saveProxyServer() {
        if (psEdit is StrategyGroup) {
            psEdit.proxyServerIds = strategyGroupMemberIds
        }
        if (resultKey != null) {
            navigator.setResult(
                resultKey,
                ProxyServerEditResult(serverId, psEdit, groupId = groupId, returnGroupId = returnGroupId),
            )
        } else {
            ps.update(psEdit)
            navigator.pop()
        }
    }

    fun deleteProxyServer() {
        if (resultKey != null && serverId != null) {
            navigator.setResult(
                resultKey,
                ProxyServerEditResult(
                    serverId = serverId,
                    server = psEdit,
                    groupId = groupId,
                    returnGroupId = returnGroupId,
                    deleted = true,
                ),
            )
        } else if (serverId != null) {
            val remarks = psEdit.getInfo().remarks.ifBlank { psEdit.getInfo().protocol }
            services.sharedApplicationStore.dispatch(SharedApplicationAction.RemoveProxyServer(serverId))
            scope.launch { tipNotifier.show(deletedTemplate.formatTemplate("name" to remarks)) }
            navigator.pop()
        } else {
            navigator.pop()
        }
    }

    fun requestDelete() {
        if (serverId != null && appState.enableDeletionConfirmation) {
            showDeleteConfirmation = true
        } else {
            deleteProxyServer()
        }
    }

    fun requestSave() {
        when (val decision = ProxyServerEditorController.requestSave(psEdit)) {
            is ProxyServerEditorSaveDecision.Reject -> scope.launch {
                tipNotifier.show(validationMessageOf(decision.issue))
            }
            is ProxyServerEditorSaveDecision.ConfirmFullValidation -> editorUiState.showFullValidationWarning(decision.issues)
            ProxyServerEditorSaveDecision.Save -> saveProxyServer()
        }
    }

    fun requestBack() {
        if (hasChanges()) editorUiState.requestDiscardConfirmation() else navigator.pop()
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = ::requestBack,
    )

    val groupOptions = remember(appState.subscriptionGroups, allGroupsLabel, defaultGroupName) {
        listOf(ProxyServerEditorGroupOption(null, allGroupsLabel)) +
            appState.subscriptionGroups
                .filter { group -> group.enabled || group.builtIn || group.id == DefaultSubscriptionGroupId }
                .map { group -> ProxyServerEditorGroupOption(group.id, group.displayName(defaultGroupName).ifBlank { defaultGroupName }) }
    }
    val memberOptions = remember(appState.proxyServers, appState.subscriptionGroups, serverId, unknownGroupName, defaultGroupName) {
        val groupNames = appState.subscriptionGroups.displayNameById(defaultGroupName)
        appState.proxyServers.filter { server ->
            server.id != serverId && !server.server.isCompositeProxyServer() &&
                (server.server !is Custom || server.server.canBeUsedInGeneratedProxyPlan()) &&
                (server.server !is StrategyGroup || server.server.sourceTrafficConfigId == null)
        }.map { server ->
            ProxyServerEditorMemberOption(
                id = server.id,
                label = server.displayNameWithGroup(defaultProxyServerTemplate, groupNames, unknownGroupName),
            )
        }
    }
    val editorOptions = remember(groupOptions, memberOptions, strategyGroupMemberIds, psEdit, serverId) {
        ProxyServerEditorOptions(
            groupOptions = groupOptions,
            memberOptions = memberOptions,
            strategyGroupSelectedMemberCount = strategyGroupMemberIds.size,
            onOpenStrategyGroupMembers = if (psEdit is StrategyGroup) {
                { navigator.navigateForResult(Route.StrategyGroupMemberSelector(strategyGroupMemberIds, serverId, strategyMemberResultKey), strategyMemberResultKey) }
            } else null,
        )
    }
    val title = psEdit.editorTitle()

    SkipiProxyServerEditorScreen(
        padding = padding,
        serverEdit = psEdit,
        title = title,
        isWideScreen = isWideScreen,
        options = editorOptions,
        hasChanges = ::hasChanges,
        messages = dialogText,
        fullValidationMessages = editorUiState.pendingSaveIssues.map(validationMessageOf),
        showDiscardConfirmation = editorUiState.showDiscardConfirmation,
        showFullValidationWarning = editorUiState.showFullValidationWarning,
        onRequestBack = navigator::pop,
        onRequestDiscardConfirmation = editorUiState::requestDiscardConfirmation,
        onConfirmDiscard = {
            editorUiState.dismissDiscardConfirmation()
            navigator.pop()
        },
        onDismissDiscard = editorUiState::dismissDiscardConfirmation,
        onCopy = {
            scope.launch {
                val basicIssue = psEdit.validateBasic().firstOrNull()
                if (basicIssue != null) {
                    tipNotifier.show(validationMessageOf(basicIssue))
                    return@launch
                }
                when (val result = psEdit.proxyServerCopyText(context, appState, serverId, groupId)) {
                    is ProxyServerCopyTextResult.Success -> {
                        clipboard.setPlainText(result.text)
                        tipNotifier.show(copiedMessage)
                    }
                    ProxyServerCopyTextResult.Unsupported -> tipNotifier.show(unsupportedMessage)
                    ProxyServerCopyTextResult.InvalidConfig -> tipNotifier.show(invalidConfigMessage)
                }
            }
        },
        onDelete = if (psEdit is StrategyGroup) ::requestDelete else null,
        onSave = ::requestSave,
        onDismissFullValidation = editorUiState::dismissFullValidationWarning,
        onContinueFullValidation = {
            editorUiState.dismissFullValidationWarning()
            saveProxyServer()
        },
        customEditor = { custom, contentPadding -> CustomProxyServerEditor(custom, contentPadding = contentPadding) },
    )

    if (showDeleteConfirmation) {
        DeleteConfirmationDialog(
            show = true,
            title = stringResource(R.string.deletion_confirmation_delete_proxy_server),
            onDismissRequest = { showDeleteConfirmation = false },
            onConfirm = {
                showDeleteConfirmation = false
                deleteProxyServer()
            },
        )
    }
}
