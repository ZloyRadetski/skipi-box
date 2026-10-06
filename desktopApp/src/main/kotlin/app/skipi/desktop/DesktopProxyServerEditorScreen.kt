// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import app.skipi.app.proxy.ProxyServerTextCopyFormat
import app.skipi.app.proxy.ProxyServerTextCopyResult
import app.skipi.app.proxy.copyProxyServerText
import app.skipi.app.server.ProxyServerEditorController
import app.skipi.app.server.ProxyServerEditorSaveDecision
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_copied
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.common_unknown_group
import app.skipi.ui.resources.common_unsupported
import app.skipi.ui.resources.proxy_editor_custom_format_json
import app.skipi.ui.resources.proxy_editor_custom_json_invalid
import app.skipi.ui.resources.proxy_editor_discard
import app.skipi.ui.resources.proxy_editor_discard_changes_summary
import app.skipi.ui.resources.proxy_editor_discard_changes_title
import app.skipi.ui.resources.proxy_editor_continue_save
import app.skipi.ui.resources.proxy_editor_full_validation_warning_summary
import app.skipi.ui.resources.proxy_editor_full_validation_warning_title
import app.skipi.ui.resources.proxy_editor_return_edit
import app.skipi.ui.resources.proxy_editor_strategy_group_all_groups
import app.skipi.ui.resources.proxy_editor_strategy_group_no_servers
import app.skipi.ui.resources.proxy_editor_strategy_group_select_servers
import app.skipi.ui.resources.proxy_editor_strategy_group_selected_servers_summary
import app.skipi.ui.resources.proxy_server_config_invalid
import app.skipi.ui.resources.routing_default_proxy_server
import app.skipi.ui.server.editor.ProxyServerEditorDialogText
import app.skipi.ui.server.editor.ProxyServerEditorGroupOption
import app.skipi.ui.server.editor.ProxyServerEditorMemberOption
import app.skipi.ui.server.editor.ProxyServerEditorOptions
import app.skipi.ui.server.editor.ServerPickerSubscriptionSummary
import app.skipi.ui.server.editor.SkipiCustomProxyServerEditorPresentation
import app.skipi.ui.server.editor.SkipiProxyServerEditorScreen
import app.skipi.ui.server.editor.SkipiStrategyGroupMemberSelectorScreen
import app.skipi.ui.server.editor.StrategyMemberSelectorGroup
import app.skipi.ui.server.editor.StrategyMemberSelectorItem
import app.skipi.ui.server.editor.editorTitle
import app.skipi.ui.server.editor.rememberProxyServerEditorUiState
import app.skipi.ui.server.editor.rememberSaveableProxyServerEditorDraft
import app.skipi.ui.server.validation.rememberProxyServerValidationMessageResolver
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.proxy.server.model.extractLeadingCountryFlagOrNull
import features.proxy.server.model.formatCustomXrayConfigJson
import features.proxy.server.model.getTransportDisplay
import features.proxy.server.model.stripLeadingCountryFlag
import features.proxy.server.presentation.ProxyServerPresentationFormatter
import features.proxy.server.presentation.ProxyServerPresentationNode
import org.jetbrains.compose.resources.stringResource
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch

