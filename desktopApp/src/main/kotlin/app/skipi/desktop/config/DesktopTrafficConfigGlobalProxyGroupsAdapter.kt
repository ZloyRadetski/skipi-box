// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import app.skipi.app.model.ProxyServerRecord as CatalogProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.proxy.ProxyServerRecord as EditorProxyServerRecord
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.app.server.createProxyServerEditorDraft
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.ui.config.SkipiTrafficConfigProxyGroupContextMenu
import app.skipi.ui.config.TrafficConfigProxyGroupItem
import app.skipi.ui.config.projectTrafficConfigGlobalProxyGroups
import app.skipi.ui.config.trafficConfigGlobalProxyGroupDuplicateResult
import app.skipi.ui.config.trafficConfigGlobalProxyGroupEditResult
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.configs_copy_name
import app.skipi.ui.resources.deletion_confirmation_delete_proxy_server
import app.skipi.ui.resources.deletion_confirmation_summary
import app.skipi.ui.resources.proxy_editor_strategy_group_all_groups
import app.skipi.ui.resources.proxy_server_list_auto_balancers
import app.skipi.ui.resources.routing_default_proxy_server
import app.skipi.ui.resources.subscription_default_group
import app.skipi.ui.subscription.toSubscriptionGroupUiState
import features.proxy.server.model.StrategyGroup
import org.jetbrains.compose.resources.stringResource

internal data class DesktopTrafficConfigGlobalProxyGroupCollection(
    val groups: List<TrafficConfigProxyGroupItem>,
    val failureMessage: String?,
    val onAdd: () -> Unit,
    val onOpen: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onContextMenu: (String) -> Unit,
)

