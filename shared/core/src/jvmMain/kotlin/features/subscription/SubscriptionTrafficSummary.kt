// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.util.Locale

private const val UnlimitedSubscriptionTrafficThreshold = Long.MAX_VALUE / 2L

/** Formats traffic metadata with the same byte units and default-locale decimals as Android. */
fun subscriptionTrafficSummary(
    uploadBytes: Long,
    downloadBytes: Long,
    totalBytes: Long,
): String? {
    if (uploadBytes < 0L && downloadBytes < 0L && totalBytes < 0L) return null
    val used = subscriptionUsedTrafficBytes(uploadBytes, downloadBytes)
    return if (totalBytes > 0L && totalBytes < UnlimitedSubscriptionTrafficThreshold) {
        "${used.formatSubscriptionBytes()} / ${totalBytes.formatSubscriptionBytes()}"
    } else {
        "${used.formatSubscriptionBytes()} / ∞"
    }
}

private fun Long.formatSubscriptionBytes(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) "$value ${units[unitIndex]}" else {
        String.format(Locale.getDefault(), "%.2f %s", value, units[unitIndex])
    }
}
