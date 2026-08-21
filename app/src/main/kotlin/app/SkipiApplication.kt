// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import android.app.Application
import system.AndroidAppIconFetcher
import features.logs.AndroidAccessLogRepository
import features.logs.AndroidCoreLogRepository
import features.logs.AndroidLogcatRepository
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import data.AppSettingsPreferences
import data.AndroidAppStateStore
import features.config.runtime.AndroidTrafficConfigScheduleGateway
import features.config.runtime.TrafficConfigScheduler
import features.subscription.runtime.AndroidSubscriptionFetcher
import features.subscription.runtime.AndroidSubscriptionScheduleGateway
import features.subscription.runtime.SubscriptionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SkipiApplication : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val stateStore: AndroidAppStateStore by lazy {
        AndroidAppStateStore.get(applicationContext)
    }
    internal val subscriptionFetcher: AndroidSubscriptionFetcher by lazy {
        AndroidSubscriptionFetcher(applicationContext)
    }
    private val subscriptionScheduler: SubscriptionScheduler by lazy {
        SubscriptionScheduler(AndroidSubscriptionScheduleGateway(applicationContext))
    }
    private val trafficConfigScheduler: TrafficConfigScheduler by lazy {
        TrafficConfigScheduler(AndroidTrafficConfigScheduleGateway(applicationContext))
    }
    internal val appUpdateScheduleGateway: features.updater.runtime.AppUpdateScheduleGateway by lazy {
        features.updater.runtime.AppUpdateScheduleGateway(applicationContext)
    }
    internal val networkAutomationMonitor: features.networkautomation.engine.NetworkAutomationMonitor by lazy {
        features.networkautomation.engine.NetworkAutomationMonitor(applicationContext, stateStore, appScope)
    }

    override fun onCreate() {
        super.onCreate()
        AppSettingsPreferences(applicationContext).getOrCreateSubscriptionHwid()
        val retentionDays = stateStore.state.value.logRetentionDays
        AndroidLogcatRepository.initialize(applicationContext, retentionDays)
        AndroidCoreLogRepository.initialize(applicationContext, retentionDays)
        AndroidAccessLogRepository.initialize(applicationContext, retentionDays)
        networkAutomationMonitor.start()
        appScope.launch {
            stateStore.state
                .map { state ->
                    state.subscriptionGroups.map { group ->
                        SubscriptionScheduleKey(
                            id = group.id,
                            url = group.url,
                            interval = group.updateInterval,
                            enabled = group.enabled,
                        )
                    }
                }
                .distinctUntilChanged()
                .collect {
                    subscriptionScheduler.reconcile(stateStore.state.value.subscriptionGroups)
                }
        }
        appScope.launch {
            stateStore.state
                .map { state ->
                    state.trafficConfigs.map { config ->
                        TrafficConfigScheduleKey(
                            id = config.id,
                            sourceUrl = config.sourceUrl,
                            updateLocked = config.updateLocked,
                            autoUpdate = config.autoUpdate,
                            updateInterval = config.updateInterval,
                            geoAutoUpdate = config.resourceSettings.autoUpdate,
                            geoUpdateInterval = config.resourceSettings.updateInterval,
                        )
                    }
                }
                .distinctUntilChanged()
                .collect {
                    trafficConfigScheduler.reconcile(stateStore.state.value.trafficConfigs)
                }
        }
        appScope.launch {
            stateStore.state
                .map { Pair(it.autoCheckAppUpdates, it.autoInstallAppUpdatesAtNight) }
                .distinctUntilChanged()
                .collect { (autoCheck, autoInstall) ->
                    appUpdateScheduleGateway.schedulePeriodicCheck(autoCheck, autoInstall)
                    if (autoCheck) {
                        launch(Dispatchers.IO) {
                            val update = features.updater.GitHubReleaseChecker(applicationContext).checkLatestRelease()
                            if (update != null) {
                                stateStore.update { it.copy(availableAppUpdate = update) }
                            }
                        }
                    }
                }
        }
        appScope.launch {
            stateStore.state
                .map { state ->
                    SubscriptionExpiryStateKey(
                        enabled = state.enableSubscriptionExpiryNotifications,
                        globalReminders = state.subscriptionExpiryReminders,
                        groups = state.subscriptionGroups.map {
                            SubscriptionExpiryGroupKey(
                                id = it.id,
                                enabled = it.enabled,
                                notify = it.notifyOnExpiry,
                                expire = it.trafficExpireAtSeconds,
                                customReminders = it.customExpiryReminders,
                            )
                        },
                    )
                }
                .distinctUntilChanged()
                .collect {
                    val currentState = stateStore.state.value
                    features.subscription.notification.SubscriptionExpiryNotifier.checkAndNotify(applicationContext, currentState)
                    features.subscription.notification.SubscriptionExpiryScheduler.schedule(applicationContext, currentState)
                }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AndroidAppIconFetcher.Factory(this@SkipiApplication))
                add(AndroidAppIconFetcher.CacheKeyer())
            }
            .build()
    }

    private data class SubscriptionScheduleKey(
        val id: Int,
        val url: String,
        val interval: String,
        val enabled: Boolean,
    )

    private data class SubscriptionExpiryStateKey(
        val enabled: Boolean,
        val globalReminders: List<app.SubscriptionExpiryReminder>,
        val groups: List<SubscriptionExpiryGroupKey>,
    )

    private data class SubscriptionExpiryGroupKey(
        val id: Int,
        val enabled: Boolean,
        val notify: Boolean,
        val expire: Long,
        val customReminders: List<app.SubscriptionExpiryReminder>?,
    )

    private data class TrafficConfigScheduleKey(
        val id: Int,
        val sourceUrl: String,
        val updateLocked: Boolean,
        val autoUpdate: Boolean,
        val updateInterval: String,
        val geoAutoUpdate: Boolean,
        val geoUpdateInterval: String,
    )
}
