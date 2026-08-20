// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import app.AppState
import app.R
import app.SubscriptionExpiryReminder
import app.SubscriptionGroupState

internal object SubscriptionExpiryNotifier {
    const val ChannelId = "subscription_expiry_channel"
    private const val PrefsName = "subscription_expiry_notified_state"
    private const val BaseNotificationId = 30000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val name = context.getString(R.string.notification_channel_subscription_expiry)
            val descriptionText = context.getString(R.string.notification_channel_subscription_expiry_desc)
            val channel = NotificationChannel(
                ChannelId,
                name,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = descriptionText
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun checkAndNotify(context: Context, appState: AppState) {
        if (!appState.enableSubscriptionExpiryNotifications) return
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val nowSeconds = System.currentTimeMillis() / 1000L
        val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

        for (group in appState.subscriptionGroups) {
            if (!group.enabled || !group.notifyOnExpiry || group.trafficExpireAtSeconds <= 0L) {
                continue
            }

            val expireSeconds = group.trafficExpireAtSeconds
            val reminders = (group.customExpiryReminders ?: appState.subscriptionExpiryReminders)
                .sortedByDescending { it.totalSeconds }

            val historyKey = "sent_${group.id}_$expireSeconds"
            val sentSet = prefs.getStringSet(historyKey, emptySet())?.toMutableSet() ?: mutableSetOf()

            for (reminder in reminders) {
                val reminderKey = reminder.toSerializedString()
                val triggerSeconds = expireSeconds - reminder.totalSeconds

                // Check if current time reached this reminder's trigger threshold
                if (nowSeconds >= triggerSeconds && !sentSet.contains(reminderKey)) {
                    val remainingSeconds = (expireSeconds - nowSeconds).coerceAtLeast(0L)
                    val isExpired = nowSeconds >= expireSeconds

                    val title = if (isExpired) {
                        context.getString(R.string.subscription_expiry_notification_title_expired)
                    } else {
                        context.getString(R.string.subscription_expiry_notification_title_expiring)
                    }

                    val dateFormatted = formatExpiryDate(expireSeconds)
                    val body = if (isExpired) {
                        context.getString(
                            R.string.subscription_expiry_notification_body_expired,
                            group.name.ifBlank { context.getString(R.string.subscription_default_group) },
                            dateFormatted,
                        )
                    } else {
                        val durationFormatted = formatRemainingDuration(context, reminder.totalSeconds.takeIf { it > 0 } ?: remainingSeconds)
                        context.getString(
                            R.string.subscription_expiry_notification_body_expiring,
                            group.name.ifBlank { context.getString(R.string.subscription_default_group) },
                            durationFormatted,
                            dateFormatted,
                        )
                    }

                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = launchIntent?.let {
                        PendingIntent.getActivity(
                            context,
                            group.id,
                            it,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    }

                    val iconRes = R.drawable.ic_about_logo

                    val notification = NotificationCompat.Builder(context, ChannelId)
                        .setSmallIcon(iconRes)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()

                    notificationManager.notify(BaseNotificationId + group.id, notification)

                    sentSet.add(reminderKey)
                    prefs.edit { putStringSet(historyKey, sentSet) }
                }
            }
        }
    }
}
