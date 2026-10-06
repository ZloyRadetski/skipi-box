// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.LocalAppStateStore
import app.ProxyServerState
import app.R
import app.collectAppState
import app.skipi.ui.server.editor.GroupMemberChoice
import app.skipi.ui.server.editor.SkipiSelectGroupMemberDialog
import engine.xray.strategyGroupMembers
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.model.StrategyGroup

/** Android selector-state adapter; the shared dialog owns its presentation. */
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
    val title = groupServer.server.getInfo().remarks.ifBlank { stringResource(R.string.proxy_group_select_active_server) }
    val choices = remember(members, currentSelectedId) {
        members.map { member ->
            val info = member.server.getInfo()
            val remarks = info.remarks
            val flag = CountryFlagUtils.extractLeadingCountryFlag(remarks)
            val name = CountryFlagUtils.stripLeadingCountryFlag(remarks).ifBlank { info.protocol }
            GroupMemberChoice(
                id = member.id,
                displayName = name,
                protocol = info.protocol,
                flag = flag,
                latency = member.latency,
                selected = member.id == currentSelectedId,
            )
        }
    }
    SkipiSelectGroupMemberDialog(
        show = true,
        title = title,
        summary = stringResource(R.string.proxy_group_select_active_server),
        members = choices,
        noMembersMessage = stringResource(R.string.proxy_editor_strategy_group_no_servers),
        cancelLabel = stringResource(R.string.common_cancel),
        onDismissRequest = onDismissRequest,
        onSelectMember = onSelectMember,
    )
}