/** Desktop adapter for global traffic-config groups and the shared proxy editor. */
@Composable
internal fun DesktopTrafficConfigGlobalProxyGroupsAdapter(
    proxyServers: List<CatalogProxyServerRecord>,
    subscriptionCatalog: SubscriptionCatalog,
    contentPadding: PaddingValues,
    confirmDeletion: Boolean,
    exportProxyServerFullJson: suspend (ProxyServerEditResult) -> String,
    copyTextToClipboard: (String) -> Unit,
    onApplyResult: (ProxyServerEditResult, (ProxyServerEditApplyOutcome) -> Unit) -> Unit,
    content: @Composable (DesktopTrafficConfigGlobalProxyGroupCollection) -> Unit,
) {
    var editResult by remember { mutableStateOf<ProxyServerEditResult?>(null) }
    var selectedContextRecord by remember { mutableStateOf<CatalogProxyServerRecord?>(null) }
    var pendingDeletion by remember { mutableStateOf<CatalogProxyServerRecord?>(null) }
    var editorMessage by remember { mutableStateOf<String?>(null) }
    var failureMessage by remember { mutableStateOf<String?>(null) }

    val defaultGroupName = stringResource(Res.string.subscription_default_group)
    val allGroupsLabel = stringResource(Res.string.proxy_editor_strategy_group_all_groups)
    val autoBalancerName = stringResource(Res.string.proxy_server_list_auto_balancers)
    val defaultProxyServerTemplate = stringResource(Res.string.routing_default_proxy_server)
    val copyNamePattern = stringResource(Res.string.configs_copy_name)
    val deleteTitle = stringResource(Res.string.deletion_confirmation_delete_proxy_server)
    val deleteSummary = stringResource(Res.string.deletion_confirmation_summary)

    val editorProxyServers = remember(proxyServers) {
        proxyServers.map { server ->
            EditorProxyServerRecord(
                id = server.id,
                groupId = server.sourceSubscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId,
                server = server.server,
            )
        }
    }
    val subscriptionSummaries = subscriptionCatalog.subscriptions.associate { subscription ->
        val group = subscription.toSubscriptionGroupUiState(defaultGroupName)
        group.id to group.toServerPickerSubscriptionSummary()
    }
    val editorInputs = remember(
        editorProxyServers,
        subscriptionCatalog,
        subscriptionSummaries,
        editResult?.serverId,
        defaultGroupName,
        allGroupsLabel,
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
            excludedServerId = editResult?.serverId,
        )
    }

    fun openEditor(result: ProxyServerEditResult?) {
        if (result == null) return
        failureMessage = null
        editorMessage = null
        editResult = result
    }

    fun findRecord(key: String): CatalogProxyServerRecord? {
        val id = key.takeIf { value -> value.startsWith("global:") }
            ?.substringAfter(':')
            ?.toIntOrNull()
            ?: return null
        return proxyServers.firstOrNull { record -> record.id == id }
    }

    fun applyDelete(record: CatalogProxyServerRecord) {
        onApplyResult(
            ProxyServerEditResult(
                serverId = record.id,
                server = record.server,
                groupId = DefaultAutoBalancerGroupId,
                returnGroupId = DefaultAutoBalancerGroupId,
                deleted = true,
            ),
        ) { outcome ->
            failureMessage = (outcome as? ProxyServerEditApplyOutcome.Failed)?.reason
        }
    }

    val currentEdit = editResult
    if (currentEdit != null) {
        key(currentEdit.serverId ?: "new-global-group") {
            DesktopProxyServerEditorHost(
                contentPadding = contentPadding,
                editResult = currentEdit,
                groupOptions = editorInputs.groupOptions,
                memberOptions = emptyList(),
                memberGroups = editorInputs.memberGroups,
                confirmDeletion = confirmDeletion,
                exportFullJson = exportProxyServerFullJson,
                copyTextToClipboard = copyTextToClipboard,
                statusMessage = editorMessage,
                onSave = { result ->
                    onApplyResult(result) { outcome ->
                        failureMessage = (outcome as? ProxyServerEditApplyOutcome.Failed)?.reason
                    }
                },
                onDismiss = {
                    editResult = null
                    editorMessage = null
                },
                onMessage = { editorMessage = it },
            )
        }
        return
    }

    val globalGroups = projectTrafficConfigGlobalProxyGroups(editorProxyServers)
    content(
        DesktopTrafficConfigGlobalProxyGroupCollection(
            groups = globalGroups,
            failureMessage = failureMessage,
            onAdd = {
                openEditor(
                    createProxyServerEditorDraft(
                        kind = ProxyHomeServerKind.StrategyGroup,
                        defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId,
                    ),
                )
            },
            onOpen = { key ->
                findRecord(key)?.let { record ->
                    openEditor(trafficConfigGlobalProxyGroupEditResult(record.server, record.id))
                }
            },
            onDelete = { key ->
                findRecord(key)?.let { record ->
                    if (confirmDeletion) pendingDeletion = record else applyDelete(record)
                }
            },
            onContextMenu = { key -> selectedContextRecord = findRecord(key) },
        ),
    )

    selectedContextRecord?.let { record ->
        SkipiTrafficConfigProxyGroupContextMenu(
            show = true,
            onDismissRequest = { selectedContextRecord = null },
            onEdit = {
                selectedContextRecord = null
                openEditor(trafficConfigGlobalProxyGroupEditResult(record.server, record.id))
            },
            onDuplicate = {
                selectedContextRecord = null
                val strategyGroup = record.server as? StrategyGroup
                val originalName = strategyGroup?.let { group ->
                    group.remarks.ifBlank { group.getInfo().protocol }
                }
                val copyName = originalName?.let { String.format(java.util.Locale.getDefault(), copyNamePattern, it) }
                openEditor(
                    if (copyName == null) null
                    else trafficConfigGlobalProxyGroupDuplicateResult(record.server, copyName),
                )
            },
            onDelete = {
                selectedContextRecord = null
                if (confirmDeletion) pendingDeletion = record else applyDelete(record)
            },
        )
    }

    pendingDeletion?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(deleteTitle) },
            text = { Text(deleteSummary) },
            confirmButton = {
                Button(onClick = {
                    pendingDeletion = null
                    applyDelete(record)
                }) {
                    Text(stringResource(Res.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
}
