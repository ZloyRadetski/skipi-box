// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.SkipiApplication
import features.logs.AndroidAppLogger
import features.updater.AppUpdateDownloadProgress
import features.updater.AppUpdateDownloader
import features.updater.AppUpdateNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

private const val LogTag = "AppUpdateDownloadWorker"

internal class AppUpdateDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? SkipiApplication ?: return Result.failure()
        val update = application.stateStore.currentState.availableAppUpdate ?: return Result.success()
        val expectedVersionCode = inputData.getInt(AppUpdateDownloadVersionCodeInputKey, Int.MIN_VALUE)
        val expectedVersionName = inputData.getString(AppUpdateDownloadVersionNameInputKey)
        if (update.versionCode != expectedVersionCode || update.versionName != expectedVersionName) {
            return Result.success()
        }

        val automatic = inputData.getBoolean(AppUpdateDownloadAutomaticInputKey, false)
        val coordinator = application.appUpdateDownloadCoordinator
        coordinator.markDownloading(update)
        if (!automatic) {
            updateForeground(update, 0L, update.apkSizeBytes)
        }

        var failureMessage: String? = null
        try {
            AppUpdateDownloader(applicationContext).downloadApk(update).collect { progress ->
                when (progress) {
                    is AppUpdateDownloadProgress.Downloading -> {
                        coordinator.markProgress(update, progress.downloadedBytes, progress.totalBytes)
                        if (!automatic) {
                            updateForeground(update, progress.downloadedBytes, progress.totalBytes)
                        }
                    }

                    is AppUpdateDownloadProgress.Completed -> {
                        if (application.stateStore.currentState.matchesUpdate(update)) {
                            coordinator.markReady(update, progress.apkFilePath)
                            AppUpdateNotifier.showInstallReady(applicationContext, java.io.File(progress.apkFilePath))
                        }
                    }

                    is AppUpdateDownloadProgress.Failed -> failureMessage = progress.errorMessage
                    AppUpdateDownloadProgress.Idle -> Unit
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AndroidAppLogger.warn(LogTag, "APK download worker failed: ${error.message}", error)
            failureMessage = error.localizedMessage ?: "Download failed"
        } finally {
            if (!automatic) AppUpdateNotifier.cancelDownloadProgress(applicationContext)
        }

        val failure = failureMessage
        if (failure == null) return Result.success()
        coordinator.markFailed(update, failure)
        return if (runAttemptCount < MaxRetryAttempts) Result.retry() else Result.failure()
    }

    private suspend fun updateForeground(
        update: features.updater.AppUpdateInfo,
        downloadedBytes: Long,
        totalBytes: Long,
    ) {
        runCatching {
            setForeground(AppUpdateNotifier.foregroundInfo(applicationContext, update, downloadedBytes, totalBytes))
        }.onFailure { error ->
            // A notification restriction must not discard an already resumable APK transfer.
            AndroidAppLogger.warn(LogTag, "Could not show update foreground notification: ${error.message}", error)
        }
    }

    private companion object {
        const val MaxRetryAttempts = 3
    }
}
