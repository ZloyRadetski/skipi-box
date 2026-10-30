// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

private const val MillisPerDay = 24L * 60L * 60L * 1_000L
private const val UnlimitedSubscriptionTrafficThreshold = Long.MAX_VALUE / 2L

/** Returns the remaining whole days using the metadata's epoch-second expiry. */
fun subscriptionExpiryDays(expireAtSeconds: Long, nowMillis: Long): Long? {
    if (expireAtSeconds <= 0L) return null
    // Saturate instead of wrapping: a far-future expiry must not read as "expired".
    val expireMillis = if (expireAtSeconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else expireAtSeconds * 1_000L
    return (expireMillis - nowMillis).coerceAtLeast(0L) / MillisPerDay
}

/** Returns the finite traffic ratio, or null when the total is unknown or unlimited. */
fun subscriptionTrafficProgress(
    uploadBytes: Long,
    downloadBytes: Long,
    totalBytes: Long,
): Float? {
    if (totalBytes <= 0L || totalBytes >= UnlimitedSubscriptionTrafficThreshold) return null
    return (subscriptionUsedTrafficBytes(uploadBytes, downloadBytes).toDouble() / totalBytes.toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
}

internal fun subscriptionUsedTrafficBytes(uploadBytes: Long, downloadBytes: Long): Long {
    val upload = uploadBytes.coerceAtLeast(0L)
    val download = downloadBytes.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
}
