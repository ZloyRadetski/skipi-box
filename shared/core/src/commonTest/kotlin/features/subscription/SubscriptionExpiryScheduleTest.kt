// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionExpiryScheduleTest {
    @Test
    fun chooses_earliest_future_trigger_from_android_scheduler_fixture() {
        val nowSeconds = 1_700_000_000L
        val atExpiration = SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration)
        val entries = listOf(
            reminderEntry(expireAtSeconds = nowSeconds + 20 * DaySeconds),
            reminderEntry(
                expireAtSeconds = nowSeconds + 5 * DaySeconds,
                customReminders = listOf(atExpiration),
            ),
            reminderEntry(
                expireAtSeconds = nowSeconds + 2 * DaySeconds,
                enabled = false,
                customReminders = listOf(atExpiration),
            ),
            reminderEntry(
                expireAtSeconds = nowSeconds + DaySeconds,
                notifyOnExpiry = false,
                customReminders = listOf(atExpiration),
            ),
            reminderEntry(
                expireAtSeconds = nowSeconds + 4 * DaySeconds,
                customReminders = emptyList(),
            ),
            reminderEntry(
                expireAtSeconds = 0L,
                customReminders = listOf(atExpiration),
            ),
        )

        val triggerSeconds = SubscriptionExpirySchedule.nextTriggerSeconds(
            notificationsEnabled = true,
            entries = entries,
            defaultReminders = listOf(
                SubscriptionExpiryReminder(3, ExpiryReminderUnit.Days),
                SubscriptionExpiryReminder(1, ExpiryReminderUnit.Days),
            ),
            nowSeconds = nowSeconds,
        )

        assertEquals(nowSeconds + 5 * DaySeconds, triggerSeconds)
    }

    @Test
    fun global_disable_and_triggers_at_or_before_now_produce_no_schedule() {
        val nowSeconds = 1_700_000_000L
        val atExpiration = SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration)
        val dueEntry = reminderEntry(
            expireAtSeconds = nowSeconds + DaySeconds,
            customReminders = listOf(SubscriptionExpiryReminder(1, ExpiryReminderUnit.Days)),
        )

        assertNull(
            SubscriptionExpirySchedule.nextTriggerSeconds(
                notificationsEnabled = false,
                entries = listOf(reminderEntry(expireAtSeconds = nowSeconds + 3 * DaySeconds)),
                defaultReminders = listOf(atExpiration),
                nowSeconds = nowSeconds,
            ),
        )
        assertNull(
            SubscriptionExpirySchedule.nextTriggerSeconds(
                notificationsEnabled = true,
                entries = listOf(dueEntry),
                defaultReminders = emptyList(),
                nowSeconds = nowSeconds,
            ),
        )
    }

    @Test
    fun non_positive_expiration_is_ineligible_even_when_its_trigger_is_future() {
        val triggerSeconds = SubscriptionExpirySchedule.nextTriggerSeconds(
            notificationsEnabled = true,
            entries = listOf(
                reminderEntry(
                    expireAtSeconds = 0L,
                    customReminders = listOf(SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration)),
                ),
            ),
            defaultReminders = emptyList(),
            nowSeconds = -1L,
        )

        assertNull(triggerSeconds)
    }
}

private const val DaySeconds = 24 * 60 * 60L

private fun reminderEntry(
    expireAtSeconds: Long,
    enabled: Boolean = true,
    notifyOnExpiry: Boolean = true,
    customReminders: List<SubscriptionExpiryReminder>? = null,
) = SubscriptionExpiryScheduleEntry(
    enabled = enabled,
    notifyOnExpiry = notifyOnExpiry,
    expireAtSeconds = expireAtSeconds,
    customReminders = customReminders,
)
