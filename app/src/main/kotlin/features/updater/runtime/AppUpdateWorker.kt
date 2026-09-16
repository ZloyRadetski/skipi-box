// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.SkipiApplication
import features.logs.AndroidAppLogger

private const val LogTag = "AppUpdateWorker"

/** Periodic release check. Downloading is delegated to a separate durable worker. */
internal class AppUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? SkipiApplication ?: return Result.failure()
        val appState = application.stateStore.currentState
        val forceCheck = inputData.getBoolean(AppUpdateForceCheckInputKey, false)

        if (!forceCheck && !appState.autoCheckAppUpdates) {
            AndroidAppLogger.debug(LogTag, "Auto-check updates is disabled. Skipping worker.")
            return Result.success()
        }

        val checkStore = AppUpdateCheckStore(applicationContext)
        if (!forceCheck && !checkStore.isDue()) {
            AndroidAppLogger.debug(LogTag, "App update check is still within its TTL. Skipping worker.")
            return Result.success()
        }

        AndroidAppLogger.debug(LogTag, "Checking for app updates in background worker...")
        checkStore.recordAttempt()
        return when (val outcome = application.appUpdateCheckCoordinator.checkLatestRelease()) {
            is AppUpdateCheckOutcome.UpdateAvailable -> {
                AndroidAppLogger.info(LogTag, "Found new app update: v${outcome.update.versionName} (${outcome.update.assetName})")
                if (!forceCheck && appState.autoInstallAppUpdatesAtNight) {
                    // The compatibility setting now means "download on Wi-Fi while charging".
                    // Installation is always left to Android's explicit user confirmation.
                    application.appUpdateDownloadCoordinator.enqueue(outcome.update, automatic = true)
                }
                Result.success()
            }

            AppUpdateCheckOutcome.UpToDate,
            AppUpdateCheckOutcome.NotModified -> Result.success()

            AppUpdateCheckOutcome.Failed -> {
                // A few bounded retries improve transient connectivity without turning a failed check
                // into a 24-hour blackout or an unbounded radio wake-up loop.
                if (runAttemptCount < MaxRetryAttempts) Result.retry() else Result.failure()
            }
        }
    }

    private companion object {
        const val MaxRetryAttempts = 3
    }
}
