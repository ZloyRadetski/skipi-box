// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import app.R
import features.logs.AndroidAppLogger
import engine.network.TunnelNetworks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val LogTag = "AppUpdateDownloader"
private const val NotificationChannelId = "app_update_channel"
private const val NotificationId = 10091

internal class AppUpdateDownloader(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    fun downloadApk(
        updateInfo: AppUpdateInfo,
        showNotification: Boolean = true,
    ): Flow<AppUpdateDownloadProgress> = flow {
        emit(AppUpdateDownloadProgress.Downloading(0f, 0L, updateInfo.apkSizeBytes))

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, updateInfo.assetName)
        val tempFile = File(updatesDir, "${updateInfo.assetName}.tmp")

        try {
            if (showNotification) {
                showProgressNotification(updateInfo.versionName, 0)
            }

            var totalBytes = updateInfo.apkSizeBytes
            var downloadedBytes = 0L
            var lastEmittedProgress = 0f

            val connection = TunnelNetworks.withLocalProxyAuthenticator {
                openConnectionWithRedirects(updateInfo.downloadUrl)
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > 0) {
                totalBytes = contentLength
            }

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        if (progress - lastEmittedProgress >= 0.02f || downloadedBytes == totalBytes) {
                            lastEmittedProgress = progress
                            emit(AppUpdateDownloadProgress.Downloading(progress, downloadedBytes, totalBytes))
                            if (showNotification) {
                                showProgressNotification(updateInfo.versionName, (progress * 100).toInt())
                            }
                        }
                    }
                }
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            if (showNotification) {
                notificationManager.cancel(NotificationId)
            }

            emit(AppUpdateDownloadProgress.Completed(targetFile.absolutePath))
        } catch (e: Exception) {
            AndroidAppLogger.warn(LogTag, "APK download failed: ${e.message}", e)
            tempFile.delete()
            if (showNotification) {
                notificationManager.cancel(NotificationId)
            }
            emit(AppUpdateDownloadProgress.Failed(e.localizedMessage ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    private fun openConnectionWithRedirects(initialUrl: String, maxRedirects: Int = 5): HttpURLConnection {
        var currentUrl = initialUrl
        var redirectCount = 0
        while (redirectCount < maxRedirects) {
            // The app is excluded from its own VPN, so bind to the tunnel
            // explicitly when it is up to download the APK through it.
            val connection = TunnelNetworks.openHttpConnection(context, URL(currentUrl)).apply {
                instanceFollowRedirects = false
                connectTimeout = 30000
                readTimeout = 60000
                setRequestProperty("User-Agent", "SKIPI-App")
            }

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308
            ) {
                val newUrl = connection.getHeaderField("Location")
                if (newUrl.isNullOrBlank()) {
                    return connection
                }
                currentUrl = newUrl
                redirectCount++
                connection.disconnect()
                continue
            }

            return connection
        }
        throw java.io.IOException("Too many redirects downloading APK")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationChannelId,
                context.getString(R.string.app_update_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.app_update_notification_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(version: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, NotificationChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.app_update_downloading_notification_title, version))
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(NotificationId, notification)
    }
}
