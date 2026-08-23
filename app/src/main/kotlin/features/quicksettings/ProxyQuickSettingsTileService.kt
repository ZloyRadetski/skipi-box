// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import app.AppState
import app.effects.resolveActiveNetworkConfig
import app.MainActivity
import app.R
import app.modes.RunModeVpnService
import features.config.withActiveTrafficConfig
import data.AndroidAppStateStore
import data.AppSettingsPreferences
import engine.proxy.AndroidProxyEngine
import engine.vpn.SkipiVpnService
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import features.settings.locale.localizedAppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

class ProxyQuickSettingsTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateStore by lazy { AndroidAppStateStore.get(applicationContext) }
    private val proxyEngine by lazy {
        AndroidProxyEngine(
            context = applicationContext,
            requestVpnPermission = { intent ->
                withContext(Dispatchers.Main.immediate) {
                    launchActivityAndCollapse(intent)
                    false
                }
            },
        )
    }
    private val proxyServiceUseCase by lazy { ProxyServiceUseCase(proxyEngine) }

    override fun attachBaseContext(newBase: Context) {
        val languageMode = AppSettingsPreferences(newBase).load().languageMode
        super.attachBaseContext(newBase.localizedAppContext(languageMode))
    }

    override fun onTileAdded() {
        super.onTileAdded()
        activeService = WeakReference(this)
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        activeService = WeakReference(this)
        refreshTile()
    }

    override fun onStopListening() {
        clearActiveService()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        activeService = WeakReference(this)
        if (!operationInProgress.compareAndSet(false, true)) return

        val appContext = applicationContext
        // Do not touch the Room-backed state store here.  This callback is
        // invoked on the main thread when Android starts a cold app process
        // just for the tile; loading a large subscription before drawing the
        // connecting state made a tap appear to do nothing for seconds.
        updateTile(running = SkipiVpnService.isRunning(), processing = true)
        requestTileRefresh(appContext)
        operationScope.launch {
            var finalRunning: Boolean? = null
            try {
                finalRunning = toggleProxy()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                withContext(Dispatchers.Main.immediate) {
                    showToast(error.quickSettingsErrorMessage())
                }
            } finally {
                operationInProgress.set(false)
                withContext(Dispatchers.Main.immediate) {
                    refreshTileAfterToggle(appContext, finalRunning)
                }
            }
        }
    }

    override fun onDestroy() {
        clearActiveService()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun clearActiveService() {
        if (activeService?.get() === this) {
            activeService = null
        }
    }

    private fun refreshTile() {
        serviceScope.launch {
            val running = withContext(Dispatchers.Default) {
                if (operationInProgress.get()) null else syncProxyRunningState()
            }
            if (running == null) {
                updateTile(processing = true)
            } else {
                updateTile(running = running)
            }
        }
    }

    private suspend fun refreshTileState() {
        if (operationInProgress.get()) {
            updateTile(processing = true)
            return
        }
        val running = withContext(Dispatchers.Default) { syncProxyRunningState() }
        updateTile(running = running)
    }

    private suspend fun toggleProxy(): Boolean {
        val running = syncProxyRunningState()
        val rawState = stateStore.state.value.copy(proxyRunning = running)
        var state = if (!running) rawState.resolveActiveNetworkConfig(applicationContext) else rawState
        if (state.activeTrafficConfigId != rawState.activeTrafficConfigId) {
            stateStore.update { it.withActiveTrafficConfig(state.activeTrafficConfigId) }
        }
        if (!running && state.requiresVpnPermission()) {
            withContext(Dispatchers.Main.immediate) {
                showToast(getString(R.string.quick_settings_tile_vpn_permission_required))
                launchActivityAndCollapse(VpnService.prepare(this@ProxyQuickSettingsTileService))
            }
            stateStore.update { currentState -> currentState.copy(proxyRunning = false) }
            return false
        }

        val selectedServer = state.proxyServers.firstOrNull { server -> server.id == state.selectedProxyServerId }
        // We already have an authoritative runtime state from the service.
        // Calling toggle() would query it once more through the global engine
        // mutex and can make a quick-settings tap wait behind an old action.
        val result = if (running) {
            proxyServiceUseCase.stop(state.runMode)
        } else {
            proxyServiceUseCase.start(state, selectedServer)
        }
        when (result) {
            is ProxyServiceResult.Success -> {
                stateStore.update { currentState ->
                    currentState.copy(
                        proxyRunning = result.proxyRunning,
                        localProxyPort = result.appState?.localProxyPort ?: currentState.localProxyPort,
                    )
                }
                withContext(Dispatchers.Main.immediate) {
                    showToast(
                        if (result.proxyRunning) {
                            getString(R.string.proxy_server_list_service_started)
                        } else {
                            getString(R.string.proxy_server_list_service_stopped)
                        },
                    )
                }
                return result.proxyRunning
            }

            ProxyServiceResult.MissingServer -> {
                withContext(Dispatchers.Main.immediate) {
                    showToast(getString(R.string.proxy_server_list_select_first))
                }
                return running
            }

            is ProxyServiceResult.Failed -> {
                stateStore.update { currentState -> currentState.copy(proxyRunning = false) }
                withContext(Dispatchers.Main.immediate) {
                    showToast(result.error.quickSettingsErrorMessage())
                }
                return false
            }
        }
    }

    private fun Throwable.quickSettingsErrorMessage(): String {
        return message ?: getString(R.string.quick_settings_tile_toggle_failed)
    }

    private suspend fun syncProxyRunningState(): Boolean {
        val currentState = stateStore.state.value
        // The tile is in the same application process as the VPN service, so
        // this avoids taking AndroidProxyEngine's operation mutex just to draw
        // or toggle a tile.
        val running = SkipiVpnService.isRunning()
        if (currentState.proxyRunning != running) {
            stateStore.update { state -> state.copy(proxyRunning = running) }
        }
        return running
    }

    private fun AppState.requiresVpnPermission(): Boolean {
        return runMode == RunModeVpnService && VpnService.prepare(this@ProxyQuickSettingsTileService) != null
    }

    private fun updateTile(
        running: Boolean = SkipiVpnService.isRunning(),
        processing: Boolean = false,
    ) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_proxy)
        tile.label = getString(R.string.quick_settings_tile_label)
        tile.state = if (running || processing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                processing -> getString(R.string.quick_settings_tile_processing)
                running -> selectedProxyServerRemarks() ?: getString(R.string.quick_settings_tile_running)
                else -> getString(R.string.quick_settings_tile_stopped)
            }
        }
        tile.updateTile()
    }

    private fun selectedProxyServerRemarks(): String? {
        val state = stateStore.state.value
        return state.proxyServers
            .firstOrNull { server -> server.id == state.selectedProxyServerId }
            ?.server
            ?.getInfo()
            ?.remarks
            ?.takeIf(String::isNotBlank)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchActivityAndCollapse(intent: Intent?) {
        val targetIntent = (intent ?: Intent(this, MainActivity::class.java)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(targetIntent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val LogTag = "ProxyQuickSettingsTile"

        private val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val operationInProgress = AtomicBoolean(false)
        private var activeService: WeakReference<ProxyQuickSettingsTileService>? = null

        /**
         * The VPN can be stopped from its persistent notification, without a
         * tile click. Ask SystemUI to rebind the tile in that case so its cached
         * ACTIVE state never outlives the actual tunnel.
         */
        internal fun notifyVpnStateChanged(
            context: Context,
            running: Boolean,
        ) {
            activeService?.get()?.let { tileService ->
                tileService.serviceScope.launch {
                    tileService.updateTile(running = running)
                }
            }
            requestTileRefresh(context.applicationContext)
        }

        private suspend fun ProxyQuickSettingsTileService.refreshTileAfterToggle(
            context: Context,
            finalRunning: Boolean?,
        ) {
            runCatching {
                val tileService = activeService?.get() ?: this
                if (finalRunning != null) {
                    tileService.updateTile(running = finalRunning)
                } else {
                    tileService.refreshTileState()
                }
            }.onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to refresh quick settings tile after proxy toggle", error)
            }

            delay(TileRefreshSettleDelayMillis.milliseconds)
            requestTileRefresh(context)
        }

        private fun requestTileRefresh(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, ProxyQuickSettingsTileService::class.java),
                )
            }.onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to request quick settings tile refresh", error)
            }
        }

        private const val TileRefreshSettleDelayMillis = 1_000L
    }
}
