// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

/** An automatic release check is due once the last successful one is older than the TTL. */
fun isAppUpdateCheckDue(
    lastCheckMillis: Long,
    nowMillis: Long,
): Boolean {
    return lastCheckMillis <= 0L ||
        nowMillis - lastCheckMillis >= AppUpdateCheckTtlMillis
}

/** A failed automatic attempt only delays the next attempt briefly; it never advances the TTL. */
fun isAppUpdateAutomaticAttemptDue(
    lastAttemptMillis: Long,
    nowMillis: Long,
): Boolean {
    return lastAttemptMillis <= 0L ||
        nowMillis - lastAttemptMillis >= AppUpdateAutomaticRetryMinMillis
}

const val AppUpdateCheckTtlMillis = 24L * 60L * 60L * 1_000L
const val AppUpdateAutomaticRetryMinMillis = 60L * 60L * 1_000L
