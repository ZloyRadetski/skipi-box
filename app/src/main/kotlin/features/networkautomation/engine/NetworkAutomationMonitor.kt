// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
import app.effects.resolveActiveNetworkConfig
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.vpn.SkipiVpnService
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class NetworkAutomationMonitor(
    context: Context,
    private val stateStore: AndroidAppStateStore,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val proxyServiceUseCase = ProxyServiceUseCase(AndroidProxyEngine(appContext) { false })
    private val operationMutex = Mutex()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var debounceJob: Job? = null
    private var lastKnownNetworkId: String? = null

    fun start() {
        scope.launch {
            stateStore.state
                .map { state ->
                    NetworkAutomationConfigKey(
                        enableNetworkAutomation = state.enableNetworkAutomation,
                        enableOnDemandVpn = state.enableOnDemandVpn,
                        rulesCount = state.networkAutomationRules.count { it.enabled },
                    )
                }
                .distinctUntilChanged()
                .collect { key ->
                    val shouldListen = (key.enableNetworkAutomation || key.enableOnDemandVpn) && key.rulesCount > 0
                    if (shouldListen) {
                        registerCallback()
                    } else {
                        unregisterCallback()
                    }
                }
        }
    }

    private fun registerCallback() {
        if (networkCallback != null) return
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return

        lastKnownNetworkId = NetworkAutomationEvaluator.getPhysicalNetworkIdentifier(appContext, null)

        val hasFineLocation = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasFineLocation) {
            runCatching {
                object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onAvailable(network: Network) {
                        scheduleEvaluation(null)
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        scheduleEvaluation(networkCapabilities)
                    }

                    override fun onLost(network: Network) {
                        scheduleEvaluation(null)
                    }
                }
            }.getOrElse {
                createDefaultCallback()
            }
        } else {
            createDefaultCallback()
        }

        networkCallback = callback
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            cm.registerNetworkCallback(request, callback)
            AndroidAppLogger.info(LogTag, "Network automation observer registered (initial network: $lastKnownNetworkId)")
        }.onFailure { error ->
            networkCallback = null
            AndroidAppLogger.warn(LogTag, "Failed to register network automation observer", error)
        }
    }

    private fun createDefaultCallback() = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleEvaluation(null)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            scheduleEvaluation(networkCapabilities)
        }

        override fun onLost(network: Network) {
            scheduleEvaluation(null)
        }
    }

    private fun unregisterCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        lastKnownNetworkId = null
        runCatching {
            appContext.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
            AndroidAppLogger.info(LogTag, "Network automation observer unregistered")
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to unregister network automation observer", error)
        }
    }

    fun scheduleEvaluation(capabilities: NetworkCapabilities?) {
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.Default) {
            delay(1000)
            withContext(NonCancellable) {
                reconcile(capabilities)
            }
        }
    }

    private suspend fun reconcile(capabilities: NetworkCapabilities?) {
        operationMutex.withLock {
            runCatching {
                val state = stateStore.state.value
                if (!state.enableNetworkAutomation && !state.enableOnDemandVpn) return@withLock

                val currentNetworkId = NetworkAutomationEvaluator.getPhysicalNetworkIdentifier(appContext, capabilities)
                val previousNetworkId = lastKnownNetworkId
                val isNetworkTransition = previousNetworkId != null && previousNetworkId != currentNetworkId
                lastKnownNetworkId = currentNetworkId

                val isRunning = SkipiVpnService.isRunning()

                // If network did not transition and VPN is currently stopped (e.g. user manually stopped VPN on LTE),
                // do not auto-connect.
                if (!isNetworkTransition && !isRunning) {
                    return@withLock
                }

                val decision = NetworkAutomationEvaluator.evaluate(appContext, state, capabilities)

                when (decision) {
                    is NetworkAutomationDecision.DisconnectVpn -> {
                        if (state.enableOnDemandVpn && isRunning && isNetworkTransition) {
                            AndroidAppLogger.info(LogTag, "On-Demand VPN: Disconnecting VPN on network switch to $currentNetworkId")
                            proxyServiceUseCase.stop(state.runMode)
                            stateStore.update { it.copy(proxyRunning = false) }
                        }
                    }

                    is NetworkAutomationDecision.SwitchServer -> {
                        val targetServerId = decision.serverId
                        val targetServer = state.proxyServers.firstOrNull { it.id == targetServerId } ?: return@withLock

                        val resolvedState = state.resolveActiveNetworkConfig(appContext)
                        val needsServerChange = state.selectedProxyServerId != targetServerId
                        val needsConfigChange = state.activeTrafficConfigId != resolvedState.activeTrafficConfigId

                        if (isRunning) {
                            if (state.enableNetworkAutomation && isNetworkTransition && (needsServerChange || needsConfigChange)) {
                                AndroidAppLogger.info(
                                    LogTag,
                                    "Network automation: Auto-switching server to #${targetServer.id} (${targetServer.server.getInfo().remarks}) on network $currentNetworkId",
                                )
                                val updatedState = state.copy(
                                    selectedProxyServerId = targetServerId,
                                    activeTrafficConfigId = resolvedState.activeTrafficConfigId,
                                )
                                when (val result = proxyServiceUseCase.restart(updatedState, targetServer)) {
                                    is ProxyServiceResult.Success -> stateStore.update {
                                        it.copy(
                                            selectedProxyServerId = targetServerId,
                                            activeTrafficConfigId = resolvedState.activeTrafficConfigId,
                                            proxyRunning = result.proxyRunning,
                                            localProxyPort = result.appState?.localProxyPort ?: it.localProxyPort,
                                        )
                                    }
                                    is ProxyServiceResult.Failed -> AndroidAppLogger.warn(LogTag, "Failed to restart VPN on new server", result.error)
                                    ProxyServiceResult.MissingServer -> AndroidAppLogger.warn(LogTag, "Failed to restart VPN on new server: server missing")
                                }
                            }
                        } else {
                            if (state.enableOnDemandVpn && isNetworkTransition && !decision.requireAlreadyRunning) {
                                AndroidAppLogger.info(
                                    LogTag,
                                    "On-Demand VPN: Auto-starting VPN on server #${targetServer.id} (${targetServer.server.getInfo().remarks}) on network transition to $currentNetworkId",
                                )
                                val updatedState = state.copy(
                                    selectedProxyServerId = targetServerId,
                                    activeTrafficConfigId = resolvedState.activeTrafficConfigId,
                                )
                                when (val result = proxyServiceUseCase.start(updatedState, targetServer)) {
                                    is ProxyServiceResult.Success -> stateStore.update {
                                        it.copy(
                                            selectedProxyServerId = targetServerId,
                                            activeTrafficConfigId = resolvedState.activeTrafficConfigId,
                                            proxyRunning = result.proxyRunning,
                                            localProxyPort = result.appState?.localProxyPort ?: it.localProxyPort,
                                        )
                                    }
                                    is ProxyServiceResult.Failed -> AndroidAppLogger.warn(LogTag, "Failed to start On-Demand VPN", result.error)
                                    ProxyServiceResult.MissingServer -> AndroidAppLogger.warn(LogTag, "Failed to start On-Demand VPN: server missing")
                                }
                            }
                        }
                    }

                    NetworkAutomationDecision.NoChange -> Unit
                }
            }.onFailure { error ->
                if (error !is CancellationException) {
                    AndroidAppLogger.error(LogTag, "Error during network automation reconciliation", error)
                }
            }
        }
    }

    private data class NetworkAutomationConfigKey(
        val enableNetworkAutomation: Boolean,
        val enableOnDemandVpn: Boolean,
        val rulesCount: Int,
    )

    companion object {
        private const val LogTag = "NetworkAutomationMonitor"
    }
}
