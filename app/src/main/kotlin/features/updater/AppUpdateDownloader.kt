// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.content.Context
import features.logs.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.File

private const val LogTag = "AppUpdateDownloader"

/**
 * Streaming APK downloader used exclusively by [features.updater.runtime.AppUpdateDownloadWorker]. The rules of the
 * transfer (HTTPS, trusted release hosts, resume, size and SHA-256 checks) are [UpdateFileDownloader]'s; this class
 * only picks the folder and file name and turns a failure into an [AppUpdateDownloadProgress.Failed] item.
 * The partial file intentionally survives cancellation and transient failures so WorkManager can resume it.
 */
internal class AppUpdateDownloader(
    private val updatesDirectory: File,
    private val downloader: UpdateFileDownloader,
) {
    constructor(context: Context, viaProxy: Boolean) : this(
        updatesDirectory = File(context.applicationContext.filesDir, UpdatesDirectoryName),
        downloader = UpdateFileDownloader(
            AndroidUpdateTransport(
                context.applicationContext,
                UpdateHttp.DownloadConnectTimeoutMs,
                UpdateHttp.DownloadReadTimeoutMs,
                viaProxy,
            ),
        ),
    )

    fun downloadApk(updateInfo: AppUpdateInfo): Flow<AppUpdateDownloadProgress> = downloader.download(
        update = updateInfo,
        directory = updatesDirectory,
        targetName = "v${updateInfo.versionCode}-${updateInfo.assetName}",
        resume = true,
    ).catch { error ->
        // Cancellation is not a failure: the work resumes the partial file later.
        if (error is CancellationException) throw error
        AppLog.warn(LogTag, "APK download failed: ${error.message}", error)
        emit(AppUpdateDownloadProgress.Failed(error.localizedMessage ?: "Download failed"))
    }

    private companion object {
        const val UpdatesDirectoryName = "updates"
    }
}
