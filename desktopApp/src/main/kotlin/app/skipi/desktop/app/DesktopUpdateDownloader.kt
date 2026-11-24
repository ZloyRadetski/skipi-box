// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.AppUpdateDownloadProgress
import features.updater.AppUpdateInfo
import features.updater.UpdateFileDownloader
import features.updater.isTrustedReleaseHost
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Downloads the release file of an update into a folder and proves it is the published one: its size and
 * the SHA-256 GitHub lists for the asset must match before it is kept. A file that fails is deleted, so
 * nothing half-written or altered is ever offered to the installer. The rules are [UpdateFileDownloader]'s.
 */
internal class DesktopUpdateDownloader(
    isTrusted: (URI) -> Boolean = ::isTrustedReleaseHost,
    requireHttps: Boolean = true,
    /** Built on the first download: the app makes its downloader at start, long before any update. */
    client: Lazy<HttpClient> = DesktopReleaseHttpClient,
) {
    private val downloader = UpdateFileDownloader(
        transport = DesktopUpdateTransport(client, requestTimeout = Duration.ofMinutes(10)),
        isTrusted = isTrusted,
        requireHttps = requireHttps,
    )

    /** [onProgress] gets the bytes written so far and the expected total (0 when unknown). */
    suspend fun download(
        update: AppUpdateInfo,
        directory: Path,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<Path> {
        var completed: String? = null
        return try {
            downloader.download(update, directory.toFile(), resume = false).collect { item ->
                when (item) {
                    is AppUpdateDownloadProgress.Downloading -> onProgress(item.downloadedBytes, item.totalBytes)
                    is AppUpdateDownloadProgress.Completed -> completed = item.apkFilePath
                    else -> Unit
                }
            }
            Result.success(Path.of(checkNotNull(completed) { "The update download did not finish" }))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}

/** Removes the folders of earlier versions' downloads: only the one being installed is kept. */
internal fun pruneOldUpdateDownloads(root: Path, keepFolder: String) {
    if (!Files.isDirectory(root)) return
    Files.list(root).use { entries ->
        entries.filter { it.fileName.toString() != keepFolder }.forEach { it.toFile().deleteRecursively() }
    }
}
