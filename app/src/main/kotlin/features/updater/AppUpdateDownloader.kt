// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.content.Context
import engine.network.TunnelNetworks
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val LogTag = "AppUpdateDownloader"

/**
 * Streaming APK downloader used exclusively by [features.updater.runtime.AppUpdateDownloadWorker].
 * The partial file intentionally survives cancellation and transient failures so WorkManager can resume it.
 */
internal class AppUpdateDownloader(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun downloadApk(updateInfo: AppUpdateInfo): Flow<AppUpdateDownloadProgress> = flow {
        val updatesDir = File(appContext.filesDir, UpdatesDirectoryName)
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            throw IOException("Could not create the update directory")
        }

        val targetFile = targetFileFor(updatesDir, updateInfo)
        val partialFile = File(updatesDir, "${targetFile.name}.part")

        try {
            if (isCompleteFile(targetFile, updateInfo)) {
                emit(AppUpdateDownloadProgress.Completed(targetFile.absolutePath))
                return@flow
            }

            // A finished part can be left behind if the process dies between fsync and rename.
            if (isCompleteFile(partialFile, updateInfo)) {
                promotePartialFile(partialFile, targetFile)
                emit(AppUpdateDownloadProgress.Completed(targetFile.absolutePath))
                return@flow
            }

            var retainedBytes = partialFile.takeIf(File::isFile)?.length() ?: 0L
            if (updateInfo.apkSizeBytes > 0L && retainedBytes >= updateInfo.apkSizeBytes) {
                if (!partialFile.delete()) throw IOException("Could not reset invalid partial APK")
                retainedBytes = 0L
            }

            var totalBytes = updateInfo.apkSizeBytes
            emit(
                AppUpdateDownloadProgress.Downloading(
                    progress = progressFor(retainedBytes, totalBytes),
                    downloadedBytes = retainedBytes,
                    totalBytes = totalBytes,
                ),
            )

            val connection = TunnelNetworks.withLocalProxyAuthenticator {
                openConnectionWithRedirects(updateInfo.downloadUrl, retainedBytes)
            }
            try {
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
                    throw IOException("APK download failed: HTTP $status")
                }

                val append = retainedBytes > 0L && status == HttpURLConnection.HTTP_PARTIAL
                if (!append && retainedBytes > 0L) {
                    if (!partialFile.delete()) throw IOException("Could not restart partial APK download")
                    retainedBytes = 0L
                }

                totalBytes = totalBytesFrom(connection, retainedBytes, append, updateInfo.apkSizeBytes)
                var downloadedBytes = retainedBytes
                var lastEmittedProgress = progressFor(downloadedBytes, totalBytes)

                connection.inputStream.use { input ->
                    FileOutputStream(partialFile, append).use { output ->
                        val buffer = ByteArray(BufferSize)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) break
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = progressFor(downloadedBytes, totalBytes)
                            if (progress - lastEmittedProgress >= ProgressStep ||
                                (totalBytes > 0L && downloadedBytes >= totalBytes)
                            ) {
                                lastEmittedProgress = progress
                                emit(
                                    AppUpdateDownloadProgress.Downloading(
                                        progress = progress,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                    ),
                                )
                            }
                        }
                        output.fd.sync()
                    }
                }

                val finalBytes = partialFile.length()
                if (totalBytes > 0L && finalBytes != totalBytes) {
                    throw IOException("Incomplete APK download: $finalBytes of $totalBytes bytes")
                }
                verifySha256IfPresent(partialFile, updateInfo.assetSha256)
                promotePartialFile(partialFile, targetFile)
                emit(AppUpdateDownloadProgress.Completed(targetFile.absolutePath))
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            // Do not convert cancellation into a failure or delete the partial file: the work will resume.
            throw cancelled
        } catch (error: Exception) {
            AndroidAppLogger.warn(LogTag, "APK download failed: ${error.message}", error)
            emit(AppUpdateDownloadProgress.Failed(error.localizedMessage ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    private fun openConnectionWithRedirects(
        initialUrl: String,
        retainedBytes: Long,
        maxRedirects: Int = 5,
    ): HttpURLConnection {
        var currentUrl = URL(initialUrl)
        repeat(maxRedirects) {
            // The app is excluded from its own VPN, so bind to the tunnel explicitly when it is up.
            val connection = TunnelNetworks.openHttpConnection(appContext, currentUrl).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "SKIPI-App")
                if (retainedBytes > 0L) {
                    setRequestProperty("Range", "bytes=$retainedBytes-")
                }
            }
            val status = connection.responseCode
            if (status in RedirectStatusCodes) {
                val location = connection.getHeaderField("Location")
                if (location.isNullOrBlank()) return connection
                currentUrl = URL(currentUrl, location)
                connection.disconnect()
            } else {
                return connection
            }
        }
        throw IOException("Too many redirects downloading APK")
    }

    private fun totalBytesFrom(
        connection: HttpURLConnection,
        retainedBytes: Long,
        append: Boolean,
        advertisedSize: Long,
    ): Long {
        val rangeTotal = connection.getHeaderField("Content-Range")
            ?.substringAfter('/', missingDelimiterValue = "")
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
        if (rangeTotal != null) return rangeTotal

        val responseSize = connection.contentLengthLong.takeIf { it > 0L } ?: 0L
        return when {
            append && responseSize > 0L -> retainedBytes + responseSize
            responseSize > 0L -> responseSize
            else -> advertisedSize
        }
    }

    private fun isCompleteFile(file: File, updateInfo: AppUpdateInfo): Boolean {
        if (!file.isFile) return false
        if (updateInfo.apkSizeBytes > 0L && file.length() != updateInfo.apkSizeBytes) return false
        return runCatching {
            verifySha256IfPresent(file, updateInfo.assetSha256)
            true
        }.getOrDefault(false)
    }

    private fun promotePartialFile(partialFile: File, targetFile: File) {
        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("Could not replace previous APK")
        }
        if (!partialFile.renameTo(targetFile)) {
            throw IOException("Could not finalize APK download")
        }
    }

    private fun verifySha256IfPresent(file: File, expectedDigest: String?) {
        val expected = expectedDigest
            ?.removePrefix("sha256:")
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            ?: return
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BufferSize)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }
        val actual = digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        if (!actual.equals(expected, ignoreCase = true)) {
            throw IOException("Downloaded APK checksum does not match the release")
        }
    }

    private fun targetFileFor(updatesDir: File, updateInfo: AppUpdateInfo): File {
        val safeAssetName = File(updateInfo.assetName).name.ifBlank { "SKIPI.apk" }
        return File(updatesDir, "v${updateInfo.versionCode}-$safeAssetName")
    }

    private companion object {
        const val UpdatesDirectoryName = "updates"
        const val BufferSize = 8 * 1024
        const val ProgressStep = 0.02f
        val RedirectStatusCodes = setOf(
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )

        fun progressFor(downloadedBytes: Long, totalBytes: Long): Float {
            return if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }
}
