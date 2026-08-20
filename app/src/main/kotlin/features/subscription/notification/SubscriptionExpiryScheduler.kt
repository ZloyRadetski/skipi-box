// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.AppState

internal object SubscriptionExpiryScheduler {
    const val ActionCheckExpiry = "com.radetski.skipi.action.CHECK_SUBSCRIPTION_EXPIRY"

    fun schedule(context: Context, state: AppState) {
        if (!state.enableSubscriptionExpiryNotifications) {
            cancel(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nowSeconds = System.currentTimeMillis() / 1000L

        var nextTriggerSeconds: Long? = null

        for (group in state.subscriptionGroups) {
            if (!group.enabled || !group.notifyOnExpiry || group.trafficExpireAtSeconds <= 0L) continue
            val expireSeconds = group.trafficExpireAtSeconds
            val reminders = group.customExpiryReminders ?: state.subscriptionExpiryReminders
            for (reminder in reminders) {
                val triggerSeconds = expireSeconds - reminder.totalSeconds
                if (triggerSeconds > nowSeconds) {
                    if (nextTriggerSeconds == null || triggerSeconds < nextTriggerSeconds) {
                        nextTriggerSeconds = triggerSeconds
                    }
                }
            }
        }

        val targetSeconds = nextTriggerSeconds ?: return
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
