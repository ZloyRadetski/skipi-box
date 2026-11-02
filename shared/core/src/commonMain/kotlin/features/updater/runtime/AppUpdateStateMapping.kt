// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import app.AppState
import features.updater.AppUpdateChannel
import features.updater.AppUpdateInfo
import features.updater.AppUpdateState
import features.updater.isSameReleaseAs

/**
 * AppState keeps the update fields flat because they are persisted individually; every
 * transition itself lives in the shared [AppUpdateState].
 */
fun AppState.appUpdateState() = AppUpdateState(
    availableUpdate = availableAppUpdate,
    dismissedVersion = dismissedUpdateVersion,
    status = appUpdateDownloadStatus,
    downloadedBytes = appUpdateDownloadedBytes,
    totalBytes = appUpdateTotalBytes,
    apkFilePath = appUpdateApkFilePath,
    error = appUpdateDownloadError,
    isAutomatic = appUpdateDownloadIsAutomatic,
)

fun AppState.withAppUpdateState(update: AppUpdateState): AppState = copy(
    availableAppUpdate = update.availableUpdate,
    dismissedUpdateVersion = update.dismissedVersion,
    appUpdateDownloadStatus = update.status,
    appUpdateDownloadedBytes = update.downloadedBytes,
    appUpdateTotalBytes = update.totalBytes,
    appUpdateApkFilePath = update.apkFilePath,
    appUpdateDownloadError = update.error,
    appUpdateDownloadIsAutomatic = update.isAutomatic,
)

fun AppState.updatingAppUpdate(transform: AppUpdateState.() -> AppUpdateState): AppState =
    withAppUpdateState(appUpdateState().transform())

fun AppState.matchesUpdate(update: AppUpdateInfo): Boolean = appUpdateState().matches(update)

/**
 * Follows another release channel. An offer found on the old channel is not valid on the new one
 * (a pre-release must not be installed after leaving Nightly), so the update state starts over.
 */
fun AppState.withAppUpdateChannel(channel: AppUpdateChannel): AppState =
    if (channel == appUpdateChannel) this else copy(appUpdateChannel = channel).updatingAppUpdate { cleared() }
