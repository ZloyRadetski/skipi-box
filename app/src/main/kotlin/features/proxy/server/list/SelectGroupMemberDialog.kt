// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppStateStore
import app.ProxyServerState
import app.R
import app.collectAppState
import engine.xray.strategyGroupMembers
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.model.StrategyGroup
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.AppTheme

@Composable
internal fun SelectGroupMemberDialog(
    show: Boolean,
    groupServer: ProxyServerState?,
    onDismissRequest: () -> Unit,
    onSelectMember: (selectedMemberId: Int) -> Unit,
) {
    if (!show || groupServer == null) return
    val strategyGroup = groupServer.server as? StrategyGroup ?: return
    val appState by LocalAppStateStore.current.collectAppState()
    val members = remember(groupServer, appState.proxyServers, appState.trafficConfigs) {
        appState.strategyGroupMembers(strategyGroup)
    }
    val currentSelectedId = strategyGroup.selectedMemberId ?: members.firstOrNull()?.id

    WindowDialog(
        show = true,
        title = groupServer.server.getInfo().remarks.ifBlank { stringResource(R.string.proxy_group_select_active_server) },
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.proxy_group_select_active_server),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (members.isEmpty()) {
                Text(
                    text = stringResource(R.string.proxy_editor_strategy_group_no_servers),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.surface),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(items = members, key = ProxyServerState::id) { member ->
                            val isSelected = member.id == currentSelectedId
                            val remarks = member.server.getInfo().remarks
                            val flag = CountryFlagUtils.extractLeadingCountryFlag(remarks)
                            val cleanName = CountryFlagUtils.stripLeadingCountryFlag(remarks).ifBlank {
                                member.server.getInfo().protocol
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectMember(member.id)
                                        onDismissRequest()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectMember(member.id)
                                        onDismissRequest()
                                    },
                                )
                                Spacer(Modifier.width(12.dp))
                                if (flag != null) {
                                    Text(
                                        text = flag,
                                        fontSize = 18.sp,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cleanName,
                                        style = MiuixTheme.textStyles.body1,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = member.server.getInfo().protocol,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                if (member.latency.isNotBlank()) {
                                    Text(
                                        text = member.latency,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismissRequest,
                )
            }
        }
    }
}
