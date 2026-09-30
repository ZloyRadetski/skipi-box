// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeEffectHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the critical Home gestures on one typed path into Android host actions. */
internal class ProxyHomeAndroidEffectBridge(
    private val onToggleTunnel: suspend () -> Result<Unit>,
    private val onSelectServer: suspend (String) -> Result<Unit>,
    private val latencyCoordinator: ProxyHomeLatencyCoordinator? = null,
    private val onTestVisibleServers: (suspend (List<String>) -> Result<Unit>)? = null,
    private val onTestGroup: (suspend (String) -> Result<Unit>)? = null,
    private val onCancelLatencyTests: (suspend () -> Result<Unit>)? = null,
    private val onOtherEffect: suspend (ProxyHomeEffect) -> Result<Unit> = { Result.success(Unit) },
) : ProxyHomeEffectHandler {
    override suspend fun handle(effect: ProxyHomeEffect): Result<Unit> = when (effect) {
        ProxyHomeEffect.ToggleTunnel -> onToggleTunnel()
        is ProxyHomeEffect.SelectServer -> onSelectServer(effect.serverId)
        is ProxyHomeEffect.TestVisibleServers -> {
            latencyCoordinator?.testVisibleServers(effect.serverIds)
                ?: onTestVisibleServers?.invoke(effect.serverIds)
                ?: onOtherEffect(effect)
        }
        is ProxyHomeEffect.TestGroup -> {
            latencyCoordinator?.testGroup(effect.groupId)
                ?: onTestGroup?.invoke(effect.groupId)
                ?: onOtherEffect(effect)
        }
        ProxyHomeEffect.CancelLatencyTests -> {
            latencyCoordinator?.cancel()
                ?: onCancelLatencyTests?.invoke()
                ?: onOtherEffect(effect)
        }
        else -> onOtherEffect(effect)
    }
}

/** Observes persisted selection changes once; the existing service restart callback owns restart policy. */
internal fun CoroutineScope.observeHomeServerSelection(
    state: StateFlow<AppState>,
    onSelectionChanged: (Int) -> Unit,
): Job = launch {
    var previousServerId = state.value.selectedProxyServerId
    state
        .map { appState -> appState.selectedProxyServerId }
        .distinctUntilChanged()
        .collect { serverId ->
            if (serverId == previousServerId) return@collect
            previousServerId = serverId
            onSelectionChanged(serverId)
        }
}
