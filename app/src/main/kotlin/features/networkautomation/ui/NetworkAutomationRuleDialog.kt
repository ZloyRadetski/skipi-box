// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ProxyServerState
import app.R
import features.networkautomation.engine.NetworkAutomationEvaluator
import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType
import features.proxy.server.display.displayName
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun NetworkAutomationRuleDialog(
    show: Boolean,
    rule: NetworkAutomationRule?,
    existingRules: List<NetworkAutomationRule>,
    servers: List<ProxyServerState>,
    onDismissRequest: () -> Unit,
    onSave: (NetworkAutomationRule) -> Unit,
) {
    val context = LocalContext.current
    val isEditing = rule != null

    val typeOptions = listOf(
        stringResource(R.string.network_automation_type_cellular),
        stringResource(R.string.network_automation_type_any_wifi),
        stringResource(R.string.network_automation_type_specific_wifi),
    )
    val typeValues = listOf(
        NetworkRuleType.CELLULAR,
        NetworkRuleType.ANY_WIFI,
        NetworkRuleType.SPECIFIC_WIFI,
    )

    var selectedTypeIndex by remember(show, rule) {
        mutableIntStateOf(
            if (rule != null) typeValues.indexOf(rule.type).coerceAtLeast(0) else 0
        )
    }

    var ssid by remember(show, rule) {
        mutableStateOf(rule?.ssid.orEmpty())
    }

    val actionOptions = listOf(
        stringResource(R.string.network_automation_action_switch_server),
        stringResource(R.string.network_automation_action_disconnect),
    )
    val actionValues = listOf(
        NetworkRuleAction.SWITCH_SERVER,
        NetworkRuleAction.DISCONNECT_VPN,
    )

    var selectedActionIndex by remember(show, rule) {
        mutableIntStateOf(
            if (rule != null) actionValues.indexOf(rule.action).coerceAtLeast(0) else 0
        )
    }

    val serverOptions = remember(servers) {
        if (servers.isEmpty()) listOf("—") else servers.map { it.displayName() }
    }
    val serverIds = remember(servers) {
        if (servers.isEmpty()) listOf<Int?>(null) else servers.map<ProxyServerState, Int?> { it.id }
    }

    var selectedServerIndex by remember(show, rule, servers) {
        mutableIntStateOf(
            if (rule?.targetServerId != null) {
                serverIds.indexOf(rule.targetServerId).coerceAtLeast(0)
            } else 0
        )
    }

    val selectedType = typeValues[selectedTypeIndex]
    val selectedAction = actionValues[selectedActionIndex]

    // Validation
    val isSsidDuplicate = remember(selectedType, ssid, rule, existingRules) {
        if (selectedType == NetworkRuleType.SPECIFIC_WIFI) {
            existingRules.any { other ->
                other.id != rule?.id &&
                    other.type == NetworkRuleType.SPECIFIC_WIFI &&
                    other.ssid?.trim()?.equals(ssid.trim(), ignoreCase = true) == true
            }
        } else false
    }

    val isTypeDuplicate = remember(selectedType, rule, existingRules) {
        if (selectedType == NetworkRuleType.CELLULAR || selectedType == NetworkRuleType.ANY_WIFI) {
            existingRules.any { other ->
                other.id != rule?.id && other.type == selectedType
            }
        } else false
    }

    val canSave = when {
        selectedType == NetworkRuleType.SPECIFIC_WIFI && (ssid.isBlank() || isSsidDuplicate) -> false
        isTypeDuplicate -> false
        selectedAction == NetworkRuleAction.SWITCH_SERVER && servers.isEmpty() -> false
        else -> true
    }

    WindowDialog(
        show = show,
        title = if (isEditing) stringResource(R.string.network_automation_edit_rule) else stringResource(R.string.network_automation_add_rule),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.network_automation_rule_type),
                    items = typeOptions,
                    selectedIndex = selectedTypeIndex,
                    onSelectedIndexChange = { selectedTypeIndex = it },
                )
            }

            AnimatedVisibility(
                visible = selectedType == NetworkRuleType.SPECIFIC_WIFI,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    TextField(
                        state = rememberTextFieldState(initialText = ssid),
                        inputTransformation = InputTransformation {
                            ssid = asCharSequence().toString()
                        },
                        label = stringResource(R.string.network_automation_ssid_label),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = stringResource(R.string.network_automation_use_current_wifi),
                            onClick = {
                                val current = NetworkAutomationEvaluator.getCurrentWifiSsid(context)
                                if (!current.isNullOrBlank()) {
                                    ssid = current
                                }
                            },
                        )
                    }

                    if (isSsidDuplicate) {
                        Text(
                            text = stringResource(R.string.network_automation_ssid_exists_error),
                            color = Color(0xFFE53935),
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (isTypeDuplicate) {
                Text(
                    text = stringResource(R.string.network_automation_ssid_exists_error),
                    color = Color(0xFFE53935),
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.network_automation_rule_action),
                    items = actionOptions,
                    selectedIndex = selectedActionIndex,
                    onSelectedIndexChange = { selectedActionIndex = it },
                )

                if (selectedAction == NetworkRuleAction.SWITCH_SERVER && servers.isNotEmpty()) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.network_automation_server_label),
                        items = serverOptions,
                        selectedIndex = selectedServerIndex.coerceIn(0, serverOptions.lastIndex),
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
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(
                    text = stringResource(R.string.network_automation_save),
                    enabled = canSave,
                    onClick = {
                        val targetServer = if (selectedAction == NetworkRuleAction.SWITCH_SERVER && servers.isNotEmpty()) {
                            serverIds.getOrNull(selectedServerIndex)
                        } else null

                        val finalRule = (rule ?: NetworkAutomationRule(type = selectedType)).copy(
                            type = selectedType,
                            ssid = if (selectedType == NetworkRuleType.SPECIFIC_WIFI) ssid.trim() else null,
                            action = selectedAction,
                            targetServerId = targetServer,
                        )
                        onSave(finalRule)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
