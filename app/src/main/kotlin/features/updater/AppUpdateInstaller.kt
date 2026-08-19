// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import features.logs.AndroidAppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

private const val LogTag = "AppUpdateInstaller"

internal object AppUpdateInstaller {

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            AndroidAppLogger.warn(LogTag, "Cannot install APK: file does not exist ${apkFile.absolutePath}")
            return
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AndroidAppLogger.warn(LogTag, "Failed to launch package installer intent: ${e.message}", e)
        }
    }

    suspend fun installSilentlyOrPrompt(context: Context, apkFile: File): Boolean = withContext(Dispatchers.IO) {
        // 1. Try Android 12+ unattended PackageInstaller
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (tryAndroid12UnattendedInstall(context, apkFile)) {
                return@withContext true
            }
        }

        // 2. Fallback: Show user notification with PendingIntent to install
        showInstallReadyNotification(context, apkFile)
        false
    }

    private fun tryAndroid12UnattendedInstall(context: Context, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (!context.packageManager.canRequestPackageInstalls()) return false

        return runCatching {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.use { s ->
                FileInputStream(apkFile).use { input ->
                    s.openWrite("package", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        s.fsync(output)
                    }
                }
                val intent = Intent(context, AppUpdateInstallReceiver::class.java).apply {
                    action = "com.radetski.skipi.action.INSTALL_STATUS"
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                s.commit(pendingIntent.intentSender)
            }
            true
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Unattended package install failed: ${error.message}", error)
        }.getOrDefault(false)
    }

    private fun showInstallReadyNotification(context: Context, apkFile: File) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            20092,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notification = NotificationCompat.Builder(context, "app_update_channel")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("SKIPI update ready to install")
            .setContentText("Tap to complete update")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(20092, notification)
    }
}
