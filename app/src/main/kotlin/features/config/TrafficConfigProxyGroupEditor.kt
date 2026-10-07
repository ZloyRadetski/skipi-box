// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.ProxyServerEditResult
import app.navigation.Route
import app.skipi.ui.config.SkipiTrafficConfigProxyGroups
import features.proxy.server.model.StrategyGroup
import ui.layout.pageScrollModifiers

/** Full-screen editor for standard Shadowrocket [Proxy Group] aliases. */
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
        trafficConfigProxyGroupServerChoices(
            appState.proxyServers.map { state ->
                TrafficConfigProxyGroupServerInput(
                    id = state.id,
                    server = state.server,
                )
            },
        )
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
            navigator.clearResult(proxyGroupResultKey)
            if (result.deleted) {
                val serverId = result.serverId
                if (serverId != null && serverId > 0) {
                    updateRaw(config.rawConfig.withoutShadowrocketProxyGroupLine(serverId))
                }
                return@collect
            }
            val strategy = result.server as? StrategyGroup ?: return@collect
            if (strategy.remarks.isBlank()) return@collect
            val line = strategy.toShadowrocketLine(serverChoices)
            val serverId = result.serverId
            if (serverId != null && serverId > 0) {
                updateRaw(config.rawConfig.withShadowrocketProxyGroupLine(serverId, line))
            } else {
                updateRaw(config.rawConfig.withShadowrocketProxyGroupAdded(line))
            }
        }
    }

    fun openNewGroup() {
        val newStrategy = newTrafficConfigProxyGroupStrategy(config.id)
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
    ) { _, listPadding, scrollBehavior ->
        SkipiTrafficConfigProxyGroups(
            groups = groups,
            contentPadding = listPadding,
            modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
            summary = stringResource(R.string.configs_proxy_groups_summary),
            addLabel = stringResource(R.string.configs_proxy_groups_add),
            emptyLabel = stringResource(R.string.configs_proxy_groups_empty),
            editContentDescription = stringResource(R.string.configs_edit),
            deleteContentDescription = stringResource(R.string.common_delete),
            onAddGroup = ::openNewGroup,
            onEditGroup = ::openEditGroup,
            onDeleteGroup = { group ->
                updateRaw(config.rawConfig.withoutShadowrocketProxyGroupLine(group.lineNumber))
            },
        )
    }
}
