// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.MainActivity
import app.R
import features.logs.AndroidAppLogger
import java.io.File

/** Owns the single notification channel used by update checks and transfers. */
internal object AppUpdateNotifier {
    const val ChannelId = "app_update_channel"
    private const val AvailableNotificationId = 20091
    private const val ReadyNotificationId = 20092
    private const val DownloadNotificationId = 10091

    fun showUpdateAvailable(context: Context, update: AppUpdateInfo) {
        if (!canPostNotifications(context)) return
        runCatching {
            val notification = NotificationCompat.Builder(context, ChannelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.app_update_available_title))
                .setContentText("v${update.versionName}")
                .setContentIntent(openAppPendingIntent(context))
                .setAutoCancel(true)
                .build()
            notificationManager(context).notify(AvailableNotificationId, notification)
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Could not show update notification: ${error.message}", error)
        }
    }

    fun showInstallReady(context: Context, apkFile: File) {
        if (!canPostNotifications(context)) return
        runCatching {
            val installIntent = AppUpdateInstaller.installPendingIntent(context, apkFile) ?: return@runCatching
            val notification = NotificationCompat.Builder(context, ChannelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.app_update_ready_notification_title))
                .setContentText(context.getString(R.string.app_update_ready_notification_text))
                .setContentIntent(installIntent)
                .setAutoCancel(true)
                .build()
            notificationManager(context).notify(ReadyNotificationId, notification)
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Could not show ready-to-install notification: ${error.message}", error)
        }
    }

    fun foregroundInfo(
        context: Context,
        update: AppUpdateInfo,
        downloadedBytes: Long,
        totalBytes: Long,
    ): ForegroundInfo {
        createChannelIfNeeded(context)
        val progress = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.app_update_downloading_notification_title, update.versionName))
            .setProgress(100, progress, totalBytes <= 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return ForegroundInfo(DownloadNotificationId, notification)
    }

    fun cancelDownloadProgress(context: Context) {
        notificationManager(context).cancel(DownloadNotificationId)
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            ChannelId,
            context.getString(R.string.app_update_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.app_update_notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationManager(context: Context): NotificationManager {
        createChannelIfNeeded(context)
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ChannelId) == null) {
            val channel = NotificationChannel(
                ChannelId,
                context.getString(R.string.app_update_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.app_update_notification_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            AvailableNotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val LogTag = "AppUpdateNotifier"
}
