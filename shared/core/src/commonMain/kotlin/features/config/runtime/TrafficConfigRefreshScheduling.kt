// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import features.subscription.SubscriptionSchedule
import features.subscription.parseSubscriptionSchedule

/**
 * Platform-neutral fields that determine profile and geo-resource refresh
 * schedules. Android and desktop-owned profile models adapt at their boundary.
 */
data class TrafficConfigRefreshTarget(
    val id: Int,
    val sourceUrl: String,
    val autoUpdate: Boolean,
    val updateLocked: Boolean,
    val updateInterval: String,
    val resourceAutoUpdate: Boolean,
    val resourceUpdateInterval: String,
)

enum class TrafficConfigExistingWorkPolicy {
    UPDATE,
}

data class TrafficConfigWorkSpec(
    val id: Int,
    val uniqueName: String,
    val repeatIntervalMillis: Long,
    val requiresConnectedNetwork: Boolean,
    val policy: TrafficConfigExistingWorkPolicy,
    val backoffMillis: Long,
    val isGeo: Boolean = false,
)

/** In-process state for one periodic WorkManager-compatible schedule. */
data class TrafficConfigPeriodicWorkState(
    val spec: TrafficConfigWorkSpec,
    val periodAnchorMillis: Long,
    val completedPeriods: Long = 0L,
    val runAttemptCount: Int = 0,
    val retryAtMillis: Long? = null,
) {
    init {
        require(spec.repeatIntervalMillis > 0L) { "Periodic work interval must be positive" }
        require(completedPeriods >= 0L) { "Completed periods cannot be negative" }
        require(runAttemptCount >= 0) { "Run attempt count cannot be negative" }
    }

    val profileId: Int get() = spec.id
    val intervalMillis: Long get() = spec.repeatIntervalMillis

    /** Updates the next periodic deadline from its retained anchor without changing retry state. */
    fun withUpdatedSpec(spec: TrafficConfigWorkSpec): TrafficConfigPeriodicWorkState = copy(spec = spec)

    /** The first run is due at its enqueue anchor; later periods anchor to attempt completion. */
    val nextRunAtMillis: Long
        get() = retryAtMillis ?: if (completedPeriods == 0L) {
            periodAnchorMillis
        } else {
            periodAnchorMillis + intervalMillis
        }

    /** Records success or a WorkManager-style exponential retry using this spec's backoff. */
    fun recordAttemptResult(
        succeeded: Boolean,
        atMillis: Long,
    ): TrafficConfigPeriodicWorkState = if (succeeded) {
        copy(
            periodAnchorMillis = atMillis,
            completedPeriods = incrementSaturated(completedPeriods),
            runAttemptCount = 0,
            retryAtMillis = null,
        )
    } else {
        val nextAttempt = incrementSaturated(runAttemptCount)
        val retryDelayMillis = workManagerExponentialBackoffDelayMillis(
            runAttemptCount = nextAttempt,
            baseDelayMillis = spec.backoffMillis,
        )
        copy(
            periodAnchorMillis = atMillis,
            runAttemptCount = nextAttempt,
            retryAtMillis = atMillis + retryDelayMillis,
        )
    }
}

/** Platform gateway for persistent config and geo-resource refresh work. */
interface TrafficConfigScheduleGateway {
    fun scheduledConfigIds(): Set<Int>
    fun scheduledGeoConfigIds(): Set<Int>

    fun enqueueConfig(spec: TrafficConfigWorkSpec)
    fun enqueueGeo(spec: TrafficConfigWorkSpec)

    /** One-time geo refresh, e.g. right after a provider routing import. */
    fun enqueueGeoOnce(configId: Int)

    fun cancelConfig(configId: Int)
    fun cancelGeo(configId: Int)

    fun storeScheduledConfigIds(configIds: Set<Int>)
    fun storeScheduledGeoConfigIds(configIds: Set<Int>)
}

