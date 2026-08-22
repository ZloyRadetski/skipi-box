// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.math.BigDecimal

sealed interface SubscriptionSchedule {
    data object Disabled : SubscriptionSchedule

    data class Enabled(
        val hours: Double,
        val repeatIntervalMillis: Long,
    ) : SubscriptionSchedule

    data object Invalid : SubscriptionSchedule
}

fun parseSubscriptionSchedule(value: String): SubscriptionSchedule {
    val normalized = value.trim()
    if (normalized.isEmpty()) return SubscriptionSchedule.Disabled
    val hours = normalized.toBigDecimalOrNull() ?: return SubscriptionSchedule.Invalid
    if (hours.compareTo(BigDecimal.ZERO) == 0 || hours < BigDecimal.ZERO) return SubscriptionSchedule.Disabled
    if (hours < MinimumSubscriptionHours) return SubscriptionSchedule.Invalid
    val milliseconds = hours.multiply(MillisecondsPerHour)
    if (milliseconds > MaximumLongDecimal) return SubscriptionSchedule.Invalid
    val hoursAsDouble = hours.toDouble()
    if (!hoursAsDouble.isFinite()) return SubscriptionSchedule.Invalid
    return SubscriptionSchedule.Enabled(
        hours = hoursAsDouble,
        repeatIntervalMillis = milliseconds.toLong(),
    )
}

fun sanitizeSubscriptionIntervalInput(value: String): String = buildString {
    var decimalSeparatorSeen = false
    value.forEach { character ->
        when {
            character.isDigit() -> append(character)
            character == '.' && !decimalSeparatorSeen -> {
                append(character)
                decimalSeparatorSeen = true
            }
        }
    }
}

fun isValidSubscriptionIntervalInput(value: String): Boolean {
    return parseSubscriptionSchedule(value) !is SubscriptionSchedule.Invalid
}

private val MinimumSubscriptionHours = BigDecimal("0.25")
private val MillisecondsPerHour = BigDecimal(60L * 60L * 1_000L)
private val MaximumLongDecimal = BigDecimal.valueOf(Long.MAX_VALUE)
