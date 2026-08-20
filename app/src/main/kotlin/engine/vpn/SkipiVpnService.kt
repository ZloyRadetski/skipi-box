// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ProxyInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import app.R
import app.effects.matchingNetworkConfigId
import features.networkautomation.engine.NetworkAutomationDecision
import features.networkautomation.engine.NetworkAutomationEvaluator
import data.AndroidAppStateStore
import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import engine.network.NetworkDefaults
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import engine.proxy.AndroidProxyEngine
import engine.vpn.hevtun.HevTunRuntime
import engine.xray.clearCoreLogs
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import system.getInstalledApplicationsCompat
import utils.toTrimmedNonEmptyDistinctList
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("VpnServicePolicy")
class SkipiVpnService : VpnService() {
    private var tunFileDescriptor: ParcelFileDescriptor? = null
    private var hevTunRuntime: HevTunRuntime? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val networkSwitchMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateStore by lazy { AndroidAppStateStore.get(applicationContext) }
    private val networkProxyEngine by lazy {
        AndroidProxyEngine(applicationContext, requestVpnPermission = { false })
    }
    private val networkProxyServiceUseCase by lazy { ProxyServiceUseCase(networkProxyEngine) }
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SkipiVpnServiceIntents.ACTION_STOP -> {
                serviceScope.launch {
                    try {
                        operationMutex.withLock {
                            stopVpn()
                        }
                    } finally {
                        completeStop()
                        stopSelfOnMain(startId)
                    }
                }
            }

