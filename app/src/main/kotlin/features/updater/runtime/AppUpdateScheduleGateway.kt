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
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

private const val AppUpdatePeriodicWorkName = "app-update-checker-periodic"
private const val AppUpdateAutomaticWorkName = "app-update-checker-auto"
private const val AppUpdateManualWorkName = "app-update-checker-manual"
internal const val AppUpdateForceCheckInputKey = "force_check"

internal class AppUpdateScheduleGateway(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val checkStore = AppUpdateCheckStore(context)

    fun schedulePeriodicCheck(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(AppUpdatePeriodicWorkName)
            workManager.cancelUniqueWork(AppUpdateAutomaticWorkName)
            return
        }

        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(
            repeatInterval = 12,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 2,
            flexTimeIntervalUnit = TimeUnit.HOURS,
        )
            .setConstraints(checkConstraints())
            .setInputData(workDataOf(AppUpdateForceCheckInputKey to false))
            .build()

        workManager.enqueueUniquePeriodicWork(
            AppUpdatePeriodicWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Queues an initial automatic check only when the persisted 24-hour TTL has expired. */
    fun enqueueAutomaticCheckIfDue() {
        if (!checkStore.isDue() || !checkStore.isAutomaticAttemptDue()) return
        val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
            .setConstraints(checkConstraints())
            .setInputData(workDataOf(AppUpdateForceCheckInputKey to false))
            .build()
        workManager.enqueueUniqueWork(
            AppUpdateAutomaticWorkName,
            ExistingWorkPolicy.KEEP,
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
            .setInputData(workDataOf(AppUpdateForceCheckInputKey to true))
            .build()

        workManager.enqueueUniqueWork(
            AppUpdateManualWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun checkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
