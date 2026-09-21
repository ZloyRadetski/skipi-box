// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

/** Parsed automatic-refresh interval shared by Android and desktop. */
sealed interface SubscriptionSchedule {
    data object Disabled : SubscriptionSchedule

    data class Enabled(
        val hours: Double,
        val repeatIntervalMillis: Long,
    ) : SubscriptionSchedule

    data object Invalid : SubscriptionSchedule
}

/**
 * Parses a decimal interval in hours. Blank and non-positive values disable a
 * schedule; a positive value must be at least fifteen minutes and fit in a
 * signed millisecond count.
 *
 * The decimal comparison and millisecond conversion are intentionally string
 * based, so Android and desktop do not diverge at Long boundaries or because
 * of binary floating-point rounding. [SubscriptionSchedule.Enabled.hours] is
 * still exposed as Double for the platform scheduling APIs.
 */
fun parseSubscriptionSchedule(value: String): SubscriptionSchedule {
    val normalized = value.trim()
    if (normalized.isEmpty()) return SubscriptionSchedule.Disabled

    val hours = parseSubscriptionDecimalOrNull(normalized) ?: return SubscriptionSchedule.Invalid
    if (hours.signum <= 0) return SubscriptionSchedule.Disabled
    if (hours.compareAbsTo(MinimumSubscriptionHoursDigits, MinimumSubscriptionHoursPower10) < 0) {
        return SubscriptionSchedule.Invalid
    }

    val milliseconds = hours.multipliedBy(MillisecondsPerHour)
    if (milliseconds.exceedsLongMax()) return SubscriptionSchedule.Invalid
    val hoursAsDouble = normalized.toDoubleOrNull() ?: return SubscriptionSchedule.Invalid
    if (!hoursAsDouble.isFinite()) return SubscriptionSchedule.Invalid

    return SubscriptionSchedule.Enabled(
        hours = hoursAsDouble,
        repeatIntervalMillis = milliseconds.truncatedLong(),
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

private data class SubscriptionDecimal(
    val signum: Int,
    val digits: String,
    val power10: Long,
) {
    fun compareAbsTo(otherDigits: String, otherPower10: Long): Int {
        val order = saturatingAdd(power10, digits.length.toLong())
        val otherOrder = saturatingAdd(otherPower10, otherDigits.length.toLong())
        if (order != otherOrder) return order.compareTo(otherOrder)

        val comparisonLength = maxOf(digits.length, otherDigits.length)
        repeat(comparisonLength) { index ->
            val digit = digits.getOrNull(index) ?: '0'
            val otherDigit = otherDigits.getOrNull(index) ?: '0'
            if (digit != otherDigit) return digit.compareTo(otherDigit)
        }
        return 0
    }

    fun multipliedBy(factor: Int): SubscriptionDecimal {
        return copy(digits = digits.multiplyBy(factor))
    }

    fun exceedsLongMax(): Boolean {
        val integerDigitCount = saturatingAdd(digits.length.toLong(), power10)
        return when {
            integerDigitCount > LongMaxDigits.length.toLong() -> true
            integerDigitCount <= 0L -> false
            integerDigitCount < LongMaxDigits.length.toLong() -> false
            else -> {
                val integerDigits = integerDigitCount.toInt()
                val comparison = leadingDigits(integerDigits).compareTo(LongMaxDigits)
                comparison > 0 || (comparison == 0 && hasNonZeroFraction(integerDigits))
            }
        }
    }

    fun truncatedLong(): Long {
        val integerDigitCount = saturatingAdd(digits.length.toLong(), power10)
        if (integerDigitCount <= 0L) return 0L
        return leadingDigits(integerDigitCount.toInt()).toLong()
    }

    private fun leadingDigits(count: Int): String = buildString(count) {
        repeat(count) { index -> append(digits.getOrNull(index) ?: '0') }
    }

    private fun hasNonZeroFraction(integerDigitCount: Int): Boolean {
        return digits.drop(integerDigitCount).any { digit -> digit != '0' }
    }
}

private fun parseSubscriptionDecimalOrNull(value: String): SubscriptionDecimal? {
    var index = 0
    var signum = 1
    when (value.firstOrNull()) {
        '-' -> {
            signum = -1
            index += 1
        }

        '+' -> index += 1
    }

    val digits = StringBuilder(value.length)
    var decimalSeparatorSeen = false
    var fractionalDigitCount = 0L
    while (index < value.length) {
        when (val character = value[index]) {
            in '0'..'9' -> {
                digits.append(character)
                if (decimalSeparatorSeen) fractionalDigitCount += 1L
                index += 1
            }

            '.' if !decimalSeparatorSeen -> {
                decimalSeparatorSeen = true
                index += 1
            }

            else -> break
        }
    }
    if (digits.isEmpty()) return null

    var exponent = 0L
    if (index < value.length) {
        if (value[index] !in charArrayOf('e', 'E')) return null
        exponent = value.parseSubscriptionExponentOrNull(index + 1) ?: return null
        index = value.length
    }
    if (index != value.length) return null

    val significantDigits = digits.toString().trimStart('0')
    if (significantDigits.isEmpty()) {
        return SubscriptionDecimal(signum = 0, digits = "0", power10 = 0L)
    }
    return SubscriptionDecimal(
        signum = signum,
        digits = significantDigits,
        power10 = saturatingSubtract(exponent, fractionalDigitCount),
    )
}

private fun String.parseSubscriptionExponentOrNull(startIndex: Int): Long? {
    var index = startIndex
    var negative = false
    when (getOrNull(index)) {
        '-' -> {
            negative = true
            index += 1
        }

        '+' -> index += 1
    }
    if (index >= length) return null

    var magnitude = 0L
    var saturated = false
    while (index < length) {
        val character = this[index]
        if (character !in '0'..'9') return null
        val digit = character - '0'
        if (!saturated) {
            if (magnitude > (Long.MAX_VALUE - digit) / 10L) {
                magnitude = Long.MAX_VALUE
                saturated = true
            } else {
                magnitude = magnitude * 10L + digit
            }
        }
        index += 1
    }
    return when {
        !negative -> magnitude
        saturated || magnitude == Long.MAX_VALUE -> Long.MIN_VALUE
        else -> -magnitude
    }
}

private fun String.multiplyBy(factor: Int): String {
    var carry = 0L
    val reversed = StringBuilder(length + 8)
    for (index in lastIndex downTo 0) {
        val product = (this[index] - '0') * factor + carry
        reversed.append(('0'.code + (product % 10L).toInt()).toChar())
        carry = product / 10L
    }
    while (carry > 0L) {
        reversed.append(('0'.code + (carry % 10L).toInt()).toChar())
        carry /= 10L
    }
    return reversed.reverse().toString()
}

private fun saturatingAdd(value: Long, increment: Long): Long = when {
    increment > 0L && value > Long.MAX_VALUE - increment -> Long.MAX_VALUE
    increment < 0L && value < Long.MIN_VALUE - increment -> Long.MIN_VALUE
    else -> value + increment
}

private fun saturatingSubtract(value: Long, decrement: Long): Long {
    return if (decrement > 0L && value < Long.MIN_VALUE + decrement) Long.MIN_VALUE
    else value - decrement
}

private const val MillisecondsPerHour = 60 * 60 * 1_000
private const val MinimumSubscriptionHoursDigits = "25"
private const val MinimumSubscriptionHoursPower10 = -2L
private const val LongMaxDigits = "9223372036854775807"
