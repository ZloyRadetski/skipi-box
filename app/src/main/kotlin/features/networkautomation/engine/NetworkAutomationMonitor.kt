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
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationActionOutcome
import app.skipi.app.store.AppStateStore
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.vpn.NetworkHandoverRecoveryGate
import engine.vpn.SkipiVpnService
import data.repository.reconcileTrafficConfigProxyGroups
import features.config.withActiveTrafficConfigProfile
import features.config.withConfigProxyGroupsReflected
import features.logs.AppLog
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import features.networkautomation.policy.NetworkAutomationAction
import features.networkautomation.policy.NetworkAutomationDecision
import features.networkautomation.policy.NetworkAutomationPolicy
import features.networkautomation.policy.NetworkAutomationReconciler
import features.networkautomation.policy.NetworkReconcileInput
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
                    NetworkAutomationPolicy.observationPlan(
                        networkAutomationEnabled = state.enableNetworkAutomation,
                        onDemandVpnEnabled = state.enableOnDemandVpn,
                        rules = state.networkAutomationRules,
                    )
                }
                .distinctUntilChanged()
                .collect { plan ->
                    if (plan.shouldListen) {
                        if (networkCallback != null && observesWifiSsid != plan.requiresWifiSsid) {
                            unregisterCallback()
                        }
                        registerCallback(plan.requiresWifiSsid)
                    } else {
                        unregisterCallback()
                    }
                }
        }
    }

    /** Re-registers after the user explicitly grants or revokes SSID permission. */
    fun refresh() {
        val state = stateStore.currentState
        val plan = NetworkAutomationPolicy.observationPlan(
            networkAutomationEnabled = state.enableNetworkAutomation,
            onDemandVpnEnabled = state.enableOnDemandVpn,
            rules = state.networkAutomationRules,
        )
        unregisterCallback()
        if (plan.shouldListen) registerCallback(plan.requiresWifiSsid)
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
            AppLog.info(LogTag, "Network automation observer registered (initial network: $lastKnownNetworkId)")
        }.onFailure { error ->
            networkCallback = null
            observesWifiSsid = false
            observedPhysicalNetworks.clear()
            AppLog.warn(LogTag, "Failed to register network automation observer", error)
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
            AppLog.info(LogTag, "Network automation observer unregistered")
        }.onFailure { error ->
            AppLog.warn(LogTag, "Failed to unregister network automation observer", error)
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
                    NetworkAutomationPolicy.DisconnectedNetworkIdentifier
                }
                val previousNetworkId = lastKnownNetworkId
                lastKnownNetworkId = currentNetworkId

                // A stale ConnectivityManager snapshot can outlive onLost for
                // a short time. The callback lifecycle is authoritative here:
                // without an observed physical upstream, never start, restart,
                // or switch a VPN.
                if (currentNetworkId == NetworkAutomationPolicy.DisconnectedNetworkIdentifier) {
                    AppLog.info(LogTag, "Network automation: physical network unavailable; waiting for an observed upstream")
                    return@withLock
                }

                val isRunning = SkipiVpnService.isRunning()
                val previousForPlan = previousNetworkId

                val decision = NetworkAutomationEvaluator.evaluate(
                    context = appContext,
                    state = state,
                    capabilities = capabilities,
                    includeWifiSsid = includeWifiSsid,
                )
                val targetServerId = (decision as? NetworkAutomationDecision.SwitchServer)?.serverId
                val targetServer = targetServerId?.let { id -> state.proxyServers.firstOrNull { it.id == id } }
                val resolvedState = if (targetServer != null) state.resolveActiveNetworkConfig(appContext) else state

                val action = NetworkAutomationReconciler.reconcile(
                    NetworkReconcileInput(
                        networkAutomationEnabled = state.enableNetworkAutomation,
                        onDemandVpnEnabled = state.enableOnDemandVpn,
                        previousNetworkId = previousForPlan,
                        currentNetworkId = currentNetworkId,
                        vpnRunning = isRunning,
                        decision = if (targetServerId != null && targetServer == null) {
                            NetworkAutomationDecision.NoChange
                        } else {
                            decision
                        },
                        selectedServerId = state.selectedProxyServerId,
                        activeProfileChangeNeeded = state.activeTrafficConfigId != resolvedState.activeTrafficConfigId,
                    ),
                )

                when (action) {
                    NetworkAutomationAction.None -> Unit

                    NetworkAutomationAction.Disconnect -> {
                        AppLog.info(LogTag, "On-Demand VPN: Disconnecting VPN on network switch to $currentNetworkId")
                        withNetworkHandoverOwnership {
                            proxyServiceUseCase.stop(state.runMode)
                        }
                        stateStore.update { it.copy(proxyRunning = false) }
                    }

                    is NetworkAutomationAction.Restart -> {
                        val server = checkNotNull(targetServer)
                        AppLog.info(
                            LogTag,
                            "Network automation: Auto-switching server to #${server.id} (${server.server.getInfo().remarks}) on network $currentNetworkId",
                        )
                        val updatedState = state.withActiveTrafficConfigProfile(resolvedState.activeTrafficConfigId)
                            .withConfigProxyGroupsReflected().copy(selectedProxyServerId = server.id)
                        when (val result = withNetworkHandoverOwnership { proxyServiceUseCase.restart(updatedState, server) }) {
                            is ProxyServiceResult.Success -> applyStartedServer(server.id, resolvedState.activeTrafficConfigId, result)
                            is ProxyServiceResult.Failed -> AppLog.warn(LogTag, "Failed to restart VPN on new server", result.error)
                            ProxyServiceResult.MissingServer -> AppLog.warn(LogTag, "Failed to restart VPN on new server: server missing")
                        }
                    }

                    is NetworkAutomationAction.Start -> {
                        val server = checkNotNull(targetServer)
                        AppLog.info(
                            LogTag,
                            "On-Demand VPN: Auto-starting VPN on server #${server.id} (${server.server.getInfo().remarks}) on network transition to $currentNetworkId",
                        )
                        val updatedState = state.withActiveTrafficConfigProfile(resolvedState.activeTrafficConfigId)
                            .withConfigProxyGroupsReflected().copy(selectedProxyServerId = server.id)
                        when (val result = proxyServiceUseCase.start(updatedState, server)) {
                            is ProxyServiceResult.Success -> applyStartedServer(server.id, resolvedState.activeTrafficConfigId, result)
                            is ProxyServiceResult.Failed -> AppLog.warn(LogTag, "Failed to start On-Demand VPN", result.error)
                            ProxyServiceResult.MissingServer -> AppLog.warn(LogTag, "Failed to start On-Demand VPN: server missing")
                        }
                    }
                }
            }.onFailure { error ->
                if (error !is CancellationException) {
                    AppLog.error(LogTag, "Error during network automation reconciliation", error)
                }
            }
        }
    }

    private suspend fun applyStartedServer(serverId: Int, activeProfileId: Int, result: ProxyServiceResult.Success) {
        selectServerInSharedCatalog(serverId)
        stateStore.update {
            it.withActiveTrafficConfigProfile(activeProfileId).copy(
                proxyRunning = result.proxyRunning,
                localProxyPort = result.appState?.localProxyPort ?: it.localProxyPort,
            )
        }
        stateStore.reconcileTrafficConfigProxyGroups()
    }

    /**
     * A server switch/disconnect already recreates or tears down the entire
     * VPN. Hold the service's smaller physical-handover recovery until this
     * operation finishes; its operation-owned gate releases recovery in the
     * finally block even when the operation fails.
     */
    private suspend fun <T> withNetworkHandoverOwnership(operation: suspend () -> T): T {
        val token = NetworkHandoverRecoveryGate.beginExternalVpnOperation()
        return try {
            operation()
        } finally {
            NetworkHandoverRecoveryGate.completeExternalVpnOperation(token)
        }
    }

    private suspend fun selectServerInSharedCatalog(serverId: Int) {
        val result = stateStore.sharedApplicationStore.dispatchAndAwait(
            SharedApplicationAction.SelectProxyServer(serverId),
        )
        when (val outcome = result.outcome) {
            SharedApplicationActionOutcome.Completed -> Unit
            is SharedApplicationActionOutcome.Rejected -> {
                AppLog.warn(LogTag, "Network automation could not select server #$serverId: ${outcome.reason}")
            }
            is SharedApplicationActionOutcome.Failed -> {
                AppLog.warn(LogTag, "Network automation failed to select server #$serverId: ${outcome.reason}")
            }
        }
    }

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
