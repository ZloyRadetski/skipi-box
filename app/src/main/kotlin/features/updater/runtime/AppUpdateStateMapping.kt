// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import app.AppState
import features.updater.AppUpdateInfo
import features.updater.AppUpdateState
import features.updater.isSameReleaseAs

/**
 * AppState keeps the update fields flat because they are persisted individually; every
 * transition itself lives in the shared [AppUpdateState].
 */
internal fun AppState.appUpdateState() = AppUpdateState(
    availableUpdate = availableAppUpdate,
    dismissedVersion = dismissedUpdateVersion,
    status = appUpdateDownloadStatus,
    downloadedBytes = appUpdateDownloadedBytes,
    totalBytes = appUpdateTotalBytes,
    apkFilePath = appUpdateApkFilePath,
    error = appUpdateDownloadError,
    isAutomatic = appUpdateDownloadIsAutomatic,
)

internal fun AppState.withAppUpdateState(update: AppUpdateState): AppState = copy(
    availableAppUpdate = update.availableUpdate,
    dismissedUpdateVersion = update.dismissedVersion,
    appUpdateDownloadStatus = update.status,
    appUpdateDownloadedBytes = update.downloadedBytes,
    appUpdateTotalBytes = update.totalBytes,
    appUpdateApkFilePath = update.apkFilePath,
    appUpdateDownloadError = update.error,
    appUpdateDownloadIsAutomatic = update.isAutomatic,
)

internal inline fun AppState.updatingAppUpdate(transform: AppUpdateState.() -> AppUpdateState): AppState =
    withAppUpdateState(appUpdateState().transform())

internal fun AppState.matchesUpdate(update: AppUpdateInfo): Boolean = appUpdateState().matches(update)
