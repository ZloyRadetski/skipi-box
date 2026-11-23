// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.SkipiApplication
import app.effects.resolveActiveNetworkConfig
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import data.repository.reconcileTrafficConfigProxyGroups
import features.config.withActiveTrafficConfigProfile
import features.logs.AppLog
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val LogTag = "BootReceiver"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        AppLog.debug(LogTag, "Received broadcast: $action")

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
        if (action !in validActions) return

        val application = context.applicationContext as? SkipiApplication ?: return
        // Without goAsync() the system treats the broadcast as finished when onReceive returns and may freeze the
        // process before the state is loaded and the service started. The state store is opened off the main thread.
        val pendingResult = goAsync()

        val work = application.appScope.launch(Dispatchers.IO) {
            val stateStore = AndroidAppStateStore.get(application)
            stateStore.awaitReady()
            val state = stateStore.currentState

            if (!state.autoConnectOnBoot) {
                AppLog.debug(LogTag, "Auto-connect on boot is disabled in settings")
                return@launch
            }

            if (VpnService.prepare(application) != null) {
                AppLog.warn(LogTag, "Cannot auto-connect on boot: VPN permission not granted")
                return@launch
            }

            val resolvedState = state.resolveActiveNetworkConfig(application)
            if (resolvedState.activeTrafficConfigId != state.activeTrafficConfigId) {
                stateStore.update { it.withActiveTrafficConfigProfile(resolvedState.activeTrafficConfigId) }
                stateStore.reconcileTrafficConfigProxyGroups()
            }
            val targetServer = resolvedState.proxyServers.firstOrNull { it.id == resolvedState.selectedProxyServerId }
            if (targetServer == null) {
                AppLog.warn(LogTag, "Cannot auto-connect on boot: No active proxy server selected")
                return@launch
            }

            val proxyEngine = AndroidProxyEngine(application, requestVpnPermission = { false })
            val proxyServiceUseCase = ProxyServiceUseCase(proxyEngine)
            when (val result = proxyServiceUseCase.start(resolvedState, targetServer)) {
                is ProxyServiceResult.Success -> {
                    AppLog.info(LogTag, "Auto-connected VPN on boot successfully")
                    stateStore.update { current ->
                        current.copy(
                            proxyRunning = result.proxyRunning,
                            localProxyPort = result.appState?.localProxyPort ?: current.localProxyPort,
                        )
                    }
                }
                is ProxyServiceResult.Failed -> {
                    AppLog.error(LogTag, "Failed to auto-connect VPN on boot: ${result.error.message}", result.error)
                }
                ProxyServiceResult.MissingServer -> {
                    AppLog.warn(LogTag, "Cannot auto-connect on boot: Missing server")
                }
            }
        }

        // The broadcast is released when the work ends or its time budget does, whichever comes first; the work
        // itself keeps running in the application scope.
        application.appScope.launch(Dispatchers.IO) {
            try {
                withTimeoutOrNull(BroadcastBudgetMillis) { work.join() }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private const val BroadcastBudgetMillis = 8_000L
