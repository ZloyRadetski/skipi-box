// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.AppState
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.collectAppState
import app.navigation.ProxyServerEditResult
import app.navigation.Route
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorProfileStore
import app.skipi.ui.config.SkipiTrafficConfigProxyGroupsEditorScreen

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
    val editor = rememberTrafficConfigProxyGroupEditorController(updateAppState)
    val config = appState.trafficConfigs.firstOrNull { it.id == trafficConfigId } ?: run {
        navigator.pop()
        return
    }
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

    val proxyGroupResultKey = remember(config.id) {
        "traffic-config-proxy-group-result-${config.id}"
    }

    LaunchedEffect(navigator, proxyGroupResultKey, serverChoices) {
        navigator.observeResult<ProxyServerEditResult>(proxyGroupResultKey).collect { result ->
            navigator.clearResult(proxyGroupResultKey)
            editor.saveProxyGroupResult(config, result, serverChoices)
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

    SkipiTrafficConfigProxyGroupsEditorScreen(
        profile = config,
        controller = editor,
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onAdd = ::openNewGroup,
        onEdit = ::openEditGroup,
        platformBackHandler = { back -> TrafficConfigEditorBackHandler(back) },
    )
}

/** The key under which a profile's own proxy group edited from Home returns its result. */
internal const val HomeConfigProxyGroupEditResultKey = "home-config-proxy-group-edit-result"

/**
 * Opens the editor of the one profile-owned group [strategy] stands for, for the Home list: the group is found
 * by its policy name in the profile and its result comes back under [HomeConfigProxyGroupEditResultKey].
 * Returns false when the profile or the group is gone.
 */
internal fun openProfileProxyGroupEditor(
    navigator: app.navigation.Navigator,
    state: AppState,
    strategy: features.proxy.server.model.StrategyGroup,
): Boolean {
    val config = state.trafficConfigs.firstOrNull { it.id == strategy.sourceTrafficConfigId } ?: return false
    val group = config.rawConfig.analyzeShadowrocketConfig().proxyGroups
        .firstOrNull { it.name.equals(strategy.sourcePolicyGroupName, ignoreCase = true) } ?: return false
    navigator.navigateForResult(
        route = Route.ProxyServerEditor(
            ps = group.toEditableStrategyGroup(config.id, state.trafficConfigProxyGroupServerChoices()),
            serverId = group.lineNumber,
            resultKey = HomeConfigProxyGroupEditResultKey,
        ),
        requestKey = HomeConfigProxyGroupEditResultKey,
    )
    return true
}

private fun AppState.trafficConfigProxyGroupServerChoices() = trafficConfigProxyGroupServerChoices(
    proxyServers.map { state -> TrafficConfigProxyGroupServerInput(id = state.id, server = state.server) },
)

/** Saves what the Home-opened profile group editor returns into the profile that owns the group. */
@Composable
internal fun HomeProfileProxyGroupEditResultHandler() {
    val stateStore = LocalAppStateStore.current
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val editor = rememberTrafficConfigProxyGroupEditorController(updateAppState)
    LaunchedEffect(navigator, editor) {
        navigator.observeResult<ProxyServerEditResult>(HomeConfigProxyGroupEditResultKey).collect { result ->
            navigator.clearResult(HomeConfigProxyGroupEditResultKey)
            val state = stateStore.state.value
            val configId = (result.server as? features.proxy.server.model.StrategyGroup)?.sourceTrafficConfigId
            val config = state.trafficConfigs.firstOrNull { it.id == configId } ?: return@collect
            editor.saveProxyGroupResult(config, result, state.trafficConfigProxyGroupServerChoices())
        }
    }
}

@Composable
private fun rememberTrafficConfigProxyGroupEditorController(
    updateAppState: ((AppState) -> AppState) -> Unit,
): TrafficConfigEditorController = remember(updateAppState) {
    TrafficConfigEditorController(
        TrafficConfigEditorProfileStore { profileId, transform ->
            var found = false
            updateAppState { state ->
                if (state.trafficConfigs.none { it.id == profileId }) {
                    state
                } else {
                    found = true
                    state.withUpdatedTrafficConfig(profileId, transform)
                }
            }
            found
        },
    )
}
