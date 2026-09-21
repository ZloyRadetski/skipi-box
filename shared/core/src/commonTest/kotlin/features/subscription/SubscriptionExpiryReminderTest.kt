// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SubscriptionExpiryReminderTest {
    @Test
    fun calculatesSecondsAndRoundTripsStorageFormat() {
        val reminders = listOf(
            SubscriptionExpiryReminder(30, ExpiryReminderUnit.Minutes),
            SubscriptionExpiryReminder(12, ExpiryReminderUnit.Hours),
            SubscriptionExpiryReminder(3, ExpiryReminderUnit.Days),
            SubscriptionExpiryReminder(2, ExpiryReminderUnit.Weeks),
            SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration),
        )

        assertEquals(listOf(1_800L, 43_200L, 259_200L, 1_209_600L, 0L), reminders.map { it.totalSeconds })
        reminders.forEach { reminder ->
            assertEquals(reminder, SubscriptionExpiryReminder.fromSerializedStringOrNull(reminder.toSerializedString()))
        }
        assertNull(SubscriptionExpiryReminder.fromSerializedStringOrNull("invalid"))
    }

    @Test
    fun validatesPersistedCustomReminderListsForBothPlatforms() {
        val reminders = listOf(
            SubscriptionExpiryReminder(1, ExpiryReminderUnit.Days),
            SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration),
        )
        assertEquals(reminders, validateSubscriptionExpiryReminders(reminders))
        assertFailsWith<IllegalArgumentException> {
            validateSubscriptionExpiryReminders(listOf(SubscriptionExpiryReminder(0, ExpiryReminderUnit.Days)))
        }
        assertFailsWith<IllegalArgumentException> {
            validateSubscriptionExpiryReminders(listOf(SubscriptionExpiryReminder(1, ExpiryReminderUnit.AtExpiration)))
        }
    }
}