/** Desktop host adapter for the shared proxy-server editor presentation and lifecycle. */
@Composable
internal fun DesktopProxyServerEditorHost(
    contentPadding: PaddingValues,
    serverId: Int,
    original: ProxyServer<*>,
    decodedServers: List<Pair<DesktopStoredProxyServer, ProxyServer<*>?>>,
    groupCatalog: DesktopProxyGroupCatalog,
    presentationNodes: List<ProxyServerPresentationNode>,
    presentationFormatter: ProxyServerPresentationFormatter,
    exportFullJson: suspend (ProxyServer<*>) -> String,
    onSave: (Int, ProxyServer<*>) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val serverEdit = rememberSaveableProxyServerEditorDraft(original, serverId, resultKey = null)
    val editorUiState = rememberProxyServerEditorUiState(serverId, original)
    val validationMessageOf = rememberProxyServerValidationMessageResolver()
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(Res.string.common_copied)
    val unsupportedMessage = stringResource(Res.string.common_unsupported)
    val invalidConfigMessage = stringResource(Res.string.proxy_server_config_invalid)
    val invalidJsonMessage = stringResource(Res.string.proxy_editor_custom_json_invalid)
    var strategyGroupMemberIds by remember(serverId, serverEdit) {
        mutableStateOf((serverEdit as? StrategyGroup)?.proxyServerIds.orEmpty())
    }
    var selectingMembers by remember(serverId) { mutableStateOf(false) }
    val unknownGroupName = stringResource(Res.string.common_unknown_group)
    val allGroupsName = stringResource(Res.string.proxy_editor_strategy_group_all_groups)
    val noServersMessage = stringResource(Res.string.proxy_editor_strategy_group_no_servers)
    val defaultProxyServerTemplate = stringResource(Res.string.routing_default_proxy_server)

    val groupOptions = remember(groupCatalog, allGroupsName) {
        buildList {
            add(ProxyServerEditorGroupOption(null, allGroupsName))
            groupCatalog.groups.filter { it.kind == DesktopProxyGroupKind.Subscription }.forEach { group ->
                group.id.removePrefix("subscription:").toIntOrNull()?.let { id -> add(ProxyServerEditorGroupOption(id, group.title)) }
            }
        }
    }
    val memberOptions = remember(decodedServers, presentationNodes, presentationFormatter, serverId, unknownGroupName) {
        decodedServers.mapNotNull { (stored, candidate) ->
            val member = candidate ?: return@mapNotNull null
            if (stored.id == serverId || member is ChainProxy || member is Custom && !member.canBeUsedInGeneratedProxyPlan()) return@mapNotNull null
            if (member is StrategyGroup && member.sourceTrafficConfigId != null) return@mapNotNull null
            val node = presentationNodes.firstOrNull { it.id == stored.id }
            ProxyServerEditorMemberOption(
                id = stored.id,
                label = node?.let { presentationFormatter.displayOf(it, presentationNodes).summary }
                    ?: member.getInfo().remarks.ifBlank { unknownGroupName },
            )
        }
    }
    val editorOptions = remember(groupOptions, memberOptions, strategyGroupMemberIds, serverEdit, selectingMembers) {
        ProxyServerEditorOptions(
            groupOptions = groupOptions,
            memberOptions = memberOptions,
            strategyGroupSelectedMemberCount = strategyGroupMemberIds.size,
            onOpenStrategyGroupMembers = if (serverEdit is StrategyGroup) ({ selectingMembers = true }) else null,
        )
    }

    fun saveDraft() {
        if (serverEdit is StrategyGroup) serverEdit.proxyServerIds = strategyGroupMemberIds
        when (val decision = ProxyServerEditorController.requestSave(serverEdit)) {
            is ProxyServerEditorSaveDecision.Reject -> onMessage(validationMessageOf(decision.issue))
            is ProxyServerEditorSaveDecision.ConfirmFullValidation -> editorUiState.showFullValidationWarning(decision.issues)
            ProxyServerEditorSaveDecision.Save -> {
                onSave(serverId, serverEdit)
                editorUiState.dismissDiscardConfirmation()
                onDismiss()
            }
        }
    }

    fun continueSaveAfterWarning() {
        if (serverEdit is StrategyGroup) serverEdit.proxyServerIds = strategyGroupMemberIds
        onSave(serverId, serverEdit)
        editorUiState.dismissFullValidationWarning()
        onDismiss()
    }

    fun copyDraft() {
        val basicIssue = serverEdit.validateBasic().firstOrNull()
        if (basicIssue != null) {
            onMessage(validationMessageOf(basicIssue))
            return
        }
        scope.launch {
            when (val result = copyProxyServerText(serverEdit, ProxyServerTextCopyFormat.Default) {
                exportFullJson(serverEdit)
            }) {
                is ProxyServerTextCopyResult.Success -> runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(result.text), null)
                }.onSuccess { onMessage(copiedMessage) }.onFailure { onMessage(it.message.orEmpty()) }
                ProxyServerTextCopyResult.Unsupported -> onMessage(unsupportedMessage)
                ProxyServerTextCopyResult.InvalidConfig -> onMessage(invalidConfigMessage)
            }
        }
    }

    if (selectingMembers && serverEdit is StrategyGroup) {
        val groups = remember(groupCatalog, decodedServers, serverId, defaultProxyServerTemplate) {
            groupCatalog.groups.filter { it.kind != DesktopProxyGroupKind.All }.map { group ->
                val items = group.serverIds.mapNotNull { id ->
                    if (id == serverId) return@mapNotNull null
                    val server = decodedServers.firstOrNull { it.first.id == id }?.second ?: return@mapNotNull null
                    if (server is ChainProxy || server is Custom && !server.canBeUsedInGeneratedProxyPlan()) return@mapNotNull null
                    if (server is StrategyGroup && server.sourceTrafficConfigId != null) return@mapNotNull null
                    val remarks = server.getInfo().remarks
                    val flag = remarks.extractLeadingCountryFlagOrNull()
                    val name = remarks.stripLeadingCountryFlag().ifBlank { defaultProxyServerTemplate.replace("{id}", id.toString()) }
                    val transport = server.getTransportDisplay()
                    val protocol = server.getInfo().protocol
                    StrategyMemberSelectorItem(id, name, flag, if (!transport.isNullOrBlank()) "$protocol • $transport" else protocol)
                }
                StrategyMemberSelectorGroup(
                    key = group.id,
                    title = group.title,
                    summary = ServerPickerSubscriptionSummary(),
                    items = items,
                )
            }
        }
        SkipiStrategyGroupMemberSelectorScreen(
            padding = contentPadding,
            groups = groups,
            selectedServerIds = strategyGroupMemberIds,
            title = stringResource(Res.string.proxy_editor_strategy_group_select_servers),
            isWideScreen = true,
            selectedSummary = { count -> stringResource(Res.string.proxy_editor_strategy_group_selected_servers_summary, count) },
            emptyGroupMessage = noServersMessage,
            saveLabel = stringResource(Res.string.common_save),
            onBack = { selectingMembers = false },
            onSave = { ids ->
                strategyGroupMemberIds = ProxyServerEditorController.normalizeStrategyGroupMemberIds(ids)
                serverEdit.proxyServerIds = strategyGroupMemberIds
                selectingMembers = false
            },
        )
        return
    }

    val editorMessages = ProxyServerEditorDialogText(
        fullValidationTitle = stringResource(Res.string.proxy_editor_full_validation_warning_title),
        fullValidationSummary = stringResource(Res.string.proxy_editor_full_validation_warning_summary),
        returnEdit = stringResource(Res.string.proxy_editor_return_edit),
        continueSave = stringResource(Res.string.proxy_editor_continue_save),
        discardTitle = stringResource(Res.string.proxy_editor_discard_changes_title),
        discardSummary = stringResource(Res.string.proxy_editor_discard_changes_summary),
        discard = stringResource(Res.string.proxy_editor_discard),
    )
    SkipiProxyServerEditorScreen(
        padding = contentPadding,
        serverEdit = serverEdit,
        title = serverEdit.editorTitle(),
        isWideScreen = true,
        options = editorOptions,
        hasChanges = { ProxyServerEditorController.hasChanges(original, serverEdit) },
        messages = editorMessages,
        fullValidationMessages = editorUiState.pendingSaveIssues.map(validationMessageOf),
        showDiscardConfirmation = editorUiState.showDiscardConfirmation,
        showFullValidationWarning = editorUiState.showFullValidationWarning,
        onRequestBack = onDismiss,
        onRequestDiscardConfirmation = editorUiState::requestDiscardConfirmation,
        onConfirmDiscard = {
            editorUiState.dismissDiscardConfirmation()
            onDismiss()
        },
        onDismissDiscard = editorUiState::dismissDiscardConfirmation,
        onCopy = ::copyDraft,
        onSave = ::saveDraft,
        onDismissFullValidation = editorUiState::dismissFullValidationWarning,
        onContinueFullValidation = ::continueSaveAfterWarning,
        customEditor = { custom, editorPadding ->
            SkipiCustomProxyServerEditorPresentation(
                draftIdentity = custom,
                remarks = custom.remarks,
                onRemarksChange = { custom.remarks = it },
                overrideInboundAndDns = custom.overrideInboundAndDns,
                onOverrideChange = { custom.overrideInboundAndDns = it },
                contentPadding = editorPadding,
                formatJsonContentDescription = stringResource(Res.string.proxy_editor_custom_format_json),
                formatButtonBackground = MaterialTheme.colorScheme.surfaceVariant,
                formatButtonTint = MaterialTheme.colorScheme.onSurfaceVariant,
                onFormatJson = {
                    runCatching { formatCustomXrayConfigJson(custom.configJson) }
                        .onSuccess { custom.configJson = it }
                        .onFailure { onMessage(invalidJsonMessage) }
                },
                jsonEditor = { jsonModifier ->
                    OutlinedTextField(
                        value = custom.configJson,
                        onValueChange = { custom.configJson = it },
                        modifier = jsonModifier,
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = false,
                    )
                },
            )
        },
    )
}
