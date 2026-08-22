// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

fun Iterable<String>.toTrimmedNonEmptyList(): List<String> {
    return mapNotNull { value -> value.trim().takeIf(String::isNotEmpty) }
}

fun Iterable<String>.toTrimmedNonEmptyDistinctList(): List<String> {
    return toTrimmedNonEmptyList().distinct()
}

fun String?.toCsvValues(): List<String> {
    return orEmpty()
        .split(',')
        .toTrimmedNonEmptyList()
}

fun String?.toDistinctCsvValues(): List<String> {
    return toCsvValues().distinct()
}

fun String.toIntInRangeOrNull(range: IntRange): Int? {
    return trim().toIntOrNull()?.takeIf { value -> value in range }
}

fun String.toIntInRangeOrDefault(range: IntRange, default: Int): Int {
    return toIntInRangeOrNull(range) ?: default
}

fun String.toIntCoercedInOrDefault(range: IntRange, default: Int): Int {
    val value = trim().toIntOrNull() ?: return default
    return value.coerceIn(range.first, range.last)
}
