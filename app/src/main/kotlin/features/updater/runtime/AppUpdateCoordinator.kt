// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.AppState
import data.AndroidAppStateStore
import features.updater.AppUpdateDownloadStatus
import features.updater.AppUpdateInfo
import features.updater.AppUpdateNotifier
import features.updater.GitHubReleaseCheckResult
import features.updater.GitHubReleaseChecker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal sealed interface AppUpdateCheckOutcome {
    data class UpdateAvailable(val update: AppUpdateInfo) : AppUpdateCheckOutcome
    data object UpToDate : AppUpdateCheckOutcome
    data object NotModified : AppUpdateCheckOutcome
    data object Failed : AppUpdateCheckOutcome
}

/**
 * Serializes release checks and makes the discovered update durable before any
 * download job is enqueued. This is deliberately separate from the Compose UI.
 */
internal class AppUpdateCheckCoordinator(
    context: Context,
    private val stateStore: AndroidAppStateStore,
) {
    private val appContext = context.applicationContext
    private val checkStore = AppUpdateCheckStore(appContext)
    private val checkMutex = Mutex()

    suspend fun checkLatestRelease(): AppUpdateCheckOutcome = checkMutex.withLock {
        val checker = GitHubReleaseChecker(appContext)
        val conditionalResult = checker.checkLatestReleaseResult(checkStore.eTag())
        // Older app versions persisted the ETag but not the discovered update.
        // A 304 in that state must not hide the update forever after process death.
        val result = if (
            conditionalResult == GitHubReleaseCheckResult.NotModified &&
            stateStore.currentState.availableAppUpdate == null
        ) {
            checker.checkLatestReleaseResult()
        } else {
            conditionalResult
        }
        when (result) {
            is GitHubReleaseCheckResult.Success -> {
                checkStore.recordSuccessfulCheck(result.eTag)
                val update = result.update
                if (update == null) {
                    stateStore.update { state -> state.clearAppUpdate() }
                    AppUpdateCheckOutcome.UpToDate
                } else {
                    val previous = stateStore.currentState.availableAppUpdate
                    val isNewRelease = previous == null || !previous.isSameReleaseAs(update)
                    stateStore.update { state ->
                        if (state.availableAppUpdate?.isSameReleaseAs(update) == true) {
                            state.copy(availableAppUpdate = update)
                        } else {
                            state.withAvailableAppUpdate(update)
                        }
                    }
                    val current = stateStore.currentState
                    if (isNewRelease && current.dismissedUpdateVersion != update.versionName) {
                        AppUpdateNotifier.showUpdateAvailable(appContext, update)
                    }
                    AppUpdateCheckOutcome.UpdateAvailable(update)
                }
            }

            GitHubReleaseCheckResult.NotModified -> {
                checkStore.recordSuccessfulCheck(checkStore.eTag())
                AppUpdateCheckOutcome.NotModified
            }

            GitHubReleaseCheckResult.Failed -> AppUpdateCheckOutcome.Failed
        }
    }
}

