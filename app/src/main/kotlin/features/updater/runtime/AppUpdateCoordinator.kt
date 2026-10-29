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
import app.skipi.app.store.AppStateStore
import data.AndroidAppStateStore
import features.updater.AppUpdateDownloadStatus
import features.updater.AppUpdateRestoreAction
import features.updater.AppUpdateInfo
import features.updater.isSameReleaseAs
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
    private val stateStore: AppStateStore,
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
                var notify = false
                stateStore.update { state ->
                    val transition = state.appUpdateState().applyCheckedRelease(update)
                    notify = transition.notify
                    state.withAppUpdateState(transition.state)
                }
                if (update == null) {
                    AppUpdateCheckOutcome.UpToDate
                } else {
                    if (notify) AppUpdateNotifier.showUpdateAvailable(appContext, update)
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
    private val stateStore: AppStateStore,
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

        stateStore.update { state -> state.updatingAppUpdate { withQueuedDownload(update, automatic) } }
        enqueueWork(update, automatic, ExistingWorkPolicy.REPLACE)
    }

    /** Reattaches WorkManager to a persisted transfer after process recreation. */
    fun restorePendingDownload() {
        val state = stateStore.currentState
        val update = state.availableAppUpdate ?: return
        val updateState = state.appUpdateState()
        val apkFileExists = state.appUpdateApkFilePath?.let(::File)?.isFile == true
        when (val action = updateState.restoreAction(apkFileExists)) {
            AppUpdateRestoreAction.None -> Unit
            AppUpdateRestoreAction.ResetToAvailable ->
                stateStore.update { it.updatingAppUpdate { withAvailableUpdate(update) } }
            is AppUpdateRestoreAction.Resume -> enqueueWork(
                update = update,
                automatic = action.automatic,
                policy = ExistingWorkPolicy.KEEP,
            )
        }
    }

    fun markDownloading(update: AppUpdateInfo) {
        stateStore.update { state -> state.updatingAppUpdate { markDownloading(update) } }
    }

    fun markProgress(update: AppUpdateInfo, downloadedBytes: Long, totalBytes: Long) {
        stateStore.update { state -> state.updatingAppUpdate { markProgress(update, downloadedBytes, totalBytes) } }
    }

    fun markReady(update: AppUpdateInfo, apkFilePath: String) {
        stateStore.update { state -> state.updatingAppUpdate { markReady(update, apkFilePath) } }
    }

    fun markFailed(update: AppUpdateInfo, errorMessage: String) {
        stateStore.update { state -> state.updatingAppUpdate { markFailed(update, errorMessage) } }
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

internal const val AppUpdateDownloadWorkName = "app-update-download"
internal const val AppUpdateDownloadVersionCodeInputKey = "app_update_download_version_code"
internal const val AppUpdateDownloadVersionNameInputKey = "app_update_download_version_name"
internal const val AppUpdateDownloadAutomaticInputKey = "app_update_download_automatic"
