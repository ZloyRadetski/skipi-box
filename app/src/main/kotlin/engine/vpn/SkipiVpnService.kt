// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ProxyInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.MainActivity
import app.R
import data.AndroidAppStateStore
import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import engine.network.NetworkDefaults
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import engine.stats.ProxyTrafficStatsRuntimeStore
import engine.stats.ProxyTrafficStatsService
import features.quicksettings.ProxyQuickSettingsTileService
import engine.vpn.hevtun.HevTunRuntime
import engine.xray.clearCoreLogs
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import system.getInstalledApplicationsCompat
import utils.toTrimmedNonEmptyDistinctList
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("VpnServicePolicy")
class SkipiVpnService : VpnService() {
    private var tunFileDescriptor: ParcelFileDescriptor? = null
    private var hevTunRuntime: HevTunRuntime? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateStore by lazy { AndroidAppStateStore.get(applicationContext) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val contentIntent by lazy {
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentConfig: VpnServiceStartConfig? = null
    private var lastObservedPhysicalNetwork: Network? = null
    private var hadActivePhysicalNetwork: Boolean = false
    private var ownsForegroundNotification = false
    @Volatile
    private var networkReloadScheduled = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return Service.START_NOT_STICKY
        val operationId = intent.vpnServiceOperationId()
        if (operationId == null) {
            AndroidAppLogger.warn(LogTag, "Ignoring VPN service request without an operation id: action=$action")
            if (action == SkipiVpnServiceIntents.ACTION_START) {
                stopSelfOnMain(startId)
            }
            return Service.START_NOT_STICKY
        }

        when (action) {
            SkipiVpnServiceIntents.ACTION_STOP -> {
                val keepForegroundNotification = intent.keepVpnForegroundNotification()
                serviceScope.launch {
                    var handled = false
                    try {
                        operationMutex.withLock {
                            if (!isLatestOperation(operationId)) return@withLock
                            stopVpn(
                                notificationDisposition = if (keepForegroundNotification) {
                                    ForegroundNotificationDisposition.Detach
                                } else {
                                    ForegroundNotificationDisposition.Remove
                                },
                            )
                            handled = true
                        }
                    } finally {
                        completeStop(operationId)
                        if (handled && isLatestOperation(operationId)) {
                            stopSelfOnMain(startId)
                        }
                    }
                }
            }

            SkipiVpnServiceIntents.ACTION_START -> {
                val config = VpnServiceStartConfigStore.take(operationId)
                if (config == null) {
                    val error = IllegalStateException(getString(R.string.error_vpn_start_config_missing))
                    if (isLatestOperation(operationId)) {
                        completeStart(operationId, Result.failure(error))
                    }
                    stopSelfOnMain(startId)
                    return Service.START_NOT_STICKY
                }
                if (!isLatestOperation(operationId)) {
                    stopSelfOnMain(startId)
                    return Service.START_NOT_STICKY
                }
                runCatching {
                    promoteVpnForeground(config, connecting = true)
                }.onFailure { error ->
                    AndroidAppLogger.error(LogTag, "Failed to enter foreground before VPN start", error)
                    completeStart(operationId, Result.failure(error))
                    stopSelfOnMain(startId)
                    return Service.START_NOT_STICKY
                }
                serviceScope.launch {
                    var failed = false
                    operationMutex.withLock startLock@{
                        if (!isLatestOperation(operationId)) return@startLock
                        runCatching {
                            startVpn(config)
                        }.onSuccess {
                            if (isLatestOperation(operationId)) {
                                completeStart(operationId, Result.success(Unit))
                            } else {
                                // The caller timed out or explicitly stopped
                                // while core startup was in progress. Never
                                // leave a late, unowned tunnel running.
                                stopVpn(ForegroundNotificationDisposition.Remove)
                            }
                        }.onFailure { error ->
                            AndroidAppLogger.error(LogTag, "Failed to start VPN Service", error)
                            stopVpn(
                                notificationDisposition = ForegroundNotificationDisposition.Remove,
                            )
                            if (isLatestOperation(operationId)) {
                                completeStart(operationId, Result.failure(error))
                                failed = true
                            }
                        }
                    }
                    if (failed && isLatestOperation(operationId)) {
                        stopSelfOnMain(startId)
                    }
                }
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        invalidateCurrentOperation()
        serviceScope.launch {
            runCatching {
                operationMutex.withLock {
                    stopVpn(ForegroundNotificationDisposition.Remove)
                }
            }.onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to stop VPN Service while destroying service", error)
            }
            serviceJob.cancel()
        }
        super.onDestroy()
    }

    override fun onRevoke() {
        invalidateCurrentOperation()
        running = false
        serviceScope.launch {
            runCatching {
                operationMutex.withLock {
                    stopVpn(ForegroundNotificationDisposition.Remove)
                }
            }.onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to clean up revoked VPN Service", error)
            }
            stateStore.update { state -> state.copy(proxyRunning = false) }
            ProxyTrafficStatsRuntimeStore.clear(applicationContext)
            ProxyTrafficStatsService.reconcile(applicationContext, null)
            stopSelfOnMain()
        }
        super.onRevoke()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        SkipiCoreRuntime.forceFreeMemory()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        SkipiCoreRuntime.forceFreeMemory()
    }

    private fun stopSelfOnMain(startId: Int? = null) {
        val stop = {
            if (startId == null) stopSelf() else stopSelf(startId)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stop()
        } else {
            mainHandler.post(stop)
        }
    }

    private fun startVpn(config: VpnServiceStartConfig) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        stopVpn(ForegroundNotificationDisposition.Keep)
        val stoppedAt = android.os.SystemClock.elapsedRealtime()
        currentConfig = config
        lastObservedPhysicalNetwork = null
        hadActivePhysicalNetwork = false
        if (config.enableWakeLock) {
            acquireWakeLock()
        }
        config.coreLogPaths.clearCoreLogs(LogTag)
        tunFileDescriptor = establishTun(config)
        val tunReadyAt = android.os.SystemClock.elapsedRealtime()
        val tunFd = tunFileDescriptor?.fd ?: error(getString(R.string.error_vpn_tun_fd_unavailable))
        SkipiCoreRuntime.start(
            context = this,
            config = config,
            tunFd = config.xrayTunFd(tunFd),
        )
        val coreReadyAt = android.os.SystemClock.elapsedRealtime()
        config.hevSocks5TunnelConfig?.let { hevConfig ->
            val runtime = hevTunRuntime ?: HevTunRuntime().also { hevTunRuntime = it }
            runtime.start(hevConfig, tunFd)
            AndroidAppLogger.info(LogTag, "Started Hev TUN with VPN file descriptor")
        }
        val hevReadyAt = android.os.SystemClock.elapsedRealtime()
        LocalProxyRuntime.update(config.localProxyOptions)
        running = true
        promoteVpnForeground(config, connecting = false)
        registerNetworkConfigCallback()
        ProxyQuickSettingsTileService.notifyVpnStateChanged(applicationContext, running = true)
        AndroidAppLogger.info(
            LogTag,
            "VPN start timing: stop=${stoppedAt - startedAt}ms, tun=${tunReadyAt - stoppedAt}ms, core=${coreReadyAt - tunReadyAt}ms, hev=${hevReadyAt - coreReadyAt}ms, total=${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
        )
    }

