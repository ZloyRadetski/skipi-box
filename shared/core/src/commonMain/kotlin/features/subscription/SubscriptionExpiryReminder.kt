// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlinx.serialization.Serializable

/** Unit used by both platform UIs and persisted subscription reminder lists. */
@Serializable
enum class ExpiryReminderUnit {
    Minutes,
    Hours,
    Days,
    Weeks,
    AtExpiration,
}

/**
 * A point before a subscription expiration when the user should be notified.
 * Its compact `value:UNIT` form is kept for Android database and preferences
 * compatibility.
 */
@Serializable
data class SubscriptionExpiryReminder(
    val value: Int = 1,
    val unit: ExpiryReminderUnit = ExpiryReminderUnit.Days,
) {
    val totalSeconds: Long
        get() = when (unit) {
            ExpiryReminderUnit.AtExpiration -> 0L
            ExpiryReminderUnit.Minutes -> value.coerceAtLeast(1) * 60L
            ExpiryReminderUnit.Hours -> value.coerceAtLeast(1) * 3600L
            ExpiryReminderUnit.Days -> value.coerceAtLeast(1) * 86400L
            ExpiryReminderUnit.Weeks -> value.coerceAtLeast(1) * 7 * 86400L
        }

    fun toSerializedString(): String = "$value:${unit.name}"

    companion object {
        fun fromSerializedStringOrNull(value: String): SubscriptionExpiryReminder? {
            val parts = value.split(":")
            if (parts.size != 2) return null
            val reminderValue = parts[0].toIntOrNull() ?: return null
            val unit = runCatching { ExpiryReminderUnit.valueOf(parts[1]) }.getOrNull() ?: return null
            return SubscriptionExpiryReminder(reminderValue, unit)
        }
    }
}

val DefaultSubscriptionExpiryReminders: List<SubscriptionExpiryReminder> = listOf(
    SubscriptionExpiryReminder(3, ExpiryReminderUnit.Days),
    SubscriptionExpiryReminder(1, ExpiryReminderUnit.Days),
)

/** Validates the UI-editable form and returns a defensive copy for storage. */
fun validateSubscriptionExpiryReminders(
    reminders: List<SubscriptionExpiryReminder>?,
): List<SubscriptionExpiryReminder>? {
    reminders?.forEach { reminder ->
        when (reminder.unit) {
            ExpiryReminderUnit.AtExpiration -> require(reminder.value == 0) {
                "At-expiration reminder must use zero"
            }

            else -> require(reminder.value > 0) {
                "Expiry reminder value must be positive"
            }
        }
    }
    return reminders?.toList()
}
