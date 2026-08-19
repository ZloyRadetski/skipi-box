// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import android.content.Context
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

internal class AndroidTrafficConfigScheduleGateway(
    context: Context,
) : TrafficConfigScheduleGateway {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val preferences = appContext.getSharedPreferences(
        TrafficConfigSchedulePreferences,
        Context.MODE_PRIVATE,
    )

    override fun scheduledConfigIds(): Set<Int> =
        preferences.getStringSet(ScheduledConfigIdsKey, emptySet())
            .orEmpty()
            .mapNotNullTo(mutableSetOf(), String::toIntOrNull)

    override fun scheduledGeoConfigIds(): Set<Int> =
        preferences.getStringSet(ScheduledGeoConfigIdsKey, emptySet())
            .orEmpty()
            .mapNotNullTo(mutableSetOf(), String::toIntOrNull)

    override fun enqueueConfig(spec: TrafficConfigWorkSpec) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequest.Builder(
            TrafficConfigAutoUpdateWorker::class.java,
            spec.repeatIntervalMillis,
            TimeUnit.MILLISECONDS,
        )
            .setInputData(workDataOf(TrafficConfigIdKey to spec.id))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                spec.backoffMillis,
                TimeUnit.MILLISECONDS,
            )
            .addTag(TrafficConfigWorkTag)
            .build()
        workManager.enqueueUniquePeriodicWork(
            spec.uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun enqueueGeo(spec: TrafficConfigWorkSpec) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequest.Builder(
            ResourceAutoUpdateWorker::class.java,
            spec.repeatIntervalMillis,
            TimeUnit.MILLISECONDS,
        )
            .setInputData(workDataOf(TrafficConfigIdKey to spec.id))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                spec.backoffMillis,
                TimeUnit.MILLISECONDS,
            )
            .addTag(TrafficConfigGeoWorkTag)
            .build()
        workManager.enqueueUniquePeriodicWork(
            spec.uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancelConfig(configId: Int) {
        workManager.cancelUniqueWork(trafficConfigWorkName(configId))
    }

    override fun cancelGeo(configId: Int) {
        workManager.cancelUniqueWork(trafficConfigGeoWorkName(configId))
    }

    override fun storeScheduledConfigIds(configIds: Set<Int>) {
        preferences.edit {
            putStringSet(ScheduledConfigIdsKey, configIds.mapTo(mutableSetOf(), Int::toString))
        }
    }

    override fun storeScheduledGeoConfigIds(configIds: Set<Int>) {
        preferences.edit {
            putStringSet(ScheduledGeoConfigIdsKey, configIds.mapTo(mutableSetOf(), Int::toString))
        }
    }
}

internal const val TrafficConfigIdKey = "traffic_config_id"
private const val TrafficConfigWorkTag = "traffic-config-update"
private const val TrafficConfigGeoWorkTag = "traffic-config-geo-update"
private const val TrafficConfigSchedulePreferences = "traffic_config_schedule"
private const val ScheduledConfigIdsKey = "scheduled_config_ids"
private const val ScheduledGeoConfigIdsKey = "scheduled_geo_config_ids"
