// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType
import features.proxy.server.display.displayName
import features.settings.SettingsSectionCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.components.BackNavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

@Composable
fun SettingsNetworkAutomationPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    var showRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<NetworkAutomationRule?>(null) }

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.settings_network_automation_title),
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = { navigator.pop() },
                    )
                },
            )
        },
    ) { innerPadding ->
        val innerContentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val innerListPadding = pageListPadding(innerContentPadding)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.background)
                    .pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = innerListPadding,
            ) {
                item(key = "automation_controls") {
                    SmallTitle(text = stringResource(R.string.settings_network_automation_title))
                    SettingsSectionCard {
                        SwitchPreference(
                            title = stringResource(R.string.network_automation_switch_title),
                            summary = stringResource(R.string.network_automation_switch_summary),
                            checked = appState.enableNetworkAutomation,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableNetworkAutomation = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.network_automation_on_demand_title),
                            summary = stringResource(R.string.network_automation_on_demand_summary),
                            checked = appState.enableOnDemandVpn,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableOnDemandVpn = enabled) }
                            },
                        )
                    }
                }

                item(key = "rules_header") {
                    SmallTitle(text = stringResource(R.string.network_automation_rules_section))
                }

                if (appState.networkAutomationRules.isEmpty()) {
                    item(key = "empty_rules") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            colors = CardDefaults.defaultColors(
                                color = AppTheme.colors.surface,
                                contentColor = AppTheme.colors.onSurfaceVariant,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.network_automation_no_rules),
                                style = MiuixTheme.textStyles.body2,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(
                        items = appState.networkAutomationRules,
                        key = { it.id },
                    ) { rule ->
                        val targetServer = appState.proxyServers.firstOrNull { it.id == rule.targetServerId }
                        val typeTitle = when (rule.type) {
                            NetworkRuleType.CELLULAR -> stringResource(R.string.network_automation_type_cellular)
                            NetworkRuleType.ANY_WIFI -> stringResource(R.string.network_automation_type_any_wifi)
                            NetworkRuleType.SPECIFIC_WIFI -> "Wi-Fi: ${rule.ssid.orEmpty()}"
                        }
                        val actionSummary = when (rule.action) {
                            NetworkRuleAction.DISCONNECT_VPN -> stringResource(R.string.network_automation_action_disconnect)
                            NetworkRuleAction.SWITCH_SERVER -> {
                                val serverName = targetServer?.displayName() ?: "—"
                                "${stringResource(R.string.network_automation_action_switch_server)}: $serverName"
                            }
                            NetworkRuleAction.SWITCH_IF_CONNECTED -> {
                                val serverName = targetServer?.displayName() ?: "—"
                                "${stringResource(R.string.network_automation_action_switch_if_connected)}: $serverName"
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable {
                                    editingRule = rule
                                    showRuleDialog = true
                                },
                            colors = CardDefaults.defaultColors(
                                color = AppTheme.colors.surface,
                                contentColor = AppTheme.colors.onSurface,
                            ),
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
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                        ),
                                        color = AppTheme.colors.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = actionSummary,
                                        style = MiuixTheme.textStyles.body2,
                                        color = AppTheme.colors.onSurfaceVariant,
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editingRule = rule
                                            showRuleDialog = true
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Edit,
                                            contentDescription = stringResource(R.string.network_automation_edit_rule),
                                            tint = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            updateAppState { state ->
                                                state.copy(
                                                    networkAutomationRules = state.networkAutomationRules.filter { it.id != rule.id },
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = stringResource(R.string.network_automation_delete_rule),
                                            tint = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { enabled ->
                                            updateAppState { state ->
                                                state.copy(
                                                    networkAutomationRules = state.networkAutomationRules.map {
                                                        if (it.id == rule.id) it.copy(enabled = enabled) else it
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "add_rule_button") {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(R.string.network_automation_add_rule),
                            onClick = {
                                editingRule = null
                                showRuleDialog = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (showRuleDialog) {
        NetworkAutomationRuleDialog(
            show = showRuleDialog,
            rule = editingRule,
            existingRules = appState.networkAutomationRules,
            servers = appState.proxyServers,
            onDismissRequest = {
                showRuleDialog = false
                editingRule = null
            },
            onSave = { savedRule ->
                updateAppState { state ->
                    val rules = state.networkAutomationRules
                    val existingIndex = rules.indexOfFirst { it.id == savedRule.id }
                    val updatedRules = if (existingIndex >= 0) {
                        rules.toMutableList().apply { set(existingIndex, savedRule) }
                    } else {
                        rules + savedRule
                    }
                    state.copy(networkAutomationRules = updatedRules)
                }
            },
        )
    }
}
