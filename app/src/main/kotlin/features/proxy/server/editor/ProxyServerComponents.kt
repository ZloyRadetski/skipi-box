// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.LocalAppStateStore
import app.R
import app.collectAppState
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.StrategyGroupDisplayMode
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import ui.AppTheme
import ui.text.formatTemplate

internal fun CharSequence.isDigitsOnly(): Boolean {
    if (isEmpty()) return true
    return all { char -> char.isDigit() }
}

internal data class ProxyServerEditorGroupOption(
    val id: Int?,
    val label: String,
)

internal data class ProxyServerEditorMemberOption(
    val id: Int,
    val label: String,
)

internal fun LazyListScope.strategyGroupProxyServer(
    strategyGroupEdit: StrategyGroup,
    groupOptions: List<ProxyServerEditorGroupOption>,
    selectedMemberCount: Int,
    onOpenMembers: (() -> Unit)?,
) {
    item(key = "properties") {
        val focusManager = LocalFocusManager.current
        val appState by LocalAppStateStore.current.collectAppState()
        val defaultProbeUrl = remember(appState.subscriptionPingUrl) {
            appState.subscriptionPingUrl.ifBlank { engine.network.NetworkDefaults.CONNECTIVITY_CHECK_URL }
        }
        val strategyValues = remember {
            listOf(
                StrategyGroupConstants.TYPE_SELECT,
                StrategyGroupConstants.TYPE_LEAST_PING,
                StrategyGroupConstants.TYPE_LEAST_LOAD,
                StrategyGroupConstants.TYPE_RANDOM,
                StrategyGroupConstants.TYPE_ROUND_ROBIN,
            )
        }
        val strategyLabels = listOf(
            stringResource(R.string.proxy_editor_strategy_group_select),
            stringResource(R.string.proxy_editor_strategy_group_least_ping),
            stringResource(R.string.proxy_editor_strategy_group_least_load),
            stringResource(R.string.proxy_editor_strategy_group_random),
            stringResource(R.string.proxy_editor_strategy_group_round_robin),
        )
        val effectiveGroupOptions = groupOptions.ifEmpty {
            listOf(ProxyServerEditorGroupOption(null, stringResource(R.string.proxy_editor_strategy_group_all_groups)))
        }
        val strategyIndex = remember(strategyGroupEdit.strategy) {
            mutableIntStateOf(strategyValues.indexOf(strategyGroupEdit.strategy).coerceAtLeast(0))
        }
        val groupIndex = remember(strategyGroupEdit.subscriptionGroupId, effectiveGroupOptions) {
            mutableIntStateOf(
                effectiveGroupOptions
                    .indexOfFirst { option -> option.id == strategyGroupEdit.subscriptionGroupId }
                    .coerceAtLeast(0),
            )
        }
        val showInAutoBalancerListState = remember(strategyGroupEdit.displayMode, strategyGroupEdit.showInAutoBalancerList) {
            mutableStateOf(strategyGroupEdit.displayMode != StrategyGroupDisplayMode.NEVER && strategyGroupEdit.showInAutoBalancerList)
        }
        val remarksState = rememberTextFieldState(initialText = strategyGroupEdit.remarks)
        LaunchedEffect(remarksState.text) {
            strategyGroupEdit.remarks = remarksState.text.toString()
        }
        val filterState = rememberTextFieldState(initialText = strategyGroupEdit.filter)
        LaunchedEffect(filterState.text) {
            strategyGroupEdit.filter = filterState.text.toString()
        }
        SmallTitle(text = stringResource(R.string.proxy_editor_properties))
        TextField(
            label = stringResource(R.string.proxy_editor_remarks),
            state = remarksState,
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                strategyGroupEdit.remarks = asCharSequence().toString()
            },
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
        ) {
            OverlayDropdownPreference(
                title = stringResource(R.string.proxy_editor_strategy_group_type),
                items = strategyLabels,
                selectedIndex = strategyIndex.intValue,
                onSelectedIndexChange = { index ->
                    strategyIndex.intValue = index
                    strategyGroupEdit.strategy = strategyValues[index]
                },
            )
            SwitchPreference(
                title = stringResource(R.string.proxy_editor_strategy_group_show_on_home),
                summary = stringResource(R.string.proxy_editor_strategy_group_show_on_home_summary),
                checked = showInAutoBalancerListState.value,
                onCheckedChange = { showInList ->
                    showInAutoBalancerListState.value = showInList
                    strategyGroupEdit.showInAutoBalancerList = showInList
                    strategyGroupEdit.displayMode = if (showInList) StrategyGroupDisplayMode.ALWAYS else StrategyGroupDisplayMode.NEVER
                },
            )
        }

        SmallTitle(text = stringResource(R.string.proxy_editor_strategy_group_select_servers))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
        ) {
            ArrowPreference(
                title = stringResource(R.string.proxy_editor_strategy_group_select_servers),
                summary = if (selectedMemberCount > 0) {
                    stringResource(
                        R.string.proxy_editor_strategy_group_selected_servers_summary,
                        selectedMemberCount,
                    )
                } else {
                    stringResource(R.string.proxy_editor_strategy_group_select_servers_summary)
                },
                onClick = {
                    strategyGroupEdit.remarks = remarksState.text.toString()
                    strategyGroupEdit.filter = filterState.text.toString()
                    onOpenMembers?.invoke()
                },
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.proxy_editor_strategy_group_source_group),
                items = effectiveGroupOptions.map { option -> option.label },
                selectedIndex = groupIndex.intValue,
                onSelectedIndexChange = { index ->
                    groupIndex.intValue = index
                    strategyGroupEdit.subscriptionGroupId = effectiveGroupOptions[index].id
                },
            )
        }
        TextField(
            label = stringResource(R.string.proxy_editor_strategy_group_filter),
            state = filterState,
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                strategyGroupEdit.filter = asCharSequence().toString()
            },
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )

        if (strategyGroupEdit.strategy != StrategyGroupConstants.TYPE_SELECT) {
            val probeIntervalValues = remember { listOf("5s", "10s", "15s", "30s", "1m", "2m", "5m") }
            val probeIntervalLabels = listOf(
                "5s",
                "10s",
                "15s",
                "30s",
                "1m",
                "2m",
                "5m",
            )
            val probeIntervalIndex = remember(strategyGroupEdit.probeInterval) {
                mutableIntStateOf(
                    probeIntervalValues.indexOf(strategyGroupEdit.probeInterval).let { if (it >= 0) it else 2 },
                )
            }
            val initialProbeUrl = remember(strategyGroupEdit.probeUrl, defaultProbeUrl) {
                strategyGroupEdit.probeUrl.ifBlank { defaultProbeUrl }
            }
            val probeUrlState = rememberTextFieldState(initialText = initialProbeUrl)
            LaunchedEffect(probeUrlState.text) {
                strategyGroupEdit.probeUrl = probeUrlState.text.toString()
            }
            val burstProbeState = remember(strategyGroupEdit.enableBurstProbe) {
                mutableStateOf(strategyGroupEdit.enableBurstProbe)
            }

            SmallTitle(text = stringResource(R.string.proxy_editor_strategy_group_health_check))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
            ) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.proxy_editor_strategy_group_probe_interval),
                    items = probeIntervalLabels,
                    selectedIndex = probeIntervalIndex.intValue,
                    onSelectedIndexChange = { index ->
                        probeIntervalIndex.intValue = index
                        strategyGroupEdit.probeInterval = probeIntervalValues[index]
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.proxy_editor_strategy_group_burst_probe),
                    summary = stringResource(R.string.proxy_editor_strategy_group_burst_probe_summary),
                    checked = burstProbeState.value,
                    onCheckedChange = { checked ->
                        burstProbeState.value = checked
                        strategyGroupEdit.enableBurstProbe = checked
                    },
                )
            }
            TextField(
                label = stringResource(R.string.proxy_editor_strategy_group_probe_url),
                state = probeUrlState,
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation {
                    strategyGroupEdit.probeUrl = asCharSequence().toString()
                },
                onKeyboardAction = { focusManager.clearFocus() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }
    }
}
internal fun LazyListScope.chainProxyServer(
    chainProxyEdit: ChainProxy,
    memberOptions: List<ProxyServerEditorMemberOption>,
) {
    item(key = "properties") {
        val focusManager = LocalFocusManager.current
        var members by remember(chainProxyEdit) {
            mutableStateOf(chainProxyEdit.proxyServerIds.ifEmpty { listOf(0, 0) })
        }
        val unselectedMember = ProxyServerEditorMemberOption(
            id = 0,
            label = stringResource(R.string.proxy_editor_chain_member_unselected),
        )
        val effectiveMemberOptions = listOf(unselectedMember) + memberOptions

        fun updateMembers(nextMembers: List<Int>) {
            members = nextMembers
            chainProxyEdit.proxyServerIds = nextMembers.filter { id -> id != unselectedMember.id }
        }

        val chainRemarksState = rememberTextFieldState(initialText = chainProxyEdit.remarks)
        LaunchedEffect(chainRemarksState.text) {
            chainProxyEdit.remarks = chainRemarksState.text.toString()
        }
        SmallTitle(text = stringResource(R.string.proxy_editor_properties))
        TextField(
            label = stringResource(R.string.proxy_editor_remarks),
            state = chainRemarksState,
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                chainProxyEdit.remarks = asCharSequence().toString()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        SmallTitle(text = stringResource(R.string.proxy_editor_chain_members))
        members.forEachIndexed { index, memberId ->
            val selectedIndex = effectiveMemberOptions
                .indexOfFirst { option -> option.id == memberId }
                .coerceAtLeast(0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.proxy_editor_chain_member)
                        .formatTemplate("index" to index + 1),
                    items = effectiveMemberOptions.map { option -> option.label },
                    selectedIndex = selectedIndex,
                    modifier = Modifier.weight(1f),
                    onSelectedIndexChange = { optionIndex ->
                        updateMembers(members.toMutableList().also { it[index] = effectiveMemberOptions[optionIndex].id })
                    },
                )
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = {
                        if (members.size > 1) {
                            updateMembers(members.filterIndexed { memberIndex, _ -> memberIndex != index })
                        }
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(
                text = stringResource(R.string.proxy_editor_chain_add_member),
                onClick = {
                    updateMembers(members + unselectedMember.id)
                },
            )
        }
    }
}
