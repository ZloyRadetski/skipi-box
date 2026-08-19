// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.proxy.server.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.ProxyServerState
import app.R
import app.SubscriptionGroupState
import app.collectAppState
import app.navigation.StrategyGroupMemberSelectionResult
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.display.displayName
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.list.CountryFlagBadge
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.proxy.server.model.getTransportDisplay
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.SubscriptionProviderBody
import features.subscription.subscriptionExpirySummary
import features.subscription.subscriptionTrafficProgress
import features.subscription.subscriptionTrafficSummary
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.components.BackNavigationIcon
import ui.components.NavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import ui.text.formatTemplate

private data class StrategyMemberSelectorGroup(
    val key: String,
    val title: String,
    val subscriptionGroup: SubscriptionGroupState? = null,
    val servers: List<ProxyServerState>,
)

/**
 * Explicit strategy-group membership is selected from the real source groups.
 * It deliberately shows auto-balancers too: the planner expands them to their
 * concrete server members before the Xray balancer is built.
 */
@Composable
fun StrategyGroupMemberSelectorPage(
    padding: PaddingValues,
    selectedServerIds: List<Int>,
    excludedServerId: Int?,
    resultKey: String,
    requireServerRemarks: Boolean = false,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val autoBalancersName = stringResource(R.string.proxy_server_list_auto_balancers)
    val defaultProxyServerTemplate = stringResource(R.string.routing_default_proxy_server)
    var selectedIds by remember(selectedServerIds) { mutableStateOf(selectedServerIds.toSet()) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val selectableServers = remember(appState.proxyServers, excludedServerId, requireServerRemarks) {
        appState.proxyServers.filter { server ->
            server.id != excludedServerId &&
                server.server !is ChainProxy &&
                (server.server !is Custom || server.server.canBeUsedInGeneratedProxyPlan()) &&
                (!requireServerRemarks || server.server.getInfo().remarks.isNotBlank())
        }
    }
    val groups = remember(
        appState.subscriptionGroups,
        selectableServers,
        defaultGroupName,
        autoBalancersName,
    ) {
        val serversByGroup = selectableServers.groupBy(ProxyServerState::groupId)
        buildList {
            val autoBalancerServers = serversByGroup[AutoBalancerGroupId]
                .orEmpty()
                .filter { server -> server.server is StrategyGroup }
            if (autoBalancerServers.isNotEmpty()) {
                add(
                    StrategyMemberSelectorGroup(
                        key = "auto-balancers",
                        title = autoBalancersName,
                        subscriptionGroup = null,
                        servers = autoBalancerServers,
                    ),
                )
            }
            appState.subscriptionGroups
                .filter { group -> group.id != DefaultSubscriptionGroupId }
                .forEach { group ->
                    add(
                        StrategyMemberSelectorGroup(
                            key = "subscription-${group.id}",
                            title = group.profileTitle.ifBlank { group.displayName(defaultGroupName) },
                            subscriptionGroup = group,
                            servers = serversByGroup[group.id].orEmpty(),
                        ),
                    )
                }
            val manualServers = serversByGroup[DefaultSubscriptionGroupId].orEmpty()
            if (manualServers.isNotEmpty() || appState.subscriptionGroups.none { it.id != DefaultSubscriptionGroupId }) {
                add(
                    StrategyMemberSelectorGroup(
                        key = "manual-servers",
                        title = defaultGroupName,
                        subscriptionGroup = appState.subscriptionGroups.firstOrNull { it.id == DefaultSubscriptionGroupId },
                        servers = manualServers,
                    ),
                )
            }
        }
    }

    fun toggle(serverId: Int) {
        selectedIds = selectedIds.toMutableSet().apply {
            if (!add(serverId)) remove(serverId)
        }
    }

    fun toggleGroup(group: StrategyMemberSelectorGroup) {
        val groupServerIds = group.servers.map { it.id }.toSet()
        if (groupServerIds.isEmpty()) return
        val allSelected = groupServerIds.all { it in selectedIds }
        selectedIds = if (allSelected) {
            selectedIds - groupServerIds
        } else {
            selectedIds + groupServerIds
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.proxy_editor_strategy_group_select_servers),
                subtitle = stringResource(
                    R.string.proxy_editor_strategy_group_selected_servers_summary,
                    selectedIds.size,
                ),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = navigator::pop) },
                actions = {
                    NavigationIcon(
                        onClick = {
                            navigator.setResult(
                                resultKey,
                                StrategyGroupMemberSelectionResult(selectedIds.toList()),
                            )
                        },
                        imageVector = MiuixIcons.Ok,
                        contentDescription = stringResource(R.string.common_save),
                    )
                },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(scrollBehavior),
                contentPadding = pageListPadding(contentPadding),
            ) {
                groups.forEach { group ->
                    val expanded = expandedGroups[group.key] ?: true
                    val groupServerIds = group.servers.map { it.id }.toSet()
                    val groupToggleState = when {
                        groupServerIds.isEmpty() -> ToggleableState.Off
                        groupServerIds.all { it in selectedIds } -> ToggleableState.On
                        groupServerIds.any { it in selectedIds } -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    }

                    item(key = "strategy-member-group-${group.key}") {
                        ServerPickerGroupHeader(
                            title = group.title,
                            count = group.servers.size,
                            subscriptionGroup = group.subscriptionGroup,
                            expanded = expanded,
                            toggleState = groupToggleState,
                            onToggleGroup = { toggleGroup(group) },
                            onExpandedChange = { expandedGroups[group.key] = it },
                        )
                    }
                    if (expanded) {
                        if (group.servers.isEmpty()) {
                            item(key = "strategy-member-empty-${group.key}") {
                                ServerPickerEmptyGroupRow()
                            }
                        } else {
                            val lastServerId = group.servers.last().id
                            items(
                                items = group.servers,
                                key = { server -> "strategy-member-server-${group.key}-${server.id}" },
                                contentType = { "strategy-member-server" },
                            ) { server ->
                                val info = server.server.getInfo()
                                val rawRemarks = info.remarks
                                val flag = remember(rawRemarks) { CountryFlagUtils.extractLeadingCountryFlag(rawRemarks) }
                                val displayTitle = if (flag != null) {
                                    CountryFlagUtils.stripLeadingCountryFlag(rawRemarks)
                                } else {
                                    rawRemarks.ifBlank { defaultProxyServerTemplate.replace("{id}", server.id.toString()) }
                                }
                                val transport = server.server.getTransportDisplay()
                                val subtitle = if (!transport.isNullOrBlank()) "${info.protocol} • $transport" else info.protocol
                                ServerPickerItemRow(
                                    flag = flag,
                                    displayTitle = displayTitle,
                                    subtitle = subtitle,
                                    selected = server.id in selectedIds,
                                    isLast = server.id == lastServerId,
                                    onClick = { toggle(server.id) },
                                )
                            }
                        }
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}
