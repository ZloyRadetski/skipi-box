// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

/** The portable fields needed to decide whether one subscription has a future expiry reminder. */
data class SubscriptionExpiryScheduleEntry(
    val enabled: Boolean,
    val notifyOnExpiry: Boolean,
    val expireAtSeconds: Long,
    val customReminders: List<SubscriptionExpiryReminder>? = null,
)

/** Pure eligibility and earliest-trigger policy shared by host AlarmManager/timer adapters. */
object SubscriptionExpirySchedule {
    fun nextTriggerSeconds(
        notificationsEnabled: Boolean,
        entries: List<SubscriptionExpiryScheduleEntry>,
        defaultReminders: List<SubscriptionExpiryReminder>,
        nowSeconds: Long,
    ): Long? {
        if (!notificationsEnabled) return null

        var nextTriggerSeconds: Long? = null
        for (entry in entries) {
            if (!entry.enabled || !entry.notifyOnExpiry || entry.expireAtSeconds <= 0L) continue
            val reminders = entry.customReminders ?: defaultReminders
            for (reminder in reminders) {
                val triggerSeconds = entry.expireAtSeconds - reminder.totalSeconds
                if (triggerSeconds > nowSeconds) {
                    if (nextTriggerSeconds == null || triggerSeconds < nextTriggerSeconds) {
                        nextTriggerSeconds = triggerSeconds
                    }
                }
            }
        }
        return nextTriggerSeconds
    }
}
