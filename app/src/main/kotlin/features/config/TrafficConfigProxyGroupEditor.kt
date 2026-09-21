// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.navigation.ProxyServerEditResult
import app.navigation.Route
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.layout.pageScrollModifiers

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

    fun updateRaw(raw: String) {
        updateAppState { state ->
            state.withUpdatedTrafficConfig(config.id) { it.copy(rawConfig = raw) }
        }
    }

    val proxyGroupResultKey = remember(config.id) {
        "traffic-config-proxy-group-result-${config.id}"
    }

    LaunchedEffect(navigator, proxyGroupResultKey, serverChoices) {
        navigator.observeResult<ProxyServerEditResult>(proxyGroupResultKey).collect { result ->
            val strategy = result.server as? StrategyGroup ?: return@collect
            if (strategy.remarks.isBlank()) return@collect
            val line = strategy.toShadowrocketLine(serverChoices)
            if (result.serverId > 0) {
                updateRaw(config.rawConfig.withShadowrocketProxyGroupLine(result.serverId, line))
            } else {
                updateRaw(config.rawConfig.withShadowrocketProxyGroupAdded(line))
            }
            navigator.clearResult(proxyGroupResultKey)
        }
    }

    fun openNewGroup() {
        val newStrategy = StrategyGroup(
            strategy = StrategyGroupConstants.TYPE_SELECT,
            sourceTrafficConfigId = config.id,
            displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
        )
        navigator.navigateForResult(
            route = Route.ProxyServerEditor(
                ps = newStrategy,
                serverId = -1,
                resultKey = proxyGroupResultKey,
            ),
            requestKey = proxyGroupResultKey,
        )
    }

    fun openEditGroup(group: ShadowrocketPolicyGroup) {
        val editStrategy = group.toEditableStrategyGroup(
            trafficConfigId = config.id,
            serverChoices = serverChoices,
        )
        navigator.navigateForResult(
            route = Route.ProxyServerEditor(
                ps = editStrategy,
                serverId = group.lineNumber,
                resultKey = proxyGroupResultKey,
            ),
            requestKey = proxyGroupResultKey,
        )
    }

    TrafficConfigFullScreenScaffold(
        title = stringResource(R.string.configs_proxy_groups_title),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onSave = navigator::pop,
    ) { listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(scrollBehavior),
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
                    onClick = ::openNewGroup,
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
                        .clickable { openEditGroup(group) },
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
                        IconButton(onClick = { openEditGroup(group) }) {
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
}
