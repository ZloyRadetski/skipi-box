// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.AppState
import features.subscription.SubscriptionExpirySchedule
import features.subscription.SubscriptionExpiryScheduleEntry

internal object SubscriptionExpiryScheduler {
    const val ActionCheckExpiry = "com.radetski.skipi.action.CHECK_SUBSCRIPTION_EXPIRY"

    fun schedule(context: Context, state: AppState) {
        if (!state.enableSubscriptionExpiryNotifications) {
            cancel(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nowSeconds = System.currentTimeMillis() / 1000L
        val targetSeconds = SubscriptionExpirySchedule.nextTriggerSeconds(
            notificationsEnabled = state.enableSubscriptionExpiryNotifications,
            entries = state.subscriptionGroups.map { group ->
                SubscriptionExpiryScheduleEntry(
                    enabled = group.enabled,
                    notifyOnExpiry = group.notifyOnExpiry,
                    expireAtSeconds = group.trafficExpireAtSeconds,
                    customReminders = group.customExpiryReminders,
                )
            },
            defaultReminders = state.subscriptionExpiryReminders,
            nowSeconds = nowSeconds,
        ) ?: return
        val intent = Intent(context, SubscriptionExpiryReceiver::class.java).apply {
            action = ActionCheckExpiry
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerMillis = targetSeconds * 1000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SubscriptionExpiryReceiver::class.java).apply {
            action = ActionCheckExpiry
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
