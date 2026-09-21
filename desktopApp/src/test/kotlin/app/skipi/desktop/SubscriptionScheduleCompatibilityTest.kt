// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.SubscriptionSchedule
import features.subscription.parseSubscriptionSchedule
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/** Locks the common parser to the former JVM BigDecimal behavior. */
class SubscriptionScheduleCompatibilityTest {
    @Test
    fun common_parser_matches_the_previous_big_decimal_rules() {
        val inputs = listOf(
            "",
            "   ",
            "0",
            "-0",
            "-1",
            "+1",
            ".25",
            "0.2500000",
            "0.249999999",
            "1.",
            "2.5e-1",
            "1e-1",
            "1E3",
            "1e-999",
            "-1e999",
            "2562047788015.2155",
            "2562047788015.215502",
            "1e999",
            ".",
            "1e",
            "1.2.3",
            "NaN",
            "Infinity",
        )

        inputs.forEach { value ->
            assertEquals(referenceSchedule(value), parseSubscriptionSchedule(value), "value=$value")
        }
    }
}

private fun referenceSchedule(value: String): SubscriptionSchedule {
    val normalized = value.trim()
    if (normalized.isEmpty()) return SubscriptionSchedule.Disabled
    val hours = normalized.toBigDecimalOrNull() ?: return SubscriptionSchedule.Invalid
    if (hours.signum() <= 0) return SubscriptionSchedule.Disabled
    if (hours < ReferenceMinimumHours) return SubscriptionSchedule.Invalid
    val milliseconds = hours.multiply(ReferenceMillisecondsPerHour)
    if (milliseconds > ReferenceMaximumLong) return SubscriptionSchedule.Invalid
    val hoursAsDouble = hours.toDouble()
    if (!hoursAsDouble.isFinite()) return SubscriptionSchedule.Invalid
    return SubscriptionSchedule.Enabled(
        hours = hoursAsDouble,
        repeatIntervalMillis = milliseconds.toLong(),
    )
}

private val ReferenceMinimumHours = BigDecimal("0.25")
private val ReferenceMillisecondsPerHour = BigDecimal(60L * 60L * 1_000L)
private val ReferenceMaximumLong = BigDecimal.valueOf(Long.MAX_VALUE)
