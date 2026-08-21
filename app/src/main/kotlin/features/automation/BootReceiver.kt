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
import features.config.withActiveTrafficConfig
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val LogTag = "BootReceiver"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        AndroidAppLogger.debug(LogTag, "Received broadcast: $action")

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
        if (action !in validActions) return

        val application = context.applicationContext as? SkipiApplication ?: return
        val stateStore = AndroidAppStateStore.get(application)
        val state = stateStore.state.value

        if (!state.autoConnectOnBoot) {
            AndroidAppLogger.debug(LogTag, "Auto-connect on boot is disabled in settings")
            return
        }

        if (VpnService.prepare(application) != null) {
            AndroidAppLogger.warn(LogTag, "Cannot auto-connect on boot: VPN permission not granted")
            return
        }

        application.appScope.launch(Dispatchers.IO) {
            val resolvedState = state.resolveActiveNetworkConfig(application)
            if (resolvedState.activeTrafficConfigId != state.activeTrafficConfigId) {
                stateStore.update { it.withActiveTrafficConfig(resolvedState.activeTrafficConfigId) }
            }
            val targetServer = resolvedState.proxyServers.firstOrNull { it.id == resolvedState.selectedProxyServerId }
            if (targetServer == null) {
                AndroidAppLogger.warn(LogTag, "Cannot auto-connect on boot: No active proxy server selected")
                return@launch
            }

            val proxyEngine = AndroidProxyEngine(application, requestVpnPermission = { false })
            val proxyServiceUseCase = ProxyServiceUseCase(proxyEngine)
            when (val result = proxyServiceUseCase.start(resolvedState, targetServer)) {
                is ProxyServiceResult.Success -> {
                    AndroidAppLogger.info(LogTag, "Auto-connected VPN on boot successfully")
                    stateStore.update { current ->
                        current.copy(
                            proxyRunning = result.proxyRunning,
                            localProxyPort = result.appState?.localProxyPort ?: current.localProxyPort,
                        )
                    }
                }
                is ProxyServiceResult.Failed -> {
                    AndroidAppLogger.error(LogTag, "Failed to auto-connect VPN on boot: ${result.error.message}", result.error)
                }
                ProxyServiceResult.MissingServer -> {
                    AndroidAppLogger.warn(LogTag, "Cannot auto-connect on boot: Missing server")
                }
            }
        }
    }
}
