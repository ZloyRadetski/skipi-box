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

        if (!appState.autoCheckAppUpdates) {
            AndroidAppLogger.debug(LogTag, "Auto-check updates is disabled. Skipping worker.")
            return Result.success()
        }

        AndroidAppLogger.debug(LogTag, "Checking for app updates in background worker...")
        val checker = GitHubReleaseChecker(applicationContext)
        val update = checker.checkLatestRelease()

        if (update != null) {
            AndroidAppLogger.info(LogTag, "Found new app update: v${update.versionName} (${update.assetName})")
            stateStore.update { it.copy(availableAppUpdate = update) }

            if (appState.autoInstallAppUpdatesAtNight) {
                AndroidAppLogger.info(LogTag, "Night auto-update is enabled. Downloading and installing v${update.versionName}...")
                val downloader = AppUpdateDownloader(applicationContext)
                val result = downloader.downloadApk(update, showNotification = false).lastOrNull()
                if (result is AppUpdateDownloadProgress.Completed) {
                    val apkFile = File(result.apkFilePath)
                    AppUpdateInstaller.installSilentlyOrPrompt(applicationContext, apkFile)
                }
            }
        } else {
            AndroidAppLogger.debug(LogTag, "No newer update found.")
        }

        return Result.success()
    }
}
