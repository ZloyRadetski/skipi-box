// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.Route
import app.navigation.StrategyGroupMemberSelectionResult
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.AppTheme

/** Full-screen Material editor for standard Shadowrocket [Proxy Group] aliases. */
@Composable
internal fun TrafficConfigProxyGroupsPage(
    padding: PaddingValues,
    trafficConfigId: Int,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run {
        navigator.pop()
        return
    }
    val groups = remember(config.rawConfig) { config.rawConfig.analyzeShadowrocketConfig().proxyGroups }
    val serverChoices = remember(appState.proxyServers) {
        appState.proxyServers
            .filter { state ->
                state.server !is ChainProxy &&
                    (state.server !is Custom || state.server.canBeUsedInGeneratedProxyPlan())
            }
            .map { state ->
                val remarks = state.server.getInfo().remarks.trim()
                ProxyGroupServerChoice(
                    id = state.id,
                    rawName = remarks,
                )
            }
            .filter { choice -> choice.rawName.isNotBlank() }
    }
    var editingGroupLineNumber by rememberSaveable(trafficConfigId) { mutableStateOf<Int?>(null) }
    var creatingGroup by rememberSaveable(trafficConfigId) { mutableStateOf(false) }
    val editingGroup = editingGroupLineNumber?.let { lineNumber ->
        groups.firstOrNull { group -> group.lineNumber == lineNumber }
    }

    fun updateRaw(raw: String) {
        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { it.copy(rawConfig = raw) }
        }
    }

    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_proxy_groups_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onSave = navigator::pop,
    ) { listPadding ->
        LazyColumn(
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "description") {
                Text(
                    text = stringResource(R.string.configs_proxy_groups_summary),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                )
            }
            item(key = "add") {
                TextButton(
                    text = stringResource(R.string.configs_proxy_groups_add),
                    onClick = { creatingGroup = true },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.configs_proxy_groups_empty),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                }
            }
            items(items = groups, key = ShadowrocketPolicyGroup::lineNumber) { group ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingGroupLineNumber = group.lineNumber },
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    colors = CardDefaults.defaultColors(
                        color = AppTheme.colors.surface,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, style = MiuixTheme.textStyles.title3)
                            Text(
                                text = "${group.type}: ${group.members.joinToString()}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { editingGroupLineNumber = group.lineNumber }) {
                            Icon(MiuixIcons.Edit, contentDescription = stringResource(R.string.configs_edit))
                        }
                        IconButton(onClick = {
                            updateRaw(config.rawConfig.withoutShadowrocketProxyGroupLine(group.lineNumber))
                        }) {
                            Icon(MiuixIcons.Delete, contentDescription = stringResource(R.string.common_delete))
                        }
                    }
                }
            }
        }
    }

    TrafficConfigProxyGroupDialog(
        show = creatingGroup || editingGroup != null,
        initialGroup = editingGroup,
        serverChoices = serverChoices,
        onDismissRequest = {
            creatingGroup = false
            editingGroupLineNumber = null
        },
        onSave = { name, type, members, url, interval, showOnHome ->
            val line = buildString {
                append(name.trim()).append(" = ").append(type)
                members.forEach { member -> append(", ").append(member) }
                url.trim().takeIf(String::isNotBlank)?.let { append(", url=").append(it) }
                interval.trim().toIntOrNull()?.takeIf { it > 0 }?.let { append(", interval=").append(it) }
                if (showOnHome) append(", skipi-show-on-home=true")
            }
            updateRaw(
                editingGroup?.let { group -> config.rawConfig.withShadowrocketProxyGroupLine(group.lineNumber, line) }
                    ?: config.rawConfig.withShadowrocketProxyGroupAdded(line),
            )
            creatingGroup = false
            editingGroupLineNumber = null
        },
    )
}

private data class ProxyGroupServerChoice(
    val id: Int,
    val rawName: String,
)

