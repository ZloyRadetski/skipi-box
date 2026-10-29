// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.proxy_editor_custom_json
import app.skipi.ui.server.editor.SkipiJsonCodeEditor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.DeleteConfirmationDialog
import app.skipi.ui.server.editor.ProxyServerEditorGroupOption
import app.skipi.ui.server.editor.ProxyServerEditorMemberOption
import app.skipi.app.proxy.ProxyServerTextCopyFormat
import app.skipi.app.proxy.ProxyServerTextCopyResult
import app.skipi.app.proxy.copyProxyServerText
import app.skipi.app.server.ProxyServerEditorController
import app.skipi.app.server.ProxyServerEditorSaveDecision
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_copied
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.common_unsupported
import app.skipi.ui.resources.deletion_confirmation_delete_proxy_server
import app.skipi.ui.resources.proxy_editor_custom_format_json
import app.skipi.ui.resources.proxy_editor_custom_json_invalid
import app.skipi.ui.resources.proxy_editor_discard
import app.skipi.ui.resources.proxy_editor_discard_changes_summary
import app.skipi.ui.resources.proxy_editor_discard_changes_title
import app.skipi.ui.resources.proxy_editor_continue_save
import app.skipi.ui.resources.proxy_editor_full_validation_warning_summary
import app.skipi.ui.resources.proxy_editor_full_validation_warning_title
import app.skipi.ui.resources.proxy_editor_return_edit
import app.skipi.ui.resources.proxy_editor_strategy_group_no_servers
import app.skipi.ui.resources.proxy_editor_strategy_group_select_servers
import app.skipi.ui.resources.proxy_editor_strategy_group_selected_servers_summary
import app.skipi.ui.resources.proxy_server_config_invalid
import app.skipi.ui.server.editor.ProxyServerEditorDialogText
import app.skipi.ui.server.editor.ProxyServerEditorOptions
import app.skipi.ui.server.editor.SkipiCustomProxyServerEditorPresentation
import app.skipi.ui.server.editor.SkipiProxyServerEditorScreen
import app.skipi.ui.server.editor.SkipiStrategyGroupMemberSelectorScreen
import app.skipi.ui.server.editor.StrategyMemberSelectorGroup
import app.skipi.ui.server.editor.editorTitle
import app.skipi.ui.server.editor.rememberProxyServerEditorUiState
import app.skipi.ui.server.editor.rememberSaveableProxyServerEditorDraft
import app.skipi.ui.server.validation.rememberProxyServerValidationMessageResolver
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.formatCustomXrayConfigJson
import org.jetbrains.compose.resources.stringResource
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch

/** Desktop host adapter for the shared proxy-server editor presentation and lifecycle. */
@Composable
internal fun DesktopProxyServerEditorHost(
    contentPadding: PaddingValues,
    editResult: ProxyServerEditResult,
    groupOptions: List<ProxyServerEditorGroupOption>,
    memberOptions: List<ProxyServerEditorMemberOption>,
    memberGroups: List<StrategyMemberSelectorGroup>,
    confirmDeletion: Boolean,
    exportFullJson: suspend (ProxyServerEditResult) -> String,
    copyTextToClipboard: (String) -> Unit = ::copyTextToDesktopClipboard,
    statusMessage: String? = null,
    onSave: (ProxyServerEditResult) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val serverId = editResult.serverId
    val original = editResult.server
    val serverEdit = rememberSaveableProxyServerEditorDraft(original, serverId, resultKey = null)
    val editorUiState = rememberProxyServerEditorUiState(serverId, original)
    val validationMessageOf = rememberProxyServerValidationMessageResolver()
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(Res.string.common_copied)
    val unsupportedMessage = stringResource(Res.string.common_unsupported)
    val invalidConfigMessage = stringResource(Res.string.proxy_server_config_invalid)
    val invalidJsonMessage = stringResource(Res.string.proxy_editor_custom_json_invalid)
    val deleteTitle = stringResource(Res.string.deletion_confirmation_delete_proxy_server)
    var strategyGroupMemberIds by remember(serverId, serverEdit) {
        mutableStateOf((serverEdit as? StrategyGroup)?.proxyServerIds.orEmpty())
    }
    var selectingMembers by remember(serverId) { mutableStateOf(false) }
    val noServersMessage = stringResource(Res.string.proxy_editor_strategy_group_no_servers)
    var showDeleteConfirmation by remember(serverId) { mutableStateOf(false) }
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
                onSave(editResult.copy(server = serverEdit))
                editorUiState.dismissDiscardConfirmation()
                onDismiss()
            }
        }
    }

    fun continueSaveAfterWarning() {
        if (serverEdit is StrategyGroup) serverEdit.proxyServerIds = strategyGroupMemberIds
        onSave(editResult.copy(server = serverEdit))
        editorUiState.dismissFullValidationWarning()
        onDismiss()
    }

    fun deleteDraft() {
        onSave(editResult.copy(server = serverEdit, deleted = true))
        editorUiState.dismissDiscardConfirmation()
        onDismiss()
    }

    fun requestDelete() {
        if (serverId != null && confirmDeletion) {
            showDeleteConfirmation = true
        } else if (serverId == null) {
            onDismiss()
        } else {
            deleteDraft()
        }
    }

    fun copyDraft() {
        val basicIssue = serverEdit.validateBasic().firstOrNull()
        if (basicIssue != null) {
            onMessage(validationMessageOf(basicIssue))
            return
        }
        scope.launch {
            when (val result = copyProxyServerText(serverEdit, ProxyServerTextCopyFormat.Default) {
                exportFullJson(editResult.copy(server = serverEdit))
            }) {
                is ProxyServerTextCopyResult.Success -> runCatching {
                    copyTextToClipboard(result.text)
                }.onSuccess { onMessage(copiedMessage) }.onFailure { onMessage(it.message.orEmpty()) }
                ProxyServerTextCopyResult.Unsupported -> onMessage(unsupportedMessage)
                ProxyServerTextCopyResult.InvalidConfig -> onMessage(invalidConfigMessage)
            }
        }
    }

    if (selectingMembers && serverEdit is StrategyGroup) {
        SkipiStrategyGroupMemberSelectorScreen(
            padding = contentPadding,
            groups = memberGroups,
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
    Box(Modifier.fillMaxSize()) {
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
            onDelete = if (serverEdit is StrategyGroup) ::requestDelete else null,
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
                        SkipiJsonCodeEditor(
                            label = stringResource(Res.string.proxy_editor_custom_json),
                            value = custom.configJson,
                            onValueChange = { custom.configJson = it },
                            modifier = jsonModifier,
                        )
                    },
                )
            },
        )
        statusMessage?.takeIf(String::isNotBlank)?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        DeleteConfirmationDialog(
            show = true,
            title = deleteTitle,
            onDismissRequest = { showDeleteConfirmation = false },
            onConfirm = {
                showDeleteConfirmation = false
                deleteDraft()
            },
        )
    }
}

internal fun copyTextToDesktopClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}
