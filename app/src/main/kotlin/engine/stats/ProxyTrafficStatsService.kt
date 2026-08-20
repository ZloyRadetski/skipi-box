// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.R
import app.activeTunnelTargetDisplayName
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.vpn.SkipiVpnService
import engine.xray.XrayStatsApiTag
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class ProxyTrafficStatsService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val stateStore by lazy { AndroidAppStateStore.get(applicationContext) }
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
    private val pauseIntent by lazy {
        PendingIntent.getService(
            this,
            PauseRequestCode,
            Intent(this, ProxyTrafficStatsService::class.java).setAction(ActionPause),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private val resumeIntent by lazy {
        PendingIntent.getService(
            this,
            ResumeRequestCode,
            Intent(this, ProxyTrafficStatsService::class.java).setAction(ActionResume),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private val disconnectIntent by lazy {
        PendingIntent.getService(
            this,
            DisconnectRequestCode,
            Intent(this, ProxyTrafficStatsService::class.java).setAction(ActionDisconnect),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private val notificationProxyEngine by lazy {
        AndroidProxyEngine(applicationContext, requestVpnPermission = { false })
    }
    private val notificationProxyServiceUseCase by lazy {
        ProxyServiceUseCase(notificationProxyEngine)
    }
    private var pollingJob: Job? = null
    private var activeRuntime: ProxyTrafficStatsRuntime? = null
    private var accumulator = XrayTrafficSessionAccumulator()
    private var latestSample = EmptyTrafficSample
    private var previousOutboundTotals = emptyMap<String, XrayTrafficBytes>()
    private var activeOutboundTag: String? = null
    private var activeTargetName = ""
    private var resumeInProgress = false
    private var isScreenInteractive = true

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    val runtime = activeRuntime
                    if (runtime != null && !runtime.paused) {
                        notificationManager.notify(
                            NotificationId,
                            buildNotification(runtime, latestSample),
                        )
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        isScreenInteractive = powerManager?.isInteractive ?: true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ActionStop) {
            stopStats()
            return START_NOT_STICKY
        }
        if (intent?.action == ActionPause) {
            pauseVpn()
            return START_STICKY
        }
        if (intent?.action == ActionResume) {
            resumeVpn()
            return START_STICKY
        }
        if (intent?.action == ActionDisconnect) {
            disconnectVpn()
            return START_NOT_STICKY
        }

        val runtime = ProxyTrafficStatsRuntimeStore.read(this) ?: intent?.readRuntime()
        if (runtime == null) {
            stopStats()
            return START_NOT_STICKY
        }

        if (runtime == activeRuntime && (runtime.paused || pollingJob?.isActive == true)) {
            return START_STICKY
        }
        if (runtime != activeRuntime) {
            accumulator = XrayTrafficSessionAccumulator()
            latestSample = EmptyTrafficSample
            previousOutboundTotals = emptyMap()
            activeOutboundTag = null
            activeTargetName = runtime.serverName
        }
        activeRuntime = runtime
        startStats(runtime)
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenStateReceiver) }
        pollingJob?.cancel()
        pollingJob = null
        activeRuntime = null
        latestSample = EmptyTrafficSample
        resumeInProgress = false
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startStats(runtime: ProxyTrafficStatsRuntime) {
        pollingJob?.cancel()
        val initialNotification = buildNotification(
            runtime = runtime,
            sample = EmptyTrafficSample,
        )
        val foregroundStarted = runCatching {
            startForegroundCompat(initialNotification)
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to start traffic stats foreground service", error)
            stopStats()
        }.isSuccess
        if (!foregroundStarted) return

        if (runtime.paused) return

        val sessionAccumulator = accumulator
        pollingJob = serviceScope.launch {
            var lastPollAt = SystemClock.elapsedRealtime()
            var nextOutboundPollAt = 0L
            var failures = 0
            XrayStatsClient(
                listenAddress = runtime.listenAddress,
                port = runtime.port,
                apiTag = runtime.apiTag,
            ).use { client ->
                while (isActive) {
                    val currentPollInterval = if (isScreenInteractive) {
                        PollIntervalMillis
                    } else {
                        ScreenOffPollIntervalMillis
                    }
                    delay(currentPollInterval.milliseconds)
                    val now = SystemClock.elapsedRealtime()
                    val elapsedMillis = now - lastPollAt
                    lastPollAt = now
                    runCatching {
                        client.queryInboundTraffic(reset = true)
                    }.onSuccess { delta ->
                        failures = 0
                        if (isScreenInteractive && now >= nextOutboundPollAt) {
                            nextOutboundPollAt = now + OutboundPollIntervalMillis
                            runCatching { client.queryOutboundTraffic(reset = false) }
                                .onSuccess { totals ->
                                    totals.maxTrafficDeltaComparedTo(previousOutboundTotals, currentActiveTag = activeOutboundTag)?.let { tag ->
                                        activeOutboundTag = tag
                                    }
                                    previousOutboundTotals = totals
                                    activeTargetName = resolveActiveTargetName(runtime)
                                }
                                .onFailure { error ->
                                    AndroidAppLogger.warn(LogTag, "Failed to resolve active VPN outbound", error)
                                }
                        }
                        latestSample = sessionAccumulator.record(delta, elapsedMillis)
                        if (isScreenInteractive) {
                            notificationManager.notify(
                                NotificationId,
                                buildNotification(
                                    runtime = runtime,
                                    sample = latestSample,
                                ),
                            )
                        }
                    }.onFailure { error ->
                        failures += 1
                        AndroidAppLogger.warn(LogTag, "Failed to query Xray traffic stats", error)
                        if (failures >= MaxConsecutiveFailures) {
                            AndroidAppLogger.warn(LogTag, "Stopping traffic stats notification after repeated failures")
                            stopStats()
                        }
                    }
                }
            }
        }
    }

    private fun stopStats() {
        pollingJob?.cancel()
        pollingJob = null
        activeRuntime = null
        accumulator = XrayTrafficSessionAccumulator()
        latestSample = EmptyTrafficSample
        previousOutboundTotals = emptyMap()
        activeOutboundTag = null
        activeTargetName = ""
        resumeInProgress = false
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        notificationManager.cancel(NotificationId)
        stopSelf()
    }

    /** Pausing stops only the VPN tunnel; this foreground notification stays actionable. */
    private fun pauseVpn() {
        val runtime = activeRuntime ?: ProxyTrafficStatsRuntimeStore.read(this) ?: return
        if (runtime.paused) return
        pollingJob?.cancel()
        pollingJob = null
        val pausedRuntime = runtime.copy(
            paused = true,
            pausedAtElapsedRealtime = SystemClock.elapsedRealtime(),
        )
        activeRuntime = pausedRuntime
        ProxyTrafficStatsRuntimeStore.write(applicationContext, pausedRuntime)
        serviceScope.launch {
            SkipiVpnService.stop(applicationContext)
        }
        stateStore.update { state -> state.copy(proxyRunning = false) }
        notificationManager.notify(NotificationId, buildNotification(pausedRuntime, latestSample))
    }

    /** Starts the selected VPN again through the same engine path as the main UI. */
    private fun resumeVpn() {
        if (resumeInProgress) return
        val runtime = activeRuntime ?: ProxyTrafficStatsRuntimeStore.read(this) ?: return
        if (!runtime.paused) return
        resumeInProgress = true
        serviceScope.launch {
            try {
                val state = stateStore.state.value
                val selectedServer = state.proxyServers.firstOrNull { server ->
                    server.id == state.selectedProxyServerId
                }
                when (val result = notificationProxyServiceUseCase.toggle(state, selectedServer)) {
                    is ProxyServiceResult.Success -> {
                        stateStore.update { current ->
                            current.copy(
                                proxyRunning = result.proxyRunning,
                                localProxyPort = result.appState?.localProxyPort ?: current.localProxyPort,
                            )
                        }
                    }

                    ProxyServiceResult.MissingServer -> {
                        AndroidAppLogger.warn(LogTag, "Cannot resume VPN from notification: selected server is missing")
                    }

                    is ProxyServiceResult.Failed -> {
                        AndroidAppLogger.warn(LogTag, "Cannot resume VPN from notification", result.error)
                    }
                }
            } finally {
                resumeInProgress = false
            }
        }
    }

    /** Completely disconnects the VPN and removes the persistent notification. */
    private fun disconnectVpn() {
        serviceScope.launch {
            SkipiVpnService.stop(applicationContext)
        }
        stateStore.update { state -> state.copy(proxyRunning = false) }
        ProxyTrafficStatsRuntimeStore.clear(applicationContext)
        stopStats()
    }

    private fun resolveActiveTargetName(runtime: ProxyTrafficStatsRuntime): String {
        return stateStore.state.value.activeTunnelTargetDisplayName(
            runtime = runtime,
            activeOutboundTag = activeOutboundTag,
            directName = getString(R.string.routing_outbound_direct),
            blockName = getString(R.string.routing_outbound_block),
        ).takeUnless { name -> name == "—" }
            ?: runtime.serverName
    }

    private fun buildNotification(
        runtime: ProxyTrafficStatsRuntime,
        sample: XrayTrafficSessionSample,
    ): Notification {
        val speedText = getString(
            R.string.proxy_traffic_stats_notification_speed,
            sample.speedBytesPerSecond.uplink.toTrafficSpeedString(),
            sample.speedBytesPerSecond.downlink.toTrafficSpeedString(),
        )
        val totalText = getString(
            R.string.proxy_traffic_stats_notification_total,
            sample.totalBytes.uplink.toTrafficSizeString(),
            sample.totalBytes.downlink.toTrafficSizeString(),
        )
        val durationText = getString(
            R.string.proxy_traffic_stats_notification_duration,
            connectionDurationString(runtime),
        )
        val pausedText = getString(R.string.proxy_traffic_stats_notification_paused)
        val contentText = if (runtime.paused) pausedText else "$durationText · $speedText"
        val expandedText = if (runtime.paused) {
            "$pausedText\n$durationText\n$totalText"
        } else {
            "$durationText\n$speedText\n$totalText"
        }
        val action = if (runtime.paused) {
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_qs_proxy),
                getString(R.string.proxy_traffic_stats_notification_resume),
                resumeIntent,
            ).build()
        } else {
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_qs_proxy),
                getString(R.string.proxy_traffic_stats_notification_pause),
                pauseIntent,
            ).build()
        }
        val disconnectAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_qs_proxy),
            getString(R.string.proxy_traffic_stats_notification_disconnect),
            disconnectIntent,
        ).build()
        return notificationBuilder()
            .setSmallIcon(R.drawable.ic_qs_proxy)
            .setContentTitle(activeTargetName.ifBlank { runtime.serverName }.ifBlank { getString(R.string.app_name) })
            .setContentText(contentText)
            .setStyle(Notification.BigTextStyle().bigText(expandedText))
            .setContentIntent(contentIntent)
            .addAction(action)
            .addAction(disconnectAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun notificationBuilder(): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, ChannelId)
        } else {
            Notification.Builder(this)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                getString(R.string.proxy_traffic_stats_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationId, notification)
        }
    }

    private fun Intent.readRuntime(): ProxyTrafficStatsRuntime? {
        val listenAddress = getStringExtra(ExtraListenAddress)?.takeIf(String::isNotBlank) ?: return null
        val port = getIntExtra(ExtraPort, 0).takeIf { value -> value > 0 } ?: return null
        val serverName = getStringExtra(ExtraServerName).orEmpty()
        return ProxyTrafficStatsRuntime(
            listenAddress = listenAddress,
            port = port,
            serverName = serverName,
            apiTag = getStringExtra(ExtraApiTag)
                ?.takeIf(String::isNotBlank)
                ?: XrayStatsApiTag,
        )
    }

    companion object {
        internal fun reconcile(context: Context, runtime: ProxyTrafficStatsRuntime?) {
            if (runtime == null) {
                stop(context)
            } else {
                start(context, runtime)
            }
        }

        internal fun start(
            context: Context,
            runtime: ProxyTrafficStatsRuntime,
        ) {
            val appContext = context.applicationContext
            ProxyTrafficStatsRuntimeStore.write(appContext, runtime)
            runCatching {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, ProxyTrafficStatsService::class.java)
                        .setAction(ActionStart)
                        .putExtra(ExtraListenAddress, runtime.listenAddress)
                        .putExtra(ExtraPort, runtime.port)
                        .putExtra(ExtraServerName, runtime.serverName)
                        .putExtra(ExtraApiTag, runtime.apiTag),
                )
            }.onFailure { error ->
                AndroidAppLogger.warn(LogTag, "Failed to request traffic stats foreground service start", error)
            }
        }

        internal fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, ProxyTrafficStatsService::class.java))
        }
    }
}

