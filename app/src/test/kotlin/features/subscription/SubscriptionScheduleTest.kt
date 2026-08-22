// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionScheduleTest {

    @Test
    fun parseSubscriptionSchedule_zero_or_negative_returns_disabled() {
        assertEquals(SubscriptionSchedule.Disabled, parseSubscriptionSchedule("0"))
        assertEquals(SubscriptionSchedule.Disabled, parseSubscriptionSchedule("0.0"))
        assertEquals(SubscriptionSchedule.Disabled, parseSubscriptionSchedule("0.00"))
        assertEquals(SubscriptionSchedule.Disabled, parseSubscriptionSchedule("-1"))
        assertEquals(SubscriptionSchedule.Disabled, parseSubscriptionSchedule(""))
        assertEquals(SubscriptionSchedule.Disabled, parseSubscriptionSchedule("   "))
    }

    @Test
    fun parseSubscriptionSchedule_less_than_minimum_positive_returns_invalid() {
        assertEquals(SubscriptionSchedule.Invalid, parseSubscriptionSchedule("0.1"))
        assertEquals(SubscriptionSchedule.Invalid, parseSubscriptionSchedule("0.24"))
        assertEquals(SubscriptionSchedule.Invalid, parseSubscriptionSchedule("abc"))
    }

    @Test
    fun parseSubscriptionSchedule_valid_hours_returns_enabled() {
        val scheduleQuarter = parseSubscriptionSchedule("0.25")
        assertTrue(scheduleQuarter is SubscriptionSchedule.Enabled)
        assertEquals(0.25, (scheduleQuarter as SubscriptionSchedule.Enabled).hours, 0.001)
        assertEquals(900_000L, scheduleQuarter.repeatIntervalMillis)

        val scheduleOne = parseSubscriptionSchedule("1")
        assertTrue(scheduleOne is SubscriptionSchedule.Enabled)
        assertEquals(1.0, (scheduleOne as SubscriptionSchedule.Enabled).hours, 0.001)
        assertEquals(3_600_000L, scheduleOne.repeatIntervalMillis)

        val scheduleDay = parseSubscriptionSchedule("24")
        assertTrue(scheduleDay is SubscriptionSchedule.Enabled)
        assertEquals(24.0, (scheduleDay as SubscriptionSchedule.Enabled).hours, 0.001)
        assertEquals(86_400_000L, scheduleDay.repeatIntervalMillis)
    }

    @Test
    fun isValidSubscriptionIntervalInput_handles_zero_properly() {
        assertTrue(isValidSubscriptionIntervalInput("0"))
        assertTrue(isValidSubscriptionIntervalInput("0.0"))
        assertTrue(isValidSubscriptionIntervalInput(""))
        assertTrue(isValidSubscriptionIntervalInput("1"))
        assertTrue(isValidSubscriptionIntervalInput("0.25"))

        assertFalse(isValidSubscriptionIntervalInput("0.1"))
        assertFalse(isValidSubscriptionIntervalInput("abc"))
    }
}
