// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import java.util.Locale

internal fun Long.toTrafficSizeString(): String {
    var size = toDouble()
    var unitIndex = 0
    while (size >= TrafficUnitThreshold && unitIndex < TrafficUnits.lastIndex) {
        size /= TrafficUnitDivisor
        unitIndex += 1
    }
    return String.format(Locale.getDefault(), "%.1f %s", size, TrafficUnits[unitIndex])
}

internal fun Long.toTrafficSpeedString(): String = toTrafficSizeString() + "/s"

private const val TrafficUnitThreshold = 1000L
private const val TrafficUnitDivisor = 1024.0
private val TrafficUnits = listOf("B", "KB", "MB", "GB", "TB", "PB")