    private fun establishTun(config: VpnServiceStartConfig): ParcelFileDescriptor {
        val builder = Builder()
            .setSession(config.sessionName)
            .setMtu(config.mtu)
            .addAddress(config.ipv4Address, config.ipv4PrefixLength)

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
            runCatching { setUnderlyingNetworks(null) }
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

    private fun stopVpn(
        notificationDisposition: ForegroundNotificationDisposition = ForegroundNotificationDisposition.Remove,
    ) {
        unregisterNetworkConfigCallback()
        releaseWakeLock()
        currentConfig = null
        lastObservedPhysicalNetwork = null
        hadActivePhysicalNetwork = false
        runCatching {
            hevTunRuntime?.stop()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to stop Hev TUN while stopping VPN Service", error)
        }
        runCatching {
            SkipiCoreRuntime.stop()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to stop SKIPI Core while stopping VPN Service", error)
        }
        runCatching {
            tunFileDescriptor?.close()
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to close VPN TUN file descriptor", error)
        }
        tunFileDescriptor = null
        LocalProxyRuntime.clear()
        running = false
        applyForegroundNotificationDisposition(notificationDisposition)
        if (notificationDisposition != ForegroundNotificationDisposition.Keep) {
            ProxyQuickSettingsTileService.notifyVpnStateChanged(applicationContext, running = false)
        }
    }

    private fun promoteVpnForeground(
        config: VpnServiceStartConfig,
        connecting: Boolean,
    ) {
        VpnForegroundNotification.ensureChannel(this, notificationManager)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, VpnForegroundNotification.ChannelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setSmallIcon(R.drawable.ic_qs_proxy)
            .setContentTitle(config.sessionName.ifBlank { getString(R.string.app_name) })
            .setContentText(
                getString(
                    if (connecting) R.string.quick_settings_tile_processing else R.string.quick_settings_tile_running,
                ),
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                VpnForegroundNotification.NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(VpnForegroundNotification.NotificationId, notification)
        }
        ownsForegroundNotification = true
    }

    private fun applyForegroundNotificationDisposition(
        notificationDisposition: ForegroundNotificationDisposition,
    ) {
        if (!ownsForegroundNotification || notificationDisposition == ForegroundNotificationDisposition.Keep) return
        stopForeground(
            when (notificationDisposition) {
                ForegroundNotificationDisposition.Detach -> STOP_FOREGROUND_DETACH
                ForegroundNotificationDisposition.Remove -> STOP_FOREGROUND_REMOVE
                ForegroundNotificationDisposition.Keep -> error("Keep is handled above")
            },
        )
        ownsForegroundNotification = false
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
                handlePhysicalNetworkChange(network, connectivityManager)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                handlePhysicalNetworkChange(network, connectivityManager)
            }

            override fun onLost(network: Network) {
                if (lastObservedPhysicalNetwork == network) {
                    lastObservedPhysicalNetwork = null
                }
                if (stateStore.state.value.enableSeamlessNetworkSwitching) {
                    val active = connectivityManager.activeNetwork
                    val activeCaps = active?.let { connectivityManager.getNetworkCapabilities(it) }
                    val isPhysical = active != null && activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false &&
                        activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    runCatching {
                        setUnderlyingNetworks(if (isPhysical) arrayOf(active) else null)
                    }
                    if (isPhysical) {
                        handlePhysicalNetworkChange(checkNotNull(active), connectivityManager)
                    }
                }
            }
        }
        networkCallback = callback
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
            if (stateStore.state.value.enableSeamlessNetworkSwitching) {
                runCatching { setUnderlyingNetworks(null) }
            }
        }.onFailure { error ->
            networkCallback = null
            AndroidAppLogger.warn(LogTag, "Failed to observe network changes for seamless routing", error)
        }
    }

    private fun handlePhysicalNetworkChange(network: Network, connectivityManager: ConnectivityManager) {
        if (!stateStore.state.value.enableSeamlessNetworkSwitching) return
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return

        val previousNetwork = lastObservedPhysicalNetwork
        val isNetworkSwitch = hadActivePhysicalNetwork && (previousNetwork == null || previousNetwork != network)
        lastObservedPhysicalNetwork = network
        hadActivePhysicalNetwork = true

        runCatching { setUnderlyingNetworks(arrayOf(network)) }

        if (isNetworkSwitch && running) {
            AndroidAppLogger.info(LogTag, "Physical network switched to $network, refreshing proxy core connections")
            reloadCoreOnNetworkChange()
        }
    }

    private fun reloadCoreOnNetworkChange() {
        if (networkReloadScheduled) return
        networkReloadScheduled = true
        serviceScope.launch {
            try {
                operationMutex.withLock {
                    val config = currentConfig ?: return@withLock
                    if (!running) return@withLock

                    runCatching {
                        AndroidAppLogger.info(LogTag, "Restarting core on network handover to flush stale sockets")
                        runCatching { hevTunRuntime?.stop() }
                        runCatching { SkipiCoreRuntime.stop() }
                        runCatching { tunFileDescriptor?.close() }

                        tunFileDescriptor = establishTun(config)
                        val newTunFd = tunFileDescriptor?.fd ?: error(getString(R.string.error_vpn_tun_fd_unavailable))

                        SkipiCoreRuntime.start(
                            context = this@SkipiVpnService,
                            config = config,
                            tunFd = config.xrayTunFd(newTunFd),
                        )
                        config.hevSocks5TunnelConfig?.let { hevConfig ->
                            val runtime = hevTunRuntime ?: HevTunRuntime().also { hevTunRuntime = it }
                            runtime.start(hevConfig, newTunFd)
                        }
                        AndroidAppLogger.info(LogTag, "Successfully reloaded proxy core on new network")
                    }.onFailure { error ->
                        AndroidAppLogger.error(LogTag, "Failed to reload proxy core on network handover", error)
                        stopVpn(ForegroundNotificationDisposition.Remove)
                        stateStore.update { state -> state.copy(proxyRunning = false) }
                        ProxyTrafficStatsRuntimeStore.clear(applicationContext)
                        ProxyTrafficStatsService.reconcile(applicationContext, null)
                        stopSelfOnMain()
                    }
                }
            } finally {
                networkReloadScheduled = false
            }
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

    private enum class ForegroundNotificationDisposition {
        /** A replacement configuration is about to take ownership immediately. */
        Keep,
        /** The paused traffic notification has already become the foreground owner. */
        Detach,
        /** The tunnel is fully disconnected. */
        Remove,
    }

    companion object {
        private const val LogTag = "SkipiVpnService"

        @Volatile
        private var running = false

        private val startMutex = Mutex()
        private val operationSequence = AtomicLong()
        private val latestOperationId = AtomicLong()

        @Volatile
        private var pendingStart: PendingStart? = null

        internal suspend fun start(context: Context, config: VpnServiceStartConfig) = startMutex.withLock {
            val request = PendingStart(
                operationId = nextOperationId(),
                result = CompletableDeferred(),
            )
            pendingStart = request
            try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    SkipiVpnServiceIntents.startIntent(
                        context = context.applicationContext,
                        config = config,
                        operationId = request.operationId,
                    ),
                )
                withTimeout(15_000.milliseconds) {
                    request.result.await()
                }.getOrThrow()
            } catch (error: TimeoutCancellationException) {
                invalidateOperation(request.operationId)
                VpnServiceStartConfigStore.remove(request.operationId)
                throw IllegalStateException("VPN service start timed out", error)
            } catch (error: Throwable) {
                invalidateOperation(request.operationId)
                VpnServiceStartConfigStore.remove(request.operationId)
                throw error
            } finally {
                if (pendingStart === request) {
                    pendingStart = null
                }
            }
        }

        @Volatile
        private var pendingStop: PendingStop? = null

        internal suspend fun stop(
            context: Context,
            keepForegroundNotification: Boolean = false,
        ) = startMutex.withLock {
            val request = PendingStop(
                operationId = nextOperationId(),
                result = CompletableDeferred(),
            )
            pendingStop = request
            running = false
            try {
                context.applicationContext.startService(
                    SkipiVpnServiceIntents.stopIntent(
                        context = context.applicationContext,
                        operationId = request.operationId,
                        keepForegroundNotification = keepForegroundNotification,
                    ),
                )
                withTimeout(5_000.milliseconds) {
                    request.result.await()
                }
            } catch (error: TimeoutCancellationException) {
                // Keep this operation current. A delayed STOP must still
                // tear down the old core unless a newer START supersedes it.
                AndroidAppLogger.warn(LogTag, "VPN service stop timed out", error)
            } catch (error: Throwable) {
                AndroidAppLogger.warn(LogTag, "Failed to request VPN service stop", error)
            } finally {
                if (pendingStop === request) {
                    pendingStop = null
                }
            }
        }

        internal fun isRunning(): Boolean {
            return running && SkipiCoreRuntime.isRunning()
        }

        private fun nextOperationId(): Long {
            return operationSequence.incrementAndGet().also(latestOperationId::set)
        }

        private fun invalidateOperation(operationId: Long) {
            val invalidationId = operationSequence.incrementAndGet()
            latestOperationId.compareAndSet(operationId, invalidationId)
        }

        private fun invalidateCurrentOperation() {
            latestOperationId.set(operationSequence.incrementAndGet())
        }

        private fun isLatestOperation(operationId: Long): Boolean {
            return latestOperationId.get() == operationId
        }

        private fun completeStart(operationId: Long, result: Result<Unit>) {
            val request = pendingStart
            if (request != null && request.operationId == operationId) {
                request.result.complete(result)
                pendingStart = null
            }
        }

        private fun completeStop(operationId: Long) {
            val request = pendingStop
            if (request != null && request.operationId == operationId) {
                request.result.complete(Unit)
                pendingStop = null
            }
        }

        private data class PendingStart(
            val operationId: Long,
            val result: CompletableDeferred<Result<Unit>>,
        )

        private data class PendingStop(
            val operationId: Long,
            val result: CompletableDeferred<Unit>,
        )
    }
}