/** A single durable owner for update download work. */
internal class AppUpdateDownloadCoordinator(
    context: Context,
    private val stateStore: AndroidAppStateStore,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun enqueue(update: AppUpdateInfo, automatic: Boolean) {
        val current = stateStore.currentState
        if (
            current.availableAppUpdate?.isSameReleaseAs(update) == true &&
            current.appUpdateDownloadStatus in setOf(
                AppUpdateDownloadStatus.QUEUED,
                AppUpdateDownloadStatus.DOWNLOADING,
            )
        ) {
            return
        }

        stateStore.update { state -> state.withQueuedAppUpdateDownload(update, automatic) }
        enqueueWork(update, automatic, ExistingWorkPolicy.REPLACE)
    }

    /** Reattaches WorkManager to a persisted transfer after process recreation. */
    fun restorePendingDownload() {
        val state = stateStore.currentState
        val update = state.availableAppUpdate ?: return
        when (state.appUpdateDownloadStatus) {
            AppUpdateDownloadStatus.READY_TO_INSTALL -> {
                val apkFile = state.appUpdateApkFilePath?.let(::File)
                if (apkFile?.isFile != true) {
                    stateStore.update { it.withAvailableAppUpdate(update) }
                }
            }

            AppUpdateDownloadStatus.QUEUED,
            AppUpdateDownloadStatus.DOWNLOADING -> enqueueWork(
                update = update,
                automatic = state.appUpdateDownloadIsAutomatic,
                policy = ExistingWorkPolicy.KEEP,
            )

            else -> Unit
        }
    }

    fun markDownloading(update: AppUpdateInfo) {
        stateStore.update { state ->
            if (!state.matchesUpdate(update)) state else state.copy(
                appUpdateDownloadStatus = AppUpdateDownloadStatus.DOWNLOADING,
                appUpdateDownloadError = null,
            )
        }
    }

    fun markProgress(update: AppUpdateInfo, downloadedBytes: Long, totalBytes: Long) {
        stateStore.update { state ->
            if (!state.matchesUpdate(update)) state else state.copy(
                appUpdateDownloadStatus = AppUpdateDownloadStatus.DOWNLOADING,
                appUpdateDownloadedBytes = downloadedBytes.coerceAtLeast(0L),
                appUpdateTotalBytes = totalBytes.coerceAtLeast(0L),
                appUpdateDownloadError = null,
            )
        }
    }

    fun markReady(update: AppUpdateInfo, apkFilePath: String) {
        stateStore.update { state ->
            if (!state.matchesUpdate(update)) state else state.copy(
                appUpdateDownloadStatus = AppUpdateDownloadStatus.READY_TO_INSTALL,
                appUpdateDownloadedBytes = maxOf(state.appUpdateDownloadedBytes, state.appUpdateTotalBytes),
                appUpdateApkFilePath = apkFilePath,
                appUpdateDownloadError = null,
            )
        }
    }

    fun markFailed(update: AppUpdateInfo, errorMessage: String) {
        stateStore.update { state ->
            if (!state.matchesUpdate(update)) state else state.copy(
                appUpdateDownloadStatus = AppUpdateDownloadStatus.FAILED,
                appUpdateDownloadError = errorMessage,
            )
        }
    }

    private fun enqueueWork(
        update: AppUpdateInfo,
        automatic: Boolean,
        policy: ExistingWorkPolicy,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (automatic) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .apply {
                if (automatic) setRequiresCharging(true)
            }
            .build()
        val request = OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    AppUpdateDownloadVersionCodeInputKey to update.versionCode,
                    AppUpdateDownloadVersionNameInputKey to update.versionName,
                    AppUpdateDownloadAutomaticInputKey to automatic,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(AppUpdateDownloadWorkName, policy, request)
    }
}

internal fun AppState.matchesUpdate(update: AppUpdateInfo): Boolean {
    return availableAppUpdate?.isSameReleaseAs(update) == true
}

internal fun AppUpdateInfo.isSameReleaseAs(other: AppUpdateInfo): Boolean {
    return versionCode == other.versionCode && versionName == other.versionName && assetName == other.assetName
}

private fun AppState.withAvailableAppUpdate(update: AppUpdateInfo): AppState {
    return copy(
        availableAppUpdate = update,
        dismissedUpdateVersion = "",
        appUpdateDownloadStatus = AppUpdateDownloadStatus.IDLE,
        appUpdateDownloadedBytes = 0L,
        appUpdateTotalBytes = 0L,
        appUpdateApkFilePath = null,
        appUpdateDownloadError = null,
        appUpdateDownloadIsAutomatic = false,
    )
}

private fun AppState.withQueuedAppUpdateDownload(update: AppUpdateInfo, automatic: Boolean): AppState {
    return copy(
        availableAppUpdate = update,
        dismissedUpdateVersion = if (availableAppUpdate?.isSameReleaseAs(update) == true) {
            dismissedUpdateVersion
        } else {
            ""
        },
        appUpdateDownloadStatus = AppUpdateDownloadStatus.QUEUED,
        appUpdateDownloadedBytes = 0L,
        appUpdateTotalBytes = update.apkSizeBytes,
        appUpdateApkFilePath = null,
        appUpdateDownloadError = null,
        appUpdateDownloadIsAutomatic = automatic,
    )
}

private fun AppState.clearAppUpdate(): AppState {
    return copy(
        availableAppUpdate = null,
        dismissedUpdateVersion = "",
        appUpdateDownloadStatus = AppUpdateDownloadStatus.IDLE,
        appUpdateDownloadedBytes = 0L,
        appUpdateTotalBytes = 0L,
        appUpdateApkFilePath = null,
        appUpdateDownloadError = null,
        appUpdateDownloadIsAutomatic = false,
    )
}

internal const val AppUpdateDownloadWorkName = "app-update-download"
internal const val AppUpdateDownloadVersionCodeInputKey = "app_update_download_version_code"
internal const val AppUpdateDownloadVersionNameInputKey = "app_update_download_version_name"
internal const val AppUpdateDownloadAutomaticInputKey = "app_update_download_automatic"
