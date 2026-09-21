// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertEquals(0.25, scheduleQuarter.hours, 0.001)
        assertEquals(900_000L, scheduleQuarter.repeatIntervalMillis)

        val scheduleOne = parseSubscriptionSchedule("1")
        assertTrue(scheduleOne is SubscriptionSchedule.Enabled)
        assertEquals(1.0, scheduleOne.hours, 0.001)
        assertEquals(3_600_000L, scheduleOne.repeatIntervalMillis)

        val scheduleDay = parseSubscriptionSchedule("24")
        assertTrue(scheduleDay is SubscriptionSchedule.Enabled)
        assertEquals(24.0, scheduleDay.hours, 0.001)
        assertEquals(86_400_000L, scheduleDay.repeatIntervalMillis)
    }

    @Test
    fun parseSubscriptionSchedule_accepts_decimal_exponents_and_preserves_long_boundary() {
        val exponent = parseSubscriptionSchedule("2.5e-1")
        val largestAllowed = parseSubscriptionSchedule("2562047788015.2155")

        assertEquals(SubscriptionSchedule.Enabled(0.25, 900_000L), exponent)
        assertEquals(
            SubscriptionSchedule.Enabled(2_562_047_788_015.2153, Long.MAX_VALUE - 7L),
            largestAllowed,
        )
        assertEquals(SubscriptionSchedule.Invalid, parseSubscriptionSchedule("2562047788015.215502"))
        assertEquals(SubscriptionSchedule.Invalid, parseSubscriptionSchedule("."))
        assertEquals(SubscriptionSchedule.Invalid, parseSubscriptionSchedule("1e"))
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