/** Reconciles persistent profile and geo-resource refresh work. */
class TrafficConfigScheduler(
    private val gateway: TrafficConfigScheduleGateway,
) {
    fun reconcile(targets: List<TrafficConfigRefreshTarget>) {
        val desiredConfigs = targets.mapNotNull(TrafficConfigRefreshTarget::profileRefreshWorkSpecOrNull)
        val desiredConfigIds = desiredConfigs.mapTo(mutableSetOf()) { it.id }
        (gateway.scheduledConfigIds() - desiredConfigIds).forEach(gateway::cancelConfig)
        desiredConfigs.forEach(gateway::enqueueConfig)
        gateway.storeScheduledConfigIds(desiredConfigIds)

        val desiredGeoConfigs = targets.mapNotNull(TrafficConfigRefreshTarget::geoRefreshWorkSpecOrNull)
        val desiredGeoConfigIds = desiredGeoConfigs.mapTo(mutableSetOf()) { it.id }
        (gateway.scheduledGeoConfigIds() - desiredGeoConfigIds).forEach(gateway::cancelGeo)
        desiredGeoConfigs.forEach(gateway::enqueueGeo)
        gateway.storeScheduledGeoConfigIds(desiredGeoConfigIds)
    }
}

fun trafficConfigWorkName(configId: Int): String = "traffic-config-update-$configId"
fun trafficConfigGeoWorkName(configId: Int): String = "traffic-config-geo-update-$configId"
fun trafficConfigGeoOnceWorkName(configId: Int): String = "traffic-config-geo-once-$configId"

private fun TrafficConfigRefreshTarget.profileRefreshIntervalMillisOrNull(): Long? {
    if (!autoUpdate || updateLocked || sourceUrl.isBlank()) return null
    return (parseSubscriptionSchedule(updateInterval) as? SubscriptionSchedule.Enabled)
        ?.repeatIntervalMillis
}

/** Returns this profile's periodic refresh work when its profile schedule is eligible. */
fun TrafficConfigRefreshTarget.profileRefreshWorkSpecOrNull(): TrafficConfigWorkSpec? {
    val schedule = profileRefreshIntervalMillisOrNull() ?: return null
    return TrafficConfigWorkSpec(
        id = id,
        uniqueName = trafficConfigWorkName(id),
        repeatIntervalMillis = schedule,
        requiresConnectedNetwork = true,
        policy = TrafficConfigExistingWorkPolicy.UPDATE,
        backoffMillis = MinimumConfigBackoffMillis,
        isGeo = false,
    )
}

private fun TrafficConfigRefreshTarget.geoRefreshIntervalMillisOrNull(): Long? {
    if (!resourceAutoUpdate) return null
    return (parseSubscriptionSchedule(resourceUpdateInterval) as? SubscriptionSchedule.Enabled)
        ?.repeatIntervalMillis
}

/** Returns this profile's periodic resource refresh work, independently of profile refresh eligibility. */
fun TrafficConfigRefreshTarget.geoRefreshWorkSpecOrNull(): TrafficConfigWorkSpec? {
    val schedule = geoRefreshIntervalMillisOrNull() ?: return null
    return TrafficConfigWorkSpec(
        id = id,
        uniqueName = trafficConfigGeoWorkName(id),
        repeatIntervalMillis = schedule,
        requiresConnectedNetwork = true,
        policy = TrafficConfigExistingWorkPolicy.UPDATE,
        backoffMillis = MinimumConfigBackoffMillis,
        isGeo = true,
    )
}

/**
 * Exponential retry delay for the Desktop resource refresh host, matching the geo WorkManager
 * request: 15-minute base, doubling by attempt, capped at WorkManager's 5-hour maximum.
 * Attempts follow WorkManager's one-based retry count.
 */
fun trafficConfigGeoRetryDelayMillis(runAttemptCount: Int): Long {
    return workManagerExponentialBackoffDelayMillis(
        runAttemptCount = runAttemptCount,
        baseDelayMillis = MinimumConfigBackoffMillis,
    )
}

internal fun workManagerExponentialBackoffDelayMillis(
    runAttemptCount: Int,
    baseDelayMillis: Long,
): Long {
    require(runAttemptCount >= 1) { "Retry attempt count must be positive" }
    require(baseDelayMillis in 1..MaximumWorkBackoffMillis) { "Backoff base must be within the WorkManager range" }
    var delayMillis = baseDelayMillis
    var doublingsRemaining = runAttemptCount - 1
    while (doublingsRemaining > 0 && delayMillis < MaximumWorkBackoffMillis) {
        delayMillis = if (delayMillis > MaximumWorkBackoffMillis / 2) {
            MaximumWorkBackoffMillis
        } else {
            delayMillis * 2
        }
        doublingsRemaining -= 1
    }
    return delayMillis
}

private fun incrementSaturated(value: Int): Int = if (value == Int.MAX_VALUE) value else value + 1

private fun incrementSaturated(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1

private const val MinimumConfigBackoffMillis = 15 * 60 * 1_000L
private const val MaximumWorkBackoffMillis = 5 * 60 * 60 * 1_000L