private fun connectionDurationString(runtime: ProxyTrafficStatsRuntime): String {
    val now = runtime.pausedAtElapsedRealtime
        .takeIf { value -> runtime.paused && value > 0L }
        ?: SystemClock.elapsedRealtime()
    val elapsedSeconds = ((now - runtime.startedAtElapsedRealtime).coerceAtLeast(0L) / 1_000L)
    val hours = elapsedSeconds / 3_600L
    val minutes = (elapsedSeconds % 3_600L) / 60L
    val seconds = elapsedSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

private const val LogTag = "ProxyTrafficStats"
private const val ActionStart = "app.action.START_PROXY_TRAFFIC_STATS"
private const val ActionStop = "app.action.STOP_PROXY_TRAFFIC_STATS"
private const val ActionPause = "app.action.PAUSE_PROXY_FROM_NOTIFICATION"
private const val ActionResume = "app.action.RESUME_PROXY_FROM_NOTIFICATION"
private const val ActionDisconnect = "app.action.DISCONNECT_PROXY_FROM_NOTIFICATION"
private const val ExtraListenAddress = "listen_address"
private const val ExtraPort = "port"
private const val ExtraServerName = "server_name"
private const val ExtraApiTag = "api_tag"
private const val ChannelId = "proxy_traffic_stats"
private const val NotificationId = 3001
private const val PauseRequestCode = 3002
private const val ResumeRequestCode = 3003
private const val DisconnectRequestCode = 3004
private const val PollIntervalMillis = 2_000L
private const val ScreenOffPollIntervalMillis = 15_000L
private const val OutboundPollIntervalMillis = 3_000L
private const val MaxConsecutiveFailures = 5
private val EmptyTrafficSample = XrayTrafficSessionSample(
    speedBytesPerSecond = XrayTrafficBytes(),
    totalBytes = XrayTrafficBytes(),
)
