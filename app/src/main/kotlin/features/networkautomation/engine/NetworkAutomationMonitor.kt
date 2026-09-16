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
import engine.vpn.NetworkHandoverRecoveryGate
import engine.vpn.SkipiVpnService
import features.config.withActiveTrafficConfig
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
    private val observedPhysicalNetworks = PhysicalNetworkAvailabilityTracker<Network>()
    @Volatile
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var debounceJob: Job? = null
    private var lastKnownNetworkId: String? = null
    private var observesWifiSsid = false

    fun start() {
        scope.launch {
            stateStore.state
                .map { state ->
                    NetworkAutomationConfigKey(
                        enableNetworkAutomation = state.enableNetworkAutomation,
                        enableOnDemandVpn = state.enableOnDemandVpn,
                        rulesCount = state.networkAutomationRules.count { it.enabled },
                        requiresWifiSsid = NetworkAutomationEvaluator.requiresWifiSsid(
                            state.networkAutomationRules.filter { it.enabled },
                        ),
                    )
                }
                .distinctUntilChanged()
                .collect { key ->
                    val shouldListen = (key.enableNetworkAutomation || key.enableOnDemandVpn) && key.rulesCount > 0
                    if (shouldListen) {
                        if (networkCallback != null && observesWifiSsid != key.requiresWifiSsid) {
                            unregisterCallback()
                        }
                        registerCallback(key.requiresWifiSsid)
                    } else {
                        unregisterCallback()
                    }
                }
        }
    }

    /** Re-registers after the user explicitly grants or revokes SSID permission. */
    fun refresh() {
        val state = stateStore.currentState
        val enabledRules = state.networkAutomationRules.filter { it.enabled }
        val shouldListen = (state.enableNetworkAutomation || state.enableOnDemandVpn) && enabledRules.isNotEmpty()
        unregisterCallback()
        if (shouldListen) {
            registerCallback(NetworkAutomationEvaluator.requiresWifiSsid(enabledRules))
        }
    }

    private fun registerCallback(requiresWifiSsid: Boolean) {
        if (networkCallback != null) return
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return

        observedPhysicalNetworks.clear()
        val includeWifiSsid = requiresWifiSsid && hasFineLocationPermission()
        observesWifiSsid = includeWifiSsid
        lastKnownNetworkId = NetworkAutomationEvaluator.getPhysicalNetworkIdentifier(
            context = appContext,
            capabilities = null,
            includeWifiSsid = includeWifiSsid,
        )

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && includeWifiSsid) {
            runCatching {
                object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onAvailable(network: Network) {
                        onPhysicalNetworkAvailable(this, network)
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        onPhysicalNetworkCapabilitiesChanged(this, network, networkCapabilities)
                    }

                    override fun onLost(network: Network) {
                        onPhysicalNetworkLost(this, network)
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
            observesWifiSsid = false
            observedPhysicalNetworks.clear()
            AndroidAppLogger.warn(LogTag, "Failed to register network automation observer", error)
        }
    }

    private fun createDefaultCallback() = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onPhysicalNetworkAvailable(this, network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            onPhysicalNetworkCapabilitiesChanged(this, network, networkCapabilities)
        }

        override fun onLost(network: Network) {
            onPhysicalNetworkLost(this, network)
        }
    }

    private fun onPhysicalNetworkAvailable(callback: ConnectivityManager.NetworkCallback, network: Network) {
        if (networkCallback !== callback) return
        observedPhysicalNetworks.markAvailable(network)
        scheduleEvaluation(null)
    }

    private fun onPhysicalNetworkCapabilitiesChanged(
        callback: ConnectivityManager.NetworkCallback,
        network: Network,
        capabilities: NetworkCapabilities,
    ) {
        if (networkCallback !== callback) return
        if (capabilities.isPhysicalInternetNetwork()) {
            observedPhysicalNetworks.markAvailable(network)
        } else {
            observedPhysicalNetworks.markLost(network)
        }
        scheduleEvaluation(capabilities)
    }

    private fun onPhysicalNetworkLost(callback: ConnectivityManager.NetworkCallback, network: Network) {
        if (networkCallback !== callback) return
        observedPhysicalNetworks.markLost(network)
        scheduleEvaluation(null)
    }

    private fun unregisterCallback() {
        val callback = networkCallback
        networkCallback = null
        lastKnownNetworkId = null
        observesWifiSsid = false
        debounceJob?.cancel()
        debounceJob = null
        NetworkHandoverRecoveryGate.clearNetworkAutomationEvaluation()
        observedPhysicalNetworks.clear()
        if (callback == null) return
        runCatching {
            appContext.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
            AndroidAppLogger.info(LogTag, "Network automation observer unregistered")
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to unregister network automation observer", error)
        }
    }

    fun scheduleEvaluation(capabilities: NetworkCapabilities?) {
        debounceJob?.cancel()
        val handoverToken = NetworkHandoverRecoveryGate.beginNetworkAutomationEvaluation()
        debounceJob = scope.launch(Dispatchers.Default) {
            try {
                delay(1000)
                withContext(NonCancellable) {
                    reconcile(capabilities)
                }
            } finally {
                NetworkHandoverRecoveryGate.completeNetworkAutomationEvaluation(handoverToken)
            }
        }
    }

    private suspend fun reconcile(capabilities: NetworkCapabilities?) {
        operationMutex.withLock {
            runCatching {
                val state = stateStore.state.value
                if (!state.enableNetworkAutomation && !state.enableOnDemandVpn) return@withLock
                val enabledRules = state.networkAutomationRules.filter { it.enabled }
                val includeWifiSsid = NetworkAutomationEvaluator.requiresWifiSsid(enabledRules) &&
                    hasFineLocationPermission()

                val hasObservedPhysicalNetwork = observedPhysicalNetworks.hasAvailableNetwork()
                val currentNetworkId = if (hasObservedPhysicalNetwork) {
                    NetworkAutomationEvaluator.getPhysicalNetworkIdentifier(
                        context = appContext,
                        capabilities = capabilities,
                        includeWifiSsid = includeWifiSsid,
                    )
                } else {
                    NetworkAutomationEvaluator.DisconnectedNetworkIdentifier
                }
                val previousNetworkId = lastKnownNetworkId
                val isNetworkTransition = previousNetworkId != null && previousNetworkId != currentNetworkId
                lastKnownNetworkId = currentNetworkId

                // A stale ConnectivityManager snapshot can outlive onLost for
                // a short time. The callback lifecycle is authoritative here:
                // without an observed physical upstream, never start, restart,
                // or switch a VPN.
                if (currentNetworkId == NetworkAutomationEvaluator.DisconnectedNetworkIdentifier) {
                    AndroidAppLogger.info(LogTag, "Network automation: physical network unavailable; waiting for an observed upstream")
                    return@withLock
                }

                val isRunning = SkipiVpnService.isRunning()

                // If network did not transition and VPN is currently stopped (e.g. user manually stopped VPN on LTE),
                // do not auto-connect.
                if (!isNetworkTransition && !isRunning) {
                    return@withLock
                }

                val decision = NetworkAutomationEvaluator.evaluate(
                    context = appContext,
                    state = state,
                    capabilities = capabilities,
                    includeWifiSsid = includeWifiSsid,
                )

                when (decision) {
                    is NetworkAutomationDecision.DisconnectVpn -> {
                        if (state.enableOnDemandVpn && isRunning && isNetworkTransition) {
                            AndroidAppLogger.info(LogTag, "On-Demand VPN: Disconnecting VPN on network switch to $currentNetworkId")
                            withNetworkHandoverOwnership {
                                proxyServiceUseCase.stop(state.runMode)
                            }
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
                                val updatedState = state.withActiveTrafficConfig(resolvedState.activeTrafficConfigId).copy(
                                    selectedProxyServerId = targetServerId,
                                )
                                when (val result = withNetworkHandoverOwnership {
                                    proxyServiceUseCase.restart(updatedState, targetServer)
                                }) {
                                    is ProxyServiceResult.Success -> stateStore.update {
                                        it.withActiveTrafficConfig(resolvedState.activeTrafficConfigId).copy(
                                            selectedProxyServerId = targetServerId,
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
                                val updatedState = state.withActiveTrafficConfig(resolvedState.activeTrafficConfigId).copy(
                                    selectedProxyServerId = targetServerId,
                                )
                                when (val result = proxyServiceUseCase.start(updatedState, targetServer)) {
                                    is ProxyServiceResult.Success -> stateStore.update {
                                        it.withActiveTrafficConfig(resolvedState.activeTrafficConfigId).copy(
                                            selectedProxyServerId = targetServerId,
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

    /**
     * A server switch/disconnect already recreates or tears down the entire
     * VPN. Hold the service's smaller physical-handover recovery until this
     * operation finishes; its bounded gate releases recovery on failure too.
     */
    private suspend fun <T> withNetworkHandoverOwnership(operation: suspend () -> T): T {
        val token = NetworkHandoverRecoveryGate.beginExternalVpnOperation()
        return try {
            operation()
        } finally {
            NetworkHandoverRecoveryGate.completeExternalVpnOperation(token)
        }
    }

    private data class NetworkAutomationConfigKey(
        val enableNetworkAutomation: Boolean,
        val enableOnDemandVpn: Boolean,
        val rulesCount: Int,
        val requiresWifiSsid: Boolean,
    )

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val LogTag = "NetworkAutomationMonitor"
    }
}
