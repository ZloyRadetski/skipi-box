// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.model.ProxyServerRecord as CatalogProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.proxy.ProxyServerRecord as EditorProxyServerRecord
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.ui.config.SkipiTrafficConfigProxyGroupsEditorScreen
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.proxy_editor_strategy_group_all_groups
import app.skipi.ui.resources.proxy_server_list_auto_balancers
import app.skipi.ui.resources.routing_default_proxy_server
import app.skipi.ui.resources.subscription_default_group
import app.skipi.ui.subscription.toSubscriptionGroupUiState
import features.config.ShadowrocketPolicyGroup
import features.config.TrafficConfigProxyGroupServerInput
import features.config.TrafficConfigState
import features.config.newTrafficConfigProxyGroupStrategy
import features.config.toEditableStrategyGroup
import features.config.trafficConfigProxyGroupServerChoices
import org.jetbrains.compose.resources.stringResource

/** Desktop adapter for the shared profile proxy-group list and shared server editor host. */
@Composable
internal fun DesktopTrafficConfigProxyGroupsEditor(
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    proxyServers: List<CatalogProxyServerRecord>,
    subscriptionCatalog: SubscriptionCatalog,
    padding: PaddingValues,
    isWideScreen: Boolean,
    confirmDeletion: Boolean,
    exportProxyServerFullJson: suspend (ProxyServerEditResult) -> String,
    copyTextToClipboard: (String) -> Unit,
    onBack: () -> Unit,
    statusContent: (@Composable () -> Unit)? = null,
) {
    var editingResult by remember(profile.id) { mutableStateOf<ProxyServerEditResult?>(null) }
    var capturedProfile by remember(profile.id) { mutableStateOf<TrafficConfigState?>(null) }
    var editorMessage by remember(profile.id) { mutableStateOf<String?>(null) }

    val serverChoices = remember(proxyServers) {
        trafficConfigProxyGroupServerChoices(
            proxyServers.map { server ->
                TrafficConfigProxyGroupServerInput(id = server.id, server = server.server)
            },
        )
    }
    val editorProxyServers = remember(proxyServers) {
        proxyServers.map { server ->
            EditorProxyServerRecord(
                id = server.id,
                groupId = server.sourceSubscriptionId ?: DesktopProxyGroupIds.DefaultManualSubscriptionId,
                server = server.server,
            )
        }
    }
    val defaultGroupName = stringResource(Res.string.subscription_default_group)
    val allGroupsLabel = stringResource(Res.string.proxy_editor_strategy_group_all_groups)
    val autoBalancerName = stringResource(Res.string.proxy_server_list_auto_balancers)
    val defaultProxyServerTemplate = stringResource(Res.string.routing_default_proxy_server)
    val subscriptionSummaries = subscriptionCatalog.subscriptions.associate { subscription ->
        val group = subscription.toSubscriptionGroupUiState(defaultGroupName)
        group.id to group.toServerPickerSubscriptionSummary()
    }
    val editorInputs = remember(
        editorProxyServers,
        subscriptionCatalog,
        subscriptionSummaries,
        editingResult?.serverId,
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
            excludedServerId = editingResult?.serverId,
        )
    }
    val currentEdit = editingResult
    if (currentEdit == null) {
        SkipiTrafficConfigProxyGroupsEditorScreen(
            profile = profile,
            controller = controller,
            padding = padding,
            isWideScreen = isWideScreen,
            onBack = onBack,
            onAdd = {
                editorMessage = null
                capturedProfile = profile
                editingResult = ProxyServerEditResult(
                    serverId = -1,
                    server = newTrafficConfigProxyGroupStrategy(profile.id),
                )
            },
            onEdit = { group: ShadowrocketPolicyGroup ->
                editorMessage = null
                capturedProfile = profile
                editingResult = ProxyServerEditResult(
                    serverId = group.lineNumber,
                    server = group.toEditableStrategyGroup(
                        trafficConfigId = profile.id,
                        serverChoices = serverChoices,
                    ),
                )
            },
            statusContent = combinedStatusContent(statusContent, editorMessage),
        )
    } else {
        DesktopProxyServerEditorHost(
            contentPadding = padding,
            editResult = currentEdit,
            groupOptions = editorInputs.groupOptions,
            // Chain-only options are unused for a StrategyGroup editor; its member picker uses
            // the separate shared projection below.
            memberOptions = emptyList(),
            memberGroups = editorInputs.memberGroups,
            confirmDeletion = confirmDeletion,
            exportFullJson = exportProxyServerFullJson,
            copyTextToClipboard = copyTextToClipboard,
            statusMessage = editorMessage,
            onSave = { result ->
                capturedProfile?.let { source ->
                    controller.saveProxyGroupResult(source, result, serverChoices)
                }
            },
            onDismiss = {
                editingResult = null
                capturedProfile = null
            },
            onMessage = { editorMessage = it },
        )
    }
}

@Composable
private fun combinedStatusContent(
    statusContent: (@Composable () -> Unit)?,
    editorMessage: String?,
): (@Composable () -> Unit)? {
    if (statusContent == null && editorMessage == null) return null
    return {
        Column {
            statusContent?.invoke()
            editorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
