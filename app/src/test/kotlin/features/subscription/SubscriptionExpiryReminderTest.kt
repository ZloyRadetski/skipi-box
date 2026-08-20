// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import app.ExpiryReminderUnit
import app.SubscriptionExpiryReminder
import app.SubscriptionGroupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionExpiryReminderTest {

    @Test
    fun totalSeconds_calculates_correct_seconds_for_each_unit() {
        assertEquals(0L, SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration).totalSeconds)
        assertEquals(1800L, SubscriptionExpiryReminder(30, ExpiryReminderUnit.Minutes).totalSeconds)
        assertEquals(43200L, SubscriptionExpiryReminder(12, ExpiryReminderUnit.Hours).totalSeconds)
        assertEquals(259200L, SubscriptionExpiryReminder(3, ExpiryReminderUnit.Days).totalSeconds)
        assertEquals(1209600L, SubscriptionExpiryReminder(2, ExpiryReminderUnit.Weeks).totalSeconds)
    }

    @Test
    fun serialization_and_deserialization_roundtrip_works() {
        val reminders = listOf(
            SubscriptionExpiryReminder(7, ExpiryReminderUnit.Days),
            SubscriptionExpiryReminder(12, ExpiryReminderUnit.Hours),
            SubscriptionExpiryReminder(45, ExpiryReminderUnit.Minutes),
            SubscriptionExpiryReminder(2, ExpiryReminderUnit.Weeks),
            SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration),
        )

        for (reminder in reminders) {
            val serialized = reminder.toSerializedString()
            val deserialized = SubscriptionExpiryReminder.fromSerializedStringOrNull(serialized)
            assertNotNull(deserialized)
            assertEquals(reminder.value, deserialized?.value)
            assertEquals(reminder.unit, deserialized?.unit)
            assertEquals(reminder.totalSeconds, deserialized?.totalSeconds)
        }
    }

    @Test
    fun invalid_serialized_string_returns_null() {
        assertNull(SubscriptionExpiryReminder.fromSerializedStringOrNull(""))
        assertNull(SubscriptionExpiryReminder.fromSerializedStringOrNull("invalid"))
        assertNull(SubscriptionExpiryReminder.fromSerializedStringOrNull("3:InvalidUnit"))
        assertNull(SubscriptionExpiryReminder.fromSerializedStringOrNull("abc:Days"))
    }

    @Test
    fun reminders_sorting_and_deduplication_by_totalSeconds() {
        val list = listOf(
            SubscriptionExpiryReminder(1, ExpiryReminderUnit.Days),
            SubscriptionExpiryReminder(2, ExpiryReminderUnit.Weeks),
            SubscriptionExpiryReminder(24, ExpiryReminderUnit.Hours), // Same total seconds as 1 Day
            SubscriptionExpiryReminder(3, ExpiryReminderUnit.Days),
            SubscriptionExpiryReminder(0, ExpiryReminderUnit.AtExpiration),
        )

        val uniqueSorted = list.distinctBy { it.totalSeconds }.sortedByDescending { it.totalSeconds }

        assertEquals(4, uniqueSorted.size)
        assertEquals(ExpiryReminderUnit.Weeks, uniqueSorted[0].unit)
        assertEquals(3, uniqueSorted[1].value)
        assertEquals(ExpiryReminderUnit.Days, uniqueSorted[1].unit)
        assertEquals(1, uniqueSorted[2].value)
        assertEquals(ExpiryReminderUnit.AtExpiration, uniqueSorted[3].unit)
    }

    @Test
    fun subscription_group_notifyOnExpiry_defaults_and_custom_reminders() {
        val defaultGroup = SubscriptionGroupState(
            id = 1,
            name = "Test",
            url = "https://example.com/sub",
            userAgent = "v2ray",
            updateInterval = "1d",
            enabled = true,
        )

        assertTrue(defaultGroup.notifyOnExpiry)
        assertNull(defaultGroup.customExpiryReminders)

        val customGroup = defaultGroup.copy(
            notifyOnExpiry = false,
            customExpiryReminders = listOf(
                SubscriptionExpiryReminder(5, ExpiryReminderUnit.Days),
            ),
        )

        assertEquals(false, customGroup.notifyOnExpiry)
        assertEquals(1, customGroup.customExpiryReminders?.size)
        assertEquals(5, customGroup.customExpiryReminders?.first()?.value)
    }
}
