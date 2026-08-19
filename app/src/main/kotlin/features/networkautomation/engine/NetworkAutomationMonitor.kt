// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.vpn.SkipiVpnService
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
                        scheduleEvaluation(null)
                    } else {
                        unregisterCallback()
                    }
                }
        }
    }

    private fun registerCallback() {
        if (networkCallback != null) return
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                .build()
            cm.registerNetworkCallback(request, callback)
            AndroidAppLogger.info(LogTag, "Network automation observer registered successfully")
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
            delay(500)
            reconcile(capabilities)
        }
    }

    private suspend fun reconcile(capabilities: NetworkCapabilities?) {
        operationMutex.withLock {
            val state = stateStore.state.value
            if (!state.enableNetworkAutomation && !state.enableOnDemandVpn) return

            val decision = NetworkAutomationEvaluator.evaluate(appContext, state, capabilities)
            val isRunning = SkipiVpnService.isRunning()

            when (decision) {
                is NetworkAutomationDecision.DisconnectVpn -> {
                    if (state.enableOnDemandVpn && isRunning) {
                        AndroidAppLogger.info(LogTag, "On-Demand VPN: Disconnecting VPN due to network rule")
                        proxyServiceUseCase.stop(state.runMode)
                        stateStore.update { it.copy(proxyRunning = false) }
                    }
                }

                is NetworkAutomationDecision.SwitchServer -> {
                    val targetServerId = decision.serverId
                    val targetServer = state.proxyServers.firstOrNull { it.id == targetServerId } ?: return

                    if (isRunning) {
                        if (state.enableNetworkAutomation && state.selectedProxyServerId != targetServerId) {
                            AndroidAppLogger.info(
                                LogTag,
                                "Network automation: Auto-switching server to #${targetServer.id} (${targetServer.server.getInfo().remarks})",
                            )
                            stateStore.update { it.copy(selectedProxyServerId = targetServerId) }
                            val updatedState = stateStore.state.value
                            when (val result = proxyServiceUseCase.restart(updatedState, targetServer)) {
                                is ProxyServiceResult.Success -> stateStore.update {
                                    it.copy(proxyRunning = result.proxyRunning)
                                }
                                else -> AndroidAppLogger.warn(LogTag, "Failed to restart VPN on new server")
                            }
                        }
                    } else {
                        if (state.enableOnDemandVpn) {
                            AndroidAppLogger.info(
                                LogTag,
                                "On-Demand VPN: Auto-starting VPN on server #${targetServer.id} (${targetServer.server.getInfo().remarks})",
                            )
                            stateStore.update { it.copy(selectedProxyServerId = targetServerId) }
                            val updatedState = stateStore.state.value
                            when (val result = proxyServiceUseCase.toggle(updatedState, targetServer)) {
                                is ProxyServiceResult.Success -> stateStore.update {
                                    it.copy(proxyRunning = result.proxyRunning)
                                }
                                else -> AndroidAppLogger.warn(LogTag, "Failed to start On-Demand VPN")
                            }
                        }
                    }
                }

                NetworkAutomationDecision.NoChange -> Unit
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
