// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import app.R

/** Synchronous store: the process is killed right after the marker is written. */
internal object EmergencyStopMarkers {
    private const val PrefsName = "emergency_stop"
    private const val KeyAt = "at"

    fun of(context: Context): EmergencyStopMarker {
        val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        return EmergencyStopMarker(
            object : EmergencyStopMarkerStore {
                override fun read(): Long = prefs.getLong(KeyAt, 0L)
                override fun write(epochMillis: Long) {
                    prefs.edit().putLong(KeyAt, epochMillis).commit()
                }
            },
        )
    }
}

/**
 * Gets the user back into the app after the emergency kill.
 *
 * minSdk is 24. Up to API 28 an AlarmManager PendingIntent (about 500 ms
 * ahead) relaunches the main activity. From API 29 the system blocks activity
 * starts from the background (an alarm is not an exemption), so instead a
 * notification "SKIPI was restarted after an emergency stop" that opens the
 * app is posted before the kill; a notification outlives the process.
 */
internal object EmergencyRelaunch {
    private const val RelaunchDelayMillis = 500L
    private const val NotificationId = 3003

    fun schedule(
        context: Context,
        notificationManager: NotificationManager,
        contentIntent: PendingIntent?,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pending = PendingIntent.getActivity(
                context,
                1,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            context.getSystemService(AlarmManager::class.java).setExact(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + RelaunchDelayMillis,
                pending,
            )
        } else {
            VpnForegroundNotification.ensureChannel(context, notificationManager)
            notificationManager.notify(
                NotificationId,
                VpnForegroundNotification.newBuilder(context)
                    .setSmallIcon(R.drawable.ic_qs_proxy)
                    .setContentTitle(context.getString(R.string.vpn_emergency_restart_title))
                    .setContentText(context.getString(R.string.vpn_emergency_restart_text))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .build(),
            )
        }
    }
}
