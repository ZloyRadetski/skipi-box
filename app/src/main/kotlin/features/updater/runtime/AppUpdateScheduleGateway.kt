// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val AppUpdatePeriodicWorkName = "app-update-checker-periodic"
private const val AppUpdateOneTimeWorkName = "app-update-checker-onetime"

internal class AppUpdateScheduleGateway(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedulePeriodicCheck(enabled: Boolean, autoInstallAtNight: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(AppUpdatePeriodicWorkName)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (autoInstallAtNight) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .apply {
                if (autoInstallAtNight) {
                    setRequiresCharging(true)
                }
            }
            .build()

        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(
            repeatInterval = 12,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 2,
            flexTimeIntervalUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AppUpdatePeriodicWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun triggerImmediateCheck() {
        val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(
            AppUpdateOneTimeWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
