// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.math.roundToInt

/** Two offers are the same release when version and asset match; release notes may be corrected. */
fun AppUpdateInfo.isSameReleaseAs(other: AppUpdateInfo): Boolean {
    return versionCode == other.versionCode && versionName == other.versionName && assetName == other.assetName
}

/**
 * Durable update state persisted by the host. Every transition lives here so the discovered
 * release, the dismissal and the transfer stay consistent on every platform.
 */
data class AppUpdateState(
    val availableUpdate: AppUpdateInfo? = null,
    val dismissedVersion: String = "",
    val status: AppUpdateDownloadStatus = AppUpdateDownloadStatus.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val apkFilePath: String? = null,
    val error: String? = null,
    val isAutomatic: Boolean = false,
) {
    fun matches(update: AppUpdateInfo): Boolean = availableUpdate?.isSameReleaseAs(update) == true

    /** A newly discovered release resets the dismissal and any previous transfer. */
    fun withAvailableUpdate(update: AppUpdateInfo): AppUpdateState = AppUpdateState(availableUpdate = update)

    fun cleared(): AppUpdateState = AppUpdateState()

    fun withQueuedDownload(update: AppUpdateInfo, automatic: Boolean): AppUpdateState = copy(
        availableUpdate = update,
        dismissedVersion = if (matches(update)) dismissedVersion else "",
        status = AppUpdateDownloadStatus.QUEUED,
        downloadedBytes = 0L,
        totalBytes = update.apkSizeBytes,
        apkFilePath = null,
        error = null,
        isAutomatic = automatic,
    )

    fun dismissed(): AppUpdateState = availableUpdate?.let { copy(dismissedVersion = it.versionName) } ?: this

    fun markDownloading(update: AppUpdateInfo): AppUpdateState = ifMatches(update) {
        copy(status = AppUpdateDownloadStatus.DOWNLOADING, error = null)
    }

    fun markProgress(update: AppUpdateInfo, downloadedBytes: Long, totalBytes: Long): AppUpdateState = ifMatches(update) {
        copy(
            status = AppUpdateDownloadStatus.DOWNLOADING,
            downloadedBytes = downloadedBytes.coerceAtLeast(0L),
            totalBytes = totalBytes.coerceAtLeast(0L),
            error = null,
        )
    }

    fun markReady(update: AppUpdateInfo, apkFilePath: String): AppUpdateState = ifMatches(update) {
        copy(
            status = AppUpdateDownloadStatus.READY_TO_INSTALL,
            downloadedBytes = maxOf(downloadedBytes, totalBytes),
            apkFilePath = apkFilePath,
            error = null,
        )
    }

    fun markFailed(update: AppUpdateInfo, errorMessage: String): AppUpdateState = ifMatches(update) {
        copy(status = AppUpdateDownloadStatus.FAILED, error = errorMessage)
    }

    /**
     * Applies a successful release check ([release] null means the build is current). The same
     * release keeps its transfer state and refreshed notes; a new one resets everything.
     */
    fun applyCheckedRelease(release: AppUpdateInfo?): AppUpdateCheckTransition {
        if (release == null) return AppUpdateCheckTransition(cleared(), notify = false)
        val isNewRelease = availableUpdate?.isSameReleaseAs(release) != true
        val next = if (isNewRelease) withAvailableUpdate(release) else copy(availableUpdate = release)
        return AppUpdateCheckTransition(next, notify = isNewRelease && next.dismissedVersion != release.versionName)
    }

    /** What to do after process recreation, given whether the downloaded APK is still on disk. */
    fun restoreAction(apkFileExists: Boolean): AppUpdateRestoreAction {
        availableUpdate ?: return AppUpdateRestoreAction.None
        return when (status) {
            AppUpdateDownloadStatus.READY_TO_INSTALL ->
                if (apkFileExists) AppUpdateRestoreAction.None else AppUpdateRestoreAction.ResetToAvailable

            AppUpdateDownloadStatus.QUEUED,
            AppUpdateDownloadStatus.DOWNLOADING -> AppUpdateRestoreAction.Resume(automatic = isAutomatic)

            else -> AppUpdateRestoreAction.None
        }
    }

    private inline fun ifMatches(update: AppUpdateInfo, transform: AppUpdateState.() -> AppUpdateState): AppUpdateState =
        if (matches(update)) transform() else this
}

data class AppUpdateCheckTransition(val state: AppUpdateState, val notify: Boolean)

sealed interface AppUpdateRestoreAction {
    data object None : AppUpdateRestoreAction
    data object ResetToAvailable : AppUpdateRestoreAction
    data class Resume(val automatic: Boolean) : AppUpdateRestoreAction
}

/** What the update banner shows for the current [AppUpdateState]; null hides the banner. */
data class AppUpdateBannerModel(
    val update: AppUpdateInfo,
    val isDownloading: Boolean,
    val isReadyToInstall: Boolean,
    /** Download fraction while a transfer is active; 0 until the total size is known. */
    val progress: Float?,
    val progressPercent: Int?,
    val sizeText: String?,
    val errorMessage: String?,
) {
    val versionName: String get() = update.versionName
    val actionEnabled: Boolean get() = !isDownloading
    val hasChangelog: Boolean get() = update.changelog.isNotBlank()
}

fun AppUpdateState.bannerModel(): AppUpdateBannerModel? {
    val update = availableUpdate ?: return null
    if (update.versionName == dismissedVersion) return null
    val isDownloading = status == AppUpdateDownloadStatus.QUEUED || status == AppUpdateDownloadStatus.DOWNLOADING
    val progress = if (isDownloading) {
        if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    } else {
        null
    }
    return AppUpdateBannerModel(
        update = update,
        isDownloading = isDownloading,
        isReadyToInstall = status == AppUpdateDownloadStatus.READY_TO_INSTALL,
        progress = progress,
        progressPercent = if (isDownloading && totalBytes > 0L) ((progress ?: 0f) * 100).toInt() else null,
        sizeText = update.apkSizeBytes.takeIf { it > 0 }?.let(::megabytesText),
        errorMessage = error.takeIf { status == AppUpdateDownloadStatus.FAILED && !isDownloading }
            ?.takeIf(String::isNotBlank),
    )
}

private fun megabytesText(bytes: Long): String {
    val tenths = (bytes / (1024f * 1024f) * 10f).roundToInt()
    return "${tenths / 10}.${tenths % 10} MB"
}
