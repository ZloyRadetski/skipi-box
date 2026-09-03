// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.math.BigDecimal

/**
 * A pure scheduling decision for desktop subscription refreshes.
 *
 * The caller owns the actual timer and refresh work. Supplying [nowMillis]
 * explicitly makes the decision deterministic and keeps this class safe to use
 * from the desktop UI, a background service, or tests.
 */
data class DesktopSubscriptionRefreshPlan(
    val dueSubscriptions: List<DesktopStoredSubscription>,
    val nextWakeupAtMillis: Long?,
    val nextDelayMillis: Long?,
)

/**
 * Computes which stored providers need an automatic refresh and when the next
 * planning pass should run. It deliberately performs no I/O, networking, or
 * clock access.
 *
 * Its interval rules mirror Android's subscription scheduler: blank and
 * non-positive values disable scheduling, positive values below 0.25 hours or
 * values that cannot fit in milliseconds are ignored.
 */
object DesktopSubscriptionRefreshPlanner {
    fun plan(
        library: DesktopSubscriptionLibrary,
        nowMillis: Long,
    ): DesktopSubscriptionRefreshPlan {
        val dueSubscriptions = mutableListOf<DesktopStoredSubscription>()
        var nextWakeupAtMillis: Long? = null

        library.subscriptions.forEach { subscription ->
            val intervalMillis = subscription.refreshIntervalMillisOrNull() ?: return@forEach
            val refreshAtMillis = subscription.nextRefreshAtMillis(intervalMillis)
            val wakeupAtMillis = refreshAtMillis.coerceAtLeast(nowMillis)

            if (refreshAtMillis <= nowMillis) {
                dueSubscriptions += subscription
            }
            val currentNextWakeupAtMillis = nextWakeupAtMillis
            if (currentNextWakeupAtMillis == null || wakeupAtMillis < currentNextWakeupAtMillis) {
                nextWakeupAtMillis = wakeupAtMillis
            }
        }

        return DesktopSubscriptionRefreshPlan(
            dueSubscriptions = dueSubscriptions,
            nextWakeupAtMillis = nextWakeupAtMillis,
            nextDelayMillis = nextWakeupAtMillis?.let { wakeup -> wakeup.delayFrom(nowMillis) },
        )
    }
}

private fun DesktopStoredSubscription.refreshIntervalMillisOrNull(): Long? {
    if (!enabled || url.isBlank()) return null

    val normalized = updateInterval.trim()
    if (normalized.isEmpty()) return null
    val hours = normalized.toBigDecimalOrNull() ?: return null
    if (hours.signum() <= 0) return null
    if (hours < MinimumDesktopSubscriptionRefreshHours) return null

    val intervalMillis = hours.multiply(DesktopSubscriptionRefreshMillisecondsPerHour)
    if (intervalMillis > MaximumDesktopSubscriptionRefreshIntervalMillis) return null
    return intervalMillis.toLong()
}

private fun DesktopStoredSubscription.nextRefreshAtMillis(intervalMillis: Long): Long {
    val lastUpdatedAtMillis = metadata.lastUpdatedAtMillis
    if (lastUpdatedAtMillis <= 0L) return Long.MIN_VALUE
    return if (lastUpdatedAtMillis > Long.MAX_VALUE - intervalMillis) {
        Long.MAX_VALUE
    } else {
        lastUpdatedAtMillis + intervalMillis
    }
}

private fun Long.delayFrom(nowMillis: Long): Long = when {
    this <= nowMillis -> 0L
    nowMillis < 0L && this > Long.MAX_VALUE + nowMillis -> Long.MAX_VALUE
    else -> this - nowMillis
}

private val MinimumDesktopSubscriptionRefreshHours = BigDecimal("0.25")
private val DesktopSubscriptionRefreshMillisecondsPerHour = BigDecimal(60L * 60L * 1_000L)
private val MaximumDesktopSubscriptionRefreshIntervalMillis = BigDecimal.valueOf(Long.MAX_VALUE)
