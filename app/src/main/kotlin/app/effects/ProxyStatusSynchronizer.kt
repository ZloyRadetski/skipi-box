// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.AppState
import app.collectAppState
import app.modes.RunModeVpnService
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProxyEngineStatus
import engine.stats.ProxyTrafficStatsRuntimeStore
import engine.stats.ProxyTrafficStatsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy

@Composable
internal fun ProxyStatusSynchronizer(
    stateStore: AndroidAppStateStore,
    proxyEngine: AndroidProxyEngine,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    val appState by stateStore.collectAppState()
    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    var foregroundSyncGeneration by remember(stateStore, proxyEngine) { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) foregroundSyncGeneration += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(stateStore, proxyEngine, foregroundSyncGeneration) {
        observeProxyStatus(
            states = stateStore.state,
            readStatus = { snapshot -> runCatching { proxyEngine.status(appState = snapshot) }.getOrNull() },
            updateAppState = updateAppState,
        )
    }
    LaunchedEffect(appContext, appState.proxyRunning, appState.enableTrafficStatsNotification) {
        if (!appState.enableTrafficStatsNotification) {
            ProxyTrafficStatsService.reconcile(appContext, null)
        } else if (appState.proxyRunning) {
            ProxyTrafficStatsRuntimeStore.read(appContext)?.let { runtime ->
                ProxyTrafficStatsService.reconcile(appContext, runtime)
            }
        } else if (ProxyTrafficStatsRuntimeStore.read(appContext)?.paused != true) {
            ProxyTrafficStatsService.reconcile(appContext, null)
        }
    }
}

internal suspend fun observeProxyStatus(
    states: Flow<AppState>,
    readStatus: suspend (AppState) -> ProxyEngineStatus?,
    updateAppState: (((AppState) -> AppState) -> Unit),
) {
    states.distinctUntilChangedBy { state -> state.proxyRunning }.collect { snapshot ->
        val status = readStatus(snapshot) ?: return@collect
        updateAppState { current ->
            if (current.proxyRunning == status.running && current.runMode == RunModeVpnService) current
            else current.copy(runMode = RunModeVpnService, proxyRunning = status.running)
        }
    }
}
