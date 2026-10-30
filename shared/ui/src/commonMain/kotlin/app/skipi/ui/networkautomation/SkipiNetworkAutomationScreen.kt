// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.networkautomation

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.components.AppWindowDropdownPreference
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.network_automation_action_disconnect
import app.skipi.ui.resources.network_automation_action_switch_if_connected
import app.skipi.ui.resources.network_automation_action_switch_server
import app.skipi.ui.resources.network_automation_add_rule
import app.skipi.ui.resources.network_automation_delete_rule
import app.skipi.ui.resources.network_automation_edit_rule
import app.skipi.ui.resources.network_automation_no_rules
import app.skipi.ui.resources.network_automation_on_demand_summary
import app.skipi.ui.resources.network_automation_on_demand_title
import app.skipi.ui.resources.network_automation_rule_action
import app.skipi.ui.resources.network_automation_rule_type
import app.skipi.ui.resources.network_automation_rules_section
import app.skipi.ui.resources.network_automation_save
import app.skipi.ui.resources.network_automation_server_label
import app.skipi.ui.resources.network_automation_ssid_exists_error
import app.skipi.ui.resources.network_automation_ssid_label
import app.skipi.ui.resources.network_automation_switch_summary
import app.skipi.ui.resources.network_automation_switch_title
import app.skipi.ui.resources.network_automation_type_any_wifi
import app.skipi.ui.resources.network_automation_type_cellular
import app.skipi.ui.resources.network_automation_wifi_rule_title
import app.skipi.ui.resources.network_automation_type_exists_error
import app.skipi.ui.resources.network_automation_type_specific_wifi
import app.skipi.ui.resources.network_automation_use_current_wifi
import app.skipi.ui.resources.network_automation_wifi_permission_summary
import app.skipi.ui.resources.settings_network_automation_title
import app.skipi.ui.settings.SkipiSettingsSectionCard
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType
import features.networkautomation.policy.NetworkAutomationRules
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A catalog server a rule can switch to; the host supplies its display name. */
data class NetworkAutomationServerOption(val id: Int, val name: String)

/**
 * Network automation settings: the two feature switches and the prioritized rule list with its
 * editor. The host owns persistence (each callback is applied atomically with
 * [NetworkAutomationRules]) and, where the platform can read the connected Wi-Fi name, passes
 * [onRequestCurrentWifi]; without it the editor offers no "insert current Wi-Fi" action.
 */
