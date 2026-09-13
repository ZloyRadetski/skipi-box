// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.SkipiApplication
import features.logs.AndroidAppLogger
import features.updater.AppUpdateDownloader
import features.updater.AppUpdateDownloadProgress
import features.updater.AppUpdateInstaller
import features.updater.GitHubReleaseChecker
import features.updater.GitHubReleaseCheckResult
import kotlinx.coroutines.flow.lastOrNull
import java.io.File

private const val LogTag = "AppUpdateWorker"

internal class AppUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? SkipiApplication ?: return Result.failure()
        val stateStore = application.stateStore
        val appState = stateStore.state.value
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
        val checker = GitHubReleaseChecker(applicationContext)
        checkStore.recordAttempt()
        return when (val result = checker.checkLatestReleaseResult(checkStore.eTag())) {
            is GitHubReleaseCheckResult.Success -> {
                checkStore.recordSuccessfulCheck(result.eTag)
                val update = result.update
                if (update != null) {
                    AndroidAppLogger.info(LogTag, "Found new app update: v${update.versionName} (${update.assetName})")
                    stateStore.update { it.copy(availableAppUpdate = update) }

                    if (!forceCheck && appState.autoInstallAppUpdatesAtNight) {
                        AndroidAppLogger.info(LogTag, "Night auto-update is enabled. Downloading and installing v${update.versionName}...")
                        val downloader = AppUpdateDownloader(applicationContext)
                        val downloadResult = downloader.downloadApk(update, showNotification = false).lastOrNull()
                        if (downloadResult is AppUpdateDownloadProgress.Completed) {
                            val apkFile = File(downloadResult.apkFilePath)
                            AppUpdateInstaller.installSilentlyOrPrompt(applicationContext, apkFile)
                        }
                    }
                } else {
                    AndroidAppLogger.debug(LogTag, "No newer update found.")
                    stateStore.update { it.copy(availableAppUpdate = null) }
                }
                Result.success()
            }

            GitHubReleaseCheckResult.NotModified -> {
                checkStore.recordSuccessfulCheck(checkStore.eTag())
                AndroidAppLogger.debug(LogTag, "Latest app release is unchanged (HTTP 304).")
                // Keep a previously discovered update visible.
                Result.success()
            }

            // Avoid a retry loop that repeatedly wakes a weak mobile radio.
            // The 12-hour periodic worker will retry after the 24-hour TTL.
            GitHubReleaseCheckResult.Failed -> Result.success()
        }
    }
}