@Composable
private fun TrafficConfigProxyGroupDialog(
    show: Boolean,
    initialGroup: ShadowrocketPolicyGroup?,
    serverChoices: List<ProxyGroupServerChoice>,
    onDismissRequest: () -> Unit,
    onSave: (name: String, type: String, members: List<String>, url: String, interval: String, showOnHome: Boolean) -> Unit,
) {
    if (!show) return
    val navigator = LocalNavigator.current
    // The member selector is a separate screen. Save the entire draft so opening it
    // cannot reset the name, check URL, interval, or selected proxy-group type.
    var name by rememberSaveable(initialGroup?.lineNumber) { mutableStateOf(initialGroup?.name.orEmpty()) }
    var members by rememberSaveable(initialGroup?.lineNumber) { mutableStateOf(initialGroup?.members.orEmpty()) }
    var url by rememberSaveable(initialGroup?.lineNumber) { mutableStateOf(initialGroup?.url.orEmpty()) }
    var interval by rememberSaveable(initialGroup?.lineNumber) { mutableStateOf(initialGroup?.intervalSeconds?.toString().orEmpty()) }
    var showOnHome by rememberSaveable(initialGroup?.lineNumber) {
        mutableStateOf(initialGroup?.showInAutoBalancerList == true)
    }
    var typeIndex by rememberSaveable(initialGroup?.lineNumber) {
        mutableIntStateOf(ShadowrocketProxyGroupTypes.indexOf(initialGroup?.type).coerceAtLeast(0))
    }
    val type = ShadowrocketProxyGroupTypes[typeIndex]
    val memberSelectorResultKey = remember(initialGroup?.lineNumber) {
        "traffic-config-proxy-group-members-${initialGroup?.lineNumber ?: "new"}"
    }
    val selectedServerIds = remember(members, serverChoices) {
        members.mapNotNull { member ->
            serverChoices.firstOrNull { choice -> choice.rawName.equals(member, ignoreCase = true) }?.id
        }.distinct()
    }
    LaunchedEffect(navigator, memberSelectorResultKey, serverChoices) {
        navigator.observeResult<StrategyGroupMemberSelectionResult>(memberSelectorResultKey).collect { result ->
            val selectedNames = serverChoices
                .filter { choice -> choice.id in result.serverIds }
                .map(ProxyGroupServerChoice::rawName)
            val unresolvedMembers = members.filter { member ->
                serverChoices.none { choice -> choice.rawName.equals(member, ignoreCase = true) }
            }
            members = (unresolvedMembers + selectedNames).distinct()
            navigator.clearResult(memberSelectorResultKey)
        }
    }

    WindowDialog(
        show = true,
        title = stringResource(if (initialGroup == null) R.string.configs_proxy_groups_add else R.string.configs_proxy_groups_edit),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                state = rememberTextFieldState(name),
                inputTransformation = { name = asCharSequence().toString() },
                label = stringResource(R.string.configs_proxy_groups_name),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            WindowDropdownPreference(
                title = stringResource(R.string.configs_proxy_groups_type),
                items = ShadowrocketProxyGroupTypes,
                selectedIndex = typeIndex,
                onSelectedIndexChange = { typeIndex = it },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SwitchPreference(
                title = stringResource(R.string.proxy_editor_strategy_group_show_on_home),
                summary = stringResource(R.string.proxy_editor_strategy_group_show_on_home_summary),
                checked = showOnHome,
                onCheckedChange = { showOnHome = it },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text(
                text = stringResource(R.string.configs_proxy_groups_members),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            ArrowPreference(
                title = stringResource(R.string.proxy_editor_strategy_group_select_servers),
                summary = if (selectedServerIds.isNotEmpty()) {
                    stringResource(
                        R.string.proxy_editor_strategy_group_selected_servers_summary,
                        selectedServerIds.size,
                    )
                } else {
                    stringResource(R.string.proxy_editor_strategy_group_select_servers_summary)
                },
                onClick = {
                    navigator.navigateForResult(
                        route = Route.StrategyGroupMemberSelector(
                            selectedServerIds = selectedServerIds,
                            resultKey = memberSelectorResultKey,
                            requireServerRemarks = true,
                        ),
                        requestKey = memberSelectorResultKey,
                    )
                },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            TextField(
                state = rememberTextFieldState(url),
                inputTransformation = { url = asCharSequence().toString() },
                label = stringResource(R.string.configs_proxy_groups_url),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            TextField(
                state = rememberTextFieldState(interval),
                inputTransformation = { interval = asCharSequence().toString() },
                label = stringResource(R.string.configs_proxy_groups_interval),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(text = stringResource(R.string.common_cancel), onClick = onDismissRequest)
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.common_save),
                    enabled = name.isNotBlank() && members.isNotEmpty(),
                    onClick = { onSave(name, type, members, url, interval, showOnHome) },
                )
            }
        }
    }
}

private val ShadowrocketProxyGroupTypes = listOf("select", "url-test", "fallback", "load-balance")
