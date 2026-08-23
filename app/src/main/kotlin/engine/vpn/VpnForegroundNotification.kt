// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import app.R

/**
 * The VPN service owns the foreground lifecycle. The traffic service updates
 * this same notification with live counters when that optional feature is on.
 */
internal object VpnForegroundNotification {
    const val ChannelId = "proxy_traffic_stats"
    const val NotificationId = 3001

    fun ensureChannel(
        context: Context,
        notificationManager: NotificationManager,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                context.getString(R.string.proxy_traffic_stats_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }
}