@Composable
fun SkipiNetworkAutomationScreen(
    networkAutomationEnabled: Boolean,
    onDemandEnabled: Boolean,
    rules: List<NetworkAutomationRule>,
    servers: List<NetworkAutomationServerOption>,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onNetworkAutomationChange: (Boolean) -> Unit,
    onOnDemandChange: (Boolean) -> Unit,
    onSaveRule: (NetworkAutomationRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onRuleEnabledChange: (id: String, enabled: Boolean) -> Unit,
    onRequestCurrentWifi: ((onSsid: (String) -> Unit) -> Unit)? = null,
    footer: @Composable () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var editorOpen by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<NetworkAutomationRule?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(Res.string.settings_network_automation_title),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding = innerPadding, outerPadding = padding, isWideScreen = isWideScreen)
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
                contentPadding = pageListPadding(contentPadding),
            ) {
                item(key = "automation_controls") {
                    SmallTitle(text = stringResource(Res.string.settings_network_automation_title))
                    SkipiSettingsSectionCard {
                        SwitchPreference(
                            title = stringResource(Res.string.network_automation_switch_title),
                            summary = stringResource(Res.string.network_automation_switch_summary),
                            checked = networkAutomationEnabled,
                            onCheckedChange = onNetworkAutomationChange,
                        )
                        SwitchPreference(
                            title = stringResource(Res.string.network_automation_on_demand_title),
                            summary = stringResource(Res.string.network_automation_on_demand_summary),
                            checked = onDemandEnabled,
                            onCheckedChange = onOnDemandChange,
                        )
                    }
                }
                item(key = "footer") { footer() }
                item(key = "rules_header") { SmallTitle(text = stringResource(Res.string.network_automation_rules_section)) }

                if (rules.isEmpty()) {
                    item(key = "empty_rules") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            colors = CardDefaults.defaultColors(
                                color = SkipiTheme.colors.surface,
                                contentColor = SkipiTheme.colors.onSurfaceVariant,
                            ),
                        ) {
                            Text(
                                text = stringResource(Res.string.network_automation_no_rules),
                                style = MiuixTheme.textStyles.body2,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(items = rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            serverName = servers.firstOrNull { it.id == rule.targetServerId }?.name,
                            onEdit = {
                                editingRule = rule
                                editorOpen = true
                            },
                            onDelete = { onDeleteRule(rule.id) },
                            onEnabledChange = { enabled -> onRuleEnabledChange(rule.id, enabled) },
                        )
                    }
                }

                item(key = "add_rule_button") {
                    Spacer(modifier = Modifier.height(8.dp))
                    SkipiSettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(Res.string.network_automation_add_rule),
                            onClick = {
                                editingRule = null
                                editorOpen = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (editorOpen) {
        NetworkAutomationRuleDialog(
            rule = editingRule,
            existingRules = rules,
            servers = servers,
            onRequestCurrentWifi = onRequestCurrentWifi,
            onDismissRequest = {
                editorOpen = false
                editingRule = null
            },
            onSave = onSaveRule,
        )
    }
}

@Composable
private fun RuleCard(
    rule: NetworkAutomationRule,
    serverName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val typeTitle = when (rule.type) {
        NetworkRuleType.CELLULAR -> stringResource(Res.string.network_automation_type_cellular)
        NetworkRuleType.ANY_WIFI -> stringResource(Res.string.network_automation_type_any_wifi)
        NetworkRuleType.SPECIFIC_WIFI -> stringResource(Res.string.network_automation_wifi_rule_title, rule.ssid.orEmpty())
    }
    val actionSummary = when (rule.action) {
        NetworkRuleAction.DISCONNECT_VPN -> stringResource(Res.string.network_automation_action_disconnect)
        NetworkRuleAction.SWITCH_SERVER ->
            "${stringResource(Res.string.network_automation_action_switch_server)}: ${serverName ?: "—"}"
        NetworkRuleAction.SWITCH_IF_CONNECTED ->
            "${stringResource(Res.string.network_automation_action_switch_if_connected)}: ${serverName ?: "—"}"
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onEdit),
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface, contentColor = SkipiTheme.colors.onSurface),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = typeTitle,
                    style = MiuixTheme.textStyles.main.copy(
                        fontWeight = themedFontWeight(FontWeight.SemiBold),
                        fontSize = 16.sp,
                    ),
                    color = SkipiTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = actionSummary,
                    style = MiuixTheme.textStyles.body2,
                    color = SkipiTheme.colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = stringResource(Res.string.network_automation_edit_rule),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = stringResource(Res.string.network_automation_delete_rule),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
            }
        }
    }
}

