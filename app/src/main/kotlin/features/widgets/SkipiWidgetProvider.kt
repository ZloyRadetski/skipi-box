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
import features.proxy.server.display.displayName
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
 * Base provider for both home screen variants. It serializes all widget-driven
 * tunnel changes so a rapid series of taps cannot start overlapping core operations.
 */
open class SkipiWidgetProvider : AppWidgetProvider() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        val appContext = context.applicationContext.localizedContext()
        when (intent.action) {
            ActionToggle -> handleToggle(appContext)
            ActionPreviousConfig -> handleSelection(appContext, WidgetSelection.Config, WidgetCycleDirection.Previous)
            ActionNextConfig -> handleSelection(appContext, WidgetSelection.Config, WidgetCycleDirection.Next)
            ActionPreviousServer -> handleSelection(appContext, WidgetSelection.Server, WidgetCycleDirection.Previous)
            ActionNextServer -> handleSelection(appContext, WidgetSelection.Server, WidgetCycleDirection.Next)
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        handleRefresh(context.applicationContext.localizedContext())
    }

    private fun handleRefresh(appContext: Context) {
        val result = goAsync()
        operationScope.launch {
            try {
                runCatching { SkipiWidgetRenderer.renderAll(appContext) }.onFailure { error ->
                    AndroidAppLogger.warn(LogTag, "Failed to render SKIPI home screen widgets", error)
                }
            } finally {
                result.finish()
            }
        }
    }

    private fun handleToggle(appContext: Context) {
        handleWidgetOperation(appContext) {
            toggleProxy(appContext)
        }
    }

    private fun handleSelection(
        appContext: Context,
        selection: WidgetSelection,
        direction: WidgetCycleDirection,
    ) {
        handleWidgetOperation(appContext) {
            changeSelection(appContext, selection, direction)
        }
    }

    private fun handleWidgetOperation(
        appContext: Context,
        operation: suspend () -> Unit,
    ) {
        if (!operationInProgress.compareAndSet(false, true)) return
        val result = goAsync()
        operationScope.launch {
            try {
                operation()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                AndroidAppLogger.warn(LogTag, "Failed to handle action from home screen widget", error)
                showToast(appContext, appContext.getString(R.string.quick_settings_tile_toggle_failed))
            } finally {
                try {
                    delay(WidgetRenderSettleDelayMillis.milliseconds)
                    runCatching { SkipiWidgetRenderer.renderAll(appContext) }.onFailure { error ->
                        AndroidAppLogger.warn(LogTag, "Failed to refresh SKIPI home screen widgets", error)
                    }
                } finally {
                    operationInProgress.set(false)
                    result.finish()
                }
            }
        }
    }

    private suspend fun toggleProxy(appContext: Context) {
        SkipiWidgetRenderer.renderAll(appContext, processing = true)
        val stateStore = AndroidAppStateStore.get(appContext)
        val proxyEngine = widgetProxyEngine(appContext)
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

    private suspend fun changeSelection(
        appContext: Context,
        selection: WidgetSelection,
        direction: WidgetCycleDirection,
    ) {
        val stateStore = AndroidAppStateStore.get(appContext)
        val proxyEngine = widgetProxyEngine(appContext)
        val running = syncProxyRunningState(appContext, stateStore, proxyEngine)
        val stateBefore = stateStore.state.value
        val optionIds = when (selection) {
            WidgetSelection.Config -> stateBefore.widgetConfigIds()
            WidgetSelection.Server -> stateBefore.widgetServerIds()
        }
        val currentId = when (selection) {
            WidgetSelection.Config -> stateBefore.activeTrafficConfigId
            WidgetSelection.Server -> stateBefore.selectedProxyServerId
        }
        val targetId = cycleWidgetOptionId(optionIds, currentId, direction)
        if (targetId == null) {
            val message = when (selection) {
                WidgetSelection.Config -> R.string.widget_no_config
                WidgetSelection.Server -> R.string.proxy_server_list_select_first
            }
            showToast(appContext, appContext.getString(message))
            return
        }

        val proposedState = stateBefore.withWidgetSelection(selection, targetId)
        if (proposedState.sameWidgetSelectionAs(stateBefore)) return
        val selectedServer = proposedState.proxyServers
            .firstOrNull { server -> server.id == proposedState.selectedProxyServerId }
        if (running && selectedServer == null) {
            showToast(appContext, appContext.getString(R.string.proxy_server_list_select_first))
            return
        }

        SkipiWidgetRenderer.renderAll(appContext, processing = true)
        stateStore.update { currentState -> currentState.withWidgetSelection(selection, targetId) }
        val selectedState = stateStore.state.value
        if (selectedState.sameWidgetSelectionAs(stateBefore)) return

        if (running) {
            when (
                val restartResult = ProxyServiceUseCase(proxyEngine).restart(
                    selectedState,
                    selectedState.proxyServers.firstOrNull { server -> server.id == selectedState.selectedProxyServerId },
                )
            ) {
                is ProxyServiceResult.Success -> {
                    stateStore.update { currentState ->
                        if (currentState.sameWidgetSelectionAs(selectedState)) {
                            currentState.copy(
                                proxyRunning = restartResult.proxyRunning,
                                localProxyPort = restartResult.appState?.localProxyPort ?: currentState.localProxyPort,
                            )
                        } else {
                            currentState
                        }
                    }
                }

                ProxyServiceResult.MissingServer -> {
                    showToast(appContext, appContext.getString(R.string.proxy_server_list_select_first))
                    return
                }

                is ProxyServiceResult.Failed -> {
                    stateStore.update { currentState ->
                        if (currentState.sameWidgetSelectionAs(selectedState)) {
                            currentState.copy(proxyRunning = false)
                        } else {
                            currentState
                        }
                    }
                    showToast(
                        appContext,
                        restartResult.error.message ?: appContext.getString(R.string.quick_settings_tile_toggle_failed),
                    )
                    return
                }
            }
        }

        val message = when (selection) {
            WidgetSelection.Config -> appContext.getString(
                R.string.widget_config_changed,
                selectedState.trafficConfigs.firstOrNull { config ->
                    config.id == selectedState.activeTrafficConfigId
                }?.name?.ifBlank { "#${selectedState.activeTrafficConfigId}" } ?: "#${selectedState.activeTrafficConfigId}",
            )

            WidgetSelection.Server -> appContext.getString(
                R.string.widget_server_changed,
                selectedState.proxyServers.firstOrNull { server ->
                    server.id == selectedState.selectedProxyServerId
                }?.displayName() ?: "#${selectedState.selectedProxyServerId}",
            )
        }
        showToast(appContext, message)
    }

    private fun AppState.withWidgetSelection(
        selection: WidgetSelection,
        targetId: Int,
    ): AppState {
        return when (selection) {
            WidgetSelection.Config -> {
                if (targetId in widgetConfigIds()) withActiveTrafficConfig(targetId) else this
            }

            WidgetSelection.Server -> {
                if (targetId in widgetServerIds()) copy(selectedProxyServerId = targetId) else this
            }
        }
    }

    private fun AppState.sameWidgetSelectionAs(other: AppState): Boolean {
        return activeTrafficConfigId == other.activeTrafficConfigId &&
            selectedProxyServerId == other.selectedProxyServerId
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

    private fun widgetProxyEngine(appContext: Context): AndroidProxyEngine {
        return AndroidProxyEngine(
            context = appContext,
            requestVpnPermission = { intent ->
                launchMainActivity(appContext, intent)
                false
            },
        )
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
        const val ActionPreviousConfig = "features.widgets.action.PREVIOUS_CONFIG"
        const val ActionNextConfig = "features.widgets.action.NEXT_CONFIG"
        const val ActionPreviousServer = "features.widgets.action.PREVIOUS_SERVER"
        const val ActionNextServer = "features.widgets.action.NEXT_SERVER"
        const val ActionRefresh = "features.widgets.action.REFRESH"

        private const val LogTag = "SkipiWidgetProvider"
        private const val WidgetRenderSettleDelayMillis = 1_000L

        private val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val operationInProgress = AtomicBoolean(false)
    }
}

private enum class WidgetSelection {
    Config,
    Server,
}
