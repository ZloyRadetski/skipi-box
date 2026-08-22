// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import app.AppState
import app.MainActivity
import app.R
import app.effects.resolveActiveNetworkConfig
import app.modes.RunModeVpnService
import data.AndroidAppStateStore
import data.AppSettingsPreferences
import engine.proxy.AndroidProxyEngine
import features.config.withActiveTrafficConfig
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import features.settings.locale.localizedAppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Home screen widget: toggles the proxy and shows the connection status,
 * the selected server and live traffic speeds.
 */
class SkipiWidgetProvider : AppWidgetProvider() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        val appContext = context.applicationContext.localizedContext()
        when (intent.action) {
            ActionToggle -> handleToggle(appContext)
            ActionRefresh -> handleRefresh(appContext)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        handleRefresh(context.applicationContext.localizedContext())
    }

    private fun handleRefresh(appContext: Context) {
        val result = goAsync()
        operationScope.launch {
            runCatching { SkipiWidgetRenderer.renderAll(appContext) }.onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to render SKIPI home screen widgets", error)
            }
            result.finish()
        }
    }

    private fun handleToggle(appContext: Context) {
        if (!operationInProgress.compareAndSet(false, true)) return
        val result = goAsync()
        operationScope.launch {
            try {
                toggleProxy(appContext)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                AndroidAppLogger.warn(LogTag, "Failed to toggle proxy from home screen widget", error)
                showToast(appContext, appContext.getString(R.string.quick_settings_tile_toggle_failed))
            } finally {
                operationInProgress.set(false)
            }
            delay(WidgetRenderSettleDelayMillis.milliseconds)
            runCatching { SkipiWidgetRenderer.renderAll(appContext) }
            result.finish()
        }
    }

    private suspend fun toggleProxy(appContext: Context) {
        SkipiWidgetRenderer.renderAll(appContext, processing = true)
        val stateStore = AndroidAppStateStore.get(appContext)
        val proxyEngine =
            AndroidProxyEngine(
                context = appContext,
                requestVpnPermission = { intent ->
                    launchMainActivity(appContext, intent)
                    false
                },
            )
        val proxyServiceUseCase = ProxyServiceUseCase(proxyEngine)

        val running = syncProxyRunningState(appContext, stateStore, proxyEngine)
        val rawState = stateStore.state.value.copy(proxyRunning = running)
        var state = if (!running) rawState.resolveActiveNetworkConfig(appContext) else rawState
        if (state.activeTrafficConfigId != rawState.activeTrafficConfigId) {
            stateStore.update { it.withActiveTrafficConfig(state.activeTrafficConfigId) }
        }
        if (!running && state.requiresVpnPermission(appContext)) {
            showToast(appContext, appContext.getString(R.string.quick_settings_tile_vpn_permission_required))
            launchMainActivity(appContext, VpnService.prepare(appContext))
            stateStore.update { currentState -> currentState.copy(proxyRunning = false) }
            return
        }

        val selectedServer = state.proxyServers.firstOrNull { server -> server.id == state.selectedProxyServerId }
        when (val result = proxyServiceUseCase.toggle(state, selectedServer)) {
            is ProxyServiceResult.Success -> {
                stateStore.update { currentState ->
                    currentState.copy(
                        proxyRunning = result.proxyRunning,
                        localProxyPort = result.appState?.localProxyPort ?: currentState.localProxyPort,
                    )
                }
                showToast(
                    appContext,
                    appContext.getString(
                        if (result.proxyRunning) {
                            R.string.proxy_server_list_service_started
                        } else {
                            R.string.proxy_server_list_service_stopped
                        },
                    ),
                )
            }

            ProxyServiceResult.MissingServer -> {
                showToast(appContext, appContext.getString(R.string.proxy_server_list_select_first))
            }

            is ProxyServiceResult.Failed -> {
                stateStore.update { currentState -> currentState.copy(proxyRunning = false) }
                showToast(
                    appContext,
                    result.error.message ?: appContext.getString(R.string.quick_settings_tile_toggle_failed),
                )
            }
        }
    }

    private suspend fun syncProxyRunningState(
        appContext: Context,
        stateStore: AndroidAppStateStore,
        proxyEngine: AndroidProxyEngine,
    ): Boolean {
        val currentState = stateStore.state.value
        val running =
            runCatching { proxyEngine.status(appState = currentState).running }
                .onFailure { error ->
                    AndroidAppLogger.warn(LogTag, "Failed to read proxy status from home screen widget", error)
                }
                .getOrElse { currentState.proxyRunning }
        if (currentState.proxyRunning != running) {
            stateStore.update { state -> state.copy(proxyRunning = running) }
        }
        return running
    }

    private fun AppState.requiresVpnPermission(appContext: Context): Boolean {
        return runMode == RunModeVpnService && VpnService.prepare(appContext) != null
    }

    private fun launchMainActivity(
        appContext: Context,
        extraIntent: Intent?,
    ) {
        val targetIntent =
            (extraIntent ?: Intent(appContext, MainActivity::class.java)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        appContext.startActivity(targetIntent)
    }

    private fun showToast(
        appContext: Context,
        message: String,
    ) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun Context.localizedContext(): Context {
        val languageMode = AppSettingsPreferences(this).load().languageMode
        return localizedAppContext(languageMode)
    }

    internal companion object {
        const val ActionToggle = "features.widgets.action.TOGGLE_PROXY"
        const val ActionRefresh = "features.widgets.action.REFRESH"

        private const val LogTag = "SkipiWidgetProvider"
        private const val WidgetRenderSettleDelayMillis = 1_000L

        private val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val operationInProgress = AtomicBoolean(false)
    }
}