@Composable
private fun NetworkAutomationRuleDialog(
    rule: NetworkAutomationRule?,
    existingRules: List<NetworkAutomationRule>,
    servers: List<NetworkAutomationServerOption>,
    onRequestCurrentWifi: ((onSsid: (String) -> Unit) -> Unit)?,
    onDismissRequest: () -> Unit,
    onSave: (NetworkAutomationRule) -> Unit,
) {
    val typeValues = listOf(NetworkRuleType.CELLULAR, NetworkRuleType.ANY_WIFI, NetworkRuleType.SPECIFIC_WIFI)
    val typeOptions = listOf(
        stringResource(Res.string.network_automation_type_cellular),
        stringResource(Res.string.network_automation_type_any_wifi),
        stringResource(Res.string.network_automation_type_specific_wifi),
    )
    val actionValues = listOf(
        NetworkRuleAction.SWITCH_SERVER,
        NetworkRuleAction.SWITCH_IF_CONNECTED,
        NetworkRuleAction.DISCONNECT_VPN,
    )
    val actionOptions = listOf(
        stringResource(Res.string.network_automation_action_switch_server),
        stringResource(Res.string.network_automation_action_switch_if_connected),
        stringResource(Res.string.network_automation_action_disconnect),
    )

    var selectedTypeIndex by remember(rule) {
        mutableIntStateOf(if (rule != null) typeValues.indexOf(rule.type).coerceAtLeast(0) else 0)
    }
    var ssid by remember(rule) { mutableStateOf(rule?.ssid.orEmpty()) }
    var selectedActionIndex by remember(rule) {
        mutableIntStateOf(if (rule != null) actionValues.indexOf(rule.action).coerceAtLeast(0) else 0)
    }
    val serverNames = remember(servers) { if (servers.isEmpty()) listOf("—") else servers.map { it.name } }
    var selectedServerIndex by remember(rule, servers) {
        mutableIntStateOf(servers.indexOfFirst { it.id == rule?.targetServerId }.coerceAtLeast(0))
    }

    val selectedType = typeValues[selectedTypeIndex.coerceIn(typeValues.indices)]
    val selectedAction = actionValues[selectedActionIndex.coerceIn(actionValues.indices)]
    val requiresServer = NetworkAutomationRules.requiresServer(selectedAction)
    val validation = NetworkAutomationRules.validateDraft(
        type = selectedType,
        ssid = ssid,
        action = selectedAction,
        editingRuleId = rule?.id,
        existingRules = existingRules,
        hasServers = servers.isNotEmpty(),
    )

    AppWindowDialog(
        show = true,
        title = stringResource(
            if (rule != null) Res.string.network_automation_edit_rule else Res.string.network_automation_add_rule,
        ),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
            ) {
                AppWindowDropdownPreference(
                    title = stringResource(Res.string.network_automation_rule_type),
                    items = typeOptions,
                    selectedIndex = selectedTypeIndex.coerceIn(typeOptions.indices),
                    onSelectedIndexChange = { selectedTypeIndex = it },
                )
            }

            AnimatedVisibility(
                visible = selectedType == NetworkRuleType.SPECIFIC_WIFI,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    TextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = stringResource(Res.string.network_automation_ssid_label),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                    if (onRequestCurrentWifi != null) {
                        Text(
                            text = stringResource(Res.string.network_automation_wifi_permission_summary),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                text = stringResource(Res.string.network_automation_use_current_wifi),
                                onClick = { onRequestCurrentWifi { detected -> ssid = detected } },
                            )
                        }
                    }
                    if (validation.ssidDuplicate) {
                        Text(
                            text = stringResource(Res.string.network_automation_ssid_exists_error),
                            color = MiuixTheme.colorScheme.error,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (validation.typeDuplicate) {
                Text(
                    text = stringResource(Res.string.network_automation_type_exists_error),
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
            ) {
                AppWindowDropdownPreference(
                    title = stringResource(Res.string.network_automation_rule_action),
                    items = actionOptions,
                    selectedIndex = selectedActionIndex.coerceIn(actionOptions.indices),
                    onSelectedIndexChange = { selectedActionIndex = it },
                )
                if (requiresServer && servers.isNotEmpty()) {
                    AppWindowDropdownPreference(
                        title = stringResource(Res.string.network_automation_server_label),
                        items = serverNames,
                        selectedIndex = selectedServerIndex.coerceIn(0, serverNames.lastIndex),
                        onSelectedIndexChange = { selectedServerIndex = it },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = stringResource(Res.string.common_cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(
                    text = stringResource(Res.string.network_automation_save),
                    enabled = validation.canSave,
                    onClick = {
                        val target = servers.getOrNull(selectedServerIndex.coerceIn(0, (servers.size - 1).coerceAtLeast(0)))?.id
                        onSave(NetworkAutomationRules.buildRule(rule, selectedType, ssid, selectedAction, target))
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