            SkipiVpnServiceIntents.ACTION_START -> {
                val config = intent.readVpnServiceStartConfig()
                if (config == null) {
                    completeStart(Result.failure(IllegalStateException(getString(R.string.error_vpn_start_config_missing))))
                    stopSelf(startId)
                    return Service.START_NOT_STICKY
                }
                serviceScope.launch {
                    operationMutex.withLock {
                        runCatching {
                            startVpn(config)
                        }.onSuccess {
                            completeStart(Result.success(Unit))
                        }.onFailure { error ->
                            AndroidAppLogger.error(LogTag, "Failed to start VPN Service", error)
                            stopVpn()
                            completeStart(Result.failure(error))
                            stopSelfOnMain(startId)
                        }
                    }
                }
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.launch {
            runCatching {
                operationMutex.withLock {
                    stopVpn()
                }
            }.onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to stop VPN Service while destroying service", error)
            }
            serviceJob.cancel()
        }
        super.onDestroy()
    }

    override fun onRevoke() {
        running = false
        super.onRevoke()
    }

    private fun stopSelfOnMain(startId: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopSelf(startId)
        } else {
            mainHandler.post { stopSelf(startId) }
        }
    }

    private fun startVpn(config: VpnServiceStartConfig) {
        stopVpn()
        if (config.enableWakeLock) {
            acquireWakeLock()
        }
        config.coreLogPaths.clearCoreLogs(LogTag)
        tunFileDescriptor = establishTun(config)
        val tunFd = tunFileDescriptor?.fd ?: error(getString(R.string.error_vpn_tun_fd_unavailable))
        AndroidLibXrayLiteRuntime.start(
            context = this,
            config = config,
            tunFd = config.xrayTunFd(tunFd),
        )
        config.hevSocks5TunnelConfig?.let { hevConfig ->
            val runtime = hevTunRuntime ?: HevTunRuntime().also { hevTunRuntime = it }
            runtime.start(hevConfig, tunFd)
            AndroidAppLogger.info(LogTag, "Started Hev TUN with VPN file descriptor")
        }
        LocalProxyRuntime.update(config.localProxyOptions)
        running = true
        registerNetworkConfigCallback()
    }

    private fun establishTun(config: VpnServiceStartConfig): ParcelFileDescriptor {
        val builder = Builder()
            .setSession(config.sessionName)
            .setMtu(config.mtu)
            .addAddress(config.ipv4Address, config.ipv4PrefixLength)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        if (config.enableIpv6 && config.ipv6Address != null) {
            builder
                .addAddress(config.ipv6Address, config.ipv6PrefixLength)
        }

        builder.applyVpnRoutes(config)

        if (config.enableLocalDns) {
            config.dnsServers.forEach { dnsServer ->
                builder.addDnsServer(dnsServer)
            }
        }

        builder.applyApplicationPolicy(config)
        builder.applyAppendHttpProxy(config)

        val pfd = builder.establish() ?: error(getString(R.string.error_vpn_tunnel_establish_failed))
        if (config.enableSeamlessNetworkSwitching) {
            val connectivityManager = getSystemService(ConnectivityManager::class.java)
            val active = connectivityManager?.activeNetwork
            val activeCaps = active?.let { connectivityManager.getNetworkCapabilities(it) }
            if (active != null && activeCaps != null && !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                setUnderlyingNetworks(arrayOf(active))
            }
        }
        return pfd
    }

    private fun Builder.applyVpnRoutes(config: VpnServiceStartConfig): Builder {
        addRoute(NetworkDefaults.IPV4_ANY_ADDRESS, 0)
        if (config.enableIpv6 && config.ipv6Address != null) {
            addRoute(NetworkDefaults.IPV6_ANY_ADDRESS, 0)
        }
        return this
    }

    private fun Builder.applyApplicationPolicy(config: VpnServiceStartConfig): Builder {
        val policy = config.applicationPolicy
        val selfPackageName = packageName
        when (policy.mode) {
            ProxyAppListModeWhitelist -> {
                val allowedCount = addAllowedApplications(policy.packageNames.filterNot { it.trim() == selfPackageName })
                if (allowedCount == 0) {
                    // An empty allowed list means "all apps" to Android, so use a full deny list instead.
                    addDisallowedApplications(installedPackageNames())
                }
            }

            ProxyAppListModeBlacklist -> {
                addDisallowedApplications(policy.packageNames + selfPackageName)
            }

            ProxyAppListModeGlobal -> {
                addDisallowedApplications(listOf(selfPackageName))
            }

            else -> Unit
        }
        AndroidAppLogger.info(LogTag, "Excluded self package from VPN routing: $selfPackageName")
        return this
    }

    private fun Builder.applyAppendHttpProxy(config: VpnServiceStartConfig): Builder {
        if (config.appendHttpProxyOptions.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setHttpProxy(ProxyInfo.buildDirectProxy(LocalProxyLoopbackAddress, config.appendHttpProxyOptions.port))
            AndroidAppLogger.info(
                LogTag,
                "Appended VPN HTTP proxy: $LocalProxyLoopbackAddress:${config.appendHttpProxyOptions.port}",
            )
        }
        return this
    }

    private fun Builder.addAllowedApplications(packageNames: List<String>): Int {
        return packageNames.toTrimmedNonEmptyDistinctList().count { packageName ->
            addApplicationIfInstalled(packageName) {
                addAllowedApplication(packageName)
            }
        }
    }

    private fun Builder.addDisallowedApplications(packageNames: List<String>): Int {
        return packageNames.toTrimmedNonEmptyDistinctList().count { packageName ->
            addApplicationIfInstalled(packageName) {
                addDisallowedApplication(packageName)
            }
        }
    }

    private fun addApplicationIfInstalled(packageName: String, addApplication: () -> Unit): Boolean {
        return runCatching {
            addApplication()
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                if (error is PackageManager.NameNotFoundException) {
                    false
                } else {
                    throw error
                }
            },
        )
    }

    private fun installedPackageNames(): List<String> {
        return packageManager.getInstalledApplicationsCompat()
            .map { applicationInfo -> applicationInfo.packageName }
            .distinct()
    }

    private fun stopVpn() {
        unregisterNetworkConfigCallback()
        releaseWakeLock()
        runCatching {
            hevTunRuntime?.stop()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to stop Hev TUN while stopping VPN Service", error)
        }
        runCatching {
            AndroidLibXrayLiteRuntime.stop()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to stop AndroidLibXrayLite while stopping VPN Service", error)
        }
        runCatching {
            tunFileDescriptor?.close()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to close VPN TUN file descriptor", error)
        }
        tunFileDescriptor = null
        LocalProxyRuntime.clear()
        running = false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "skipi:vpn_tunnel")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            AndroidAppLogger.info(LogTag, "Acquired partial WakeLock for VPN tunnel")
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to acquire partial WakeLock", error)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to release partial WakeLock", error)
        }
        wakeLock = null
    }

    /** Keeps automatic Wi-Fi/LTE profile switching and seamless underlying network binding alive. */
    private fun registerNetworkConfigCallback() {
        if (networkCallback != null) return
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: return
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                if (stateStore.state.value.enableSeamlessNetworkSwitching) {
                    setUnderlyingNetworks(arrayOf(network))
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                if (stateStore.state.value.enableSeamlessNetworkSwitching) {
                    setUnderlyingNetworks(arrayOf(network))
                }
            }

            override fun onLost(network: Network) {
                if (stateStore.state.value.enableSeamlessNetworkSwitching) {
                    val active = connectivityManager.activeNetwork
                    val activeCaps = active?.let { connectivityManager.getNetworkCapabilities(it) }
                    val isPhysical = active != null && activeCaps != null && !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    if (isPhysical) {
                        setUnderlyingNetworks(arrayOf(active))
                    } else {
                        setUnderlyingNetworks(null)
                    }
                }
            }
        }
        networkCallback = callback
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
            val active = connectivityManager.activeNetwork
            val activeCaps = active?.let { connectivityManager.getNetworkCapabilities(it) }
            if (active != null && activeCaps != null && !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && stateStore.state.value.enableSeamlessNetworkSwitching) {
                setUnderlyingNetworks(arrayOf(active))
            }
        }.onFailure { error ->
            networkCallback = null
            AndroidAppLogger.warn(LogTag, "Failed to observe network changes for seamless routing", error)
        }
    }

    private fun unregisterNetworkConfigCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to stop network observer", error)
        }
    }

    companion object {
        private const val LogTag = "SkipiVpnService"

        @Volatile
        private var running = false

        private val startMutex = Mutex()

        @Volatile
        private var pendingStart: CompletableDeferred<Result<Unit>>? = null

        internal suspend fun start(context: Context, config: VpnServiceStartConfig) = startMutex.withLock {
            val result = CompletableDeferred<Result<Unit>>()
            pendingStart = result
            try {
                context.startService(SkipiVpnServiceIntents.startIntent(context, config))
                withTimeout(15_000.milliseconds) {
                    result.await()
                }.getOrThrow()
            } finally {
                if (pendingStart === result) {
                    pendingStart = null
                }
            }
        }

        @Volatile
        private var pendingStop: CompletableDeferred<Unit>? = null

        internal suspend fun stop(context: Context) = startMutex.withLock {
            if (!running) return@withLock
            val result = CompletableDeferred<Unit>()
            pendingStop = result
            running = false
            try {
                context.startService(SkipiVpnServiceIntents.stopIntent(context))
                withTimeout(5_000.milliseconds) {
                    result.await()
                }
            } catch (_: Throwable) {
                // Ignore stop timeout
            } finally {
                if (pendingStop === result) {
                    pendingStop = null
                }
            }
        }

        internal fun isRunning(): Boolean {
            return running && AndroidLibXrayLiteRuntime.isRunning()
        }

        private fun completeStart(result: Result<Unit>) {
            pendingStart?.complete(result)
            pendingStart = null
        }

        private fun completeStop() {
            pendingStop?.complete(Unit)
            pendingStop = null
        }

    }
}
