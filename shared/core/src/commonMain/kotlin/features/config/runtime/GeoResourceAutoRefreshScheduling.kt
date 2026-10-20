// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

/** In-process periodic schedule state for the resource-only Desktop refresh host. */
data class GeoResourcePeriodicSchedule(
    val profileId: Int,
    val intervalMillis: Long,
    val periodAnchorMillis: Long,
    val completedPeriods: Long = 0,
    val runAttemptCount: Int = 0,
    val retryAtMillis: Long? = null,
) {
    init {
        require(intervalMillis > 0) { "Resource refresh interval must be positive" }
        require(completedPeriods >= 0) { "Completed periods cannot be negative" }
        require(runAttemptCount >= 0) { "Run attempt count cannot be negative" }
    }

    /** The next due time uses the latest enqueue anchor, including retry enqueues. */
    val nextRunAtMillis: Long
        get() = retryAtMillis ?: if (completedPeriods == 0L) {
            periodAnchorMillis
        } else {
            periodAnchorMillis + intervalMillis
        }
}

data class GeoResourcePeriodicScheduleState(
    val schedulesByProfileId: Map<Int, GeoResourcePeriodicSchedule> = emptyMap(),
) {
    /**
     * Reconcile only resource-auto-update eligibility. A new schedule is due
     * immediately. Existing schedules retain their latest enqueue anchor and
     * retry state when their interval is updated.
     */
    fun reconcile(
        targets: List<TrafficConfigRefreshTarget>,
        nowMillis: Long,
    ): GeoResourcePeriodicScheduleReconciliation {
        val nextSchedules = mutableMapOf<Int, GeoResourcePeriodicSchedule>()
        targets.forEach { target ->
            val workSpec = target.geoRefreshWorkSpecOrNull() ?: return@forEach
            val existing = schedulesByProfileId[workSpec.id]
            nextSchedules[workSpec.id] = if (existing == null) {
                GeoResourcePeriodicSchedule(
                    profileId = workSpec.id,
                    intervalMillis = workSpec.repeatIntervalMillis,
                    periodAnchorMillis = nowMillis,
                )
            } else {
                existing.copy(intervalMillis = workSpec.repeatIntervalMillis)
            }
        }

        val nextState = GeoResourcePeriodicScheduleState(nextSchedules.toMap())
        return GeoResourcePeriodicScheduleReconciliation(
            state = nextState,
            newlyEligibleProfileIds = nextSchedules.keys - schedulesByProfileId.keys,
            removedProfileIds = schedulesByProfileId.keys - nextSchedules.keys,
        )
    }

    /** Apply the result of a scheduled attempt without changing captured request inputs. */
    fun recordAttemptResult(
        profileId: Int,
        succeeded: Boolean,
        atMillis: Long,
    ): GeoResourcePeriodicScheduleState {
        val schedule = schedulesByProfileId[profileId] ?: return this
        val updated = if (succeeded) {
            schedule.copy(
                periodAnchorMillis = atMillis,
                completedPeriods = incrementSaturated(schedule.completedPeriods),
                runAttemptCount = 0,
                retryAtMillis = null,
            )
        } else {
            val nextAttempt = incrementSaturated(schedule.runAttemptCount)
            val retryDelayMillis = trafficConfigGeoRetryDelayMillis(nextAttempt)
            schedule.copy(
                periodAnchorMillis = atMillis,
                runAttemptCount = nextAttempt,
                retryAtMillis = atMillis + retryDelayMillis,
            )
        }
        return copy(schedulesByProfileId = schedulesByProfileId + (profileId to updated))
    }
}

data class GeoResourcePeriodicScheduleReconciliation(
    val state: GeoResourcePeriodicScheduleState,
    val newlyEligibleProfileIds: Set<Int>,
    val removedProfileIds: Set<Int>,
)

/** The exact active-profile fields that trigger Android's one-time resource refresh. */
data class ActiveGeoResourceRefreshTrigger(
    val profileId: Int,
    val customGeoIpUrl: String,
    val customGeoSiteUrl: String,
)

data class GeoResourceRefreshTriggerState(
    val lastObserved: ActiveGeoResourceRefreshTrigger? = null,
) {
    /** The first observation is a baseline; only a later changed positive ID is queued. */
    fun observe(current: ActiveGeoResourceRefreshTrigger): GeoResourceRefreshTriggerObservation {
        val oneTimeProfileId = current.profileId.takeIf {
            lastObserved != null && lastObserved != current && current.profileId > 0
        }
        return GeoResourceRefreshTriggerObservation(
            state = copy(lastObserved = current),
            oneTimeProfileId = oneTimeProfileId,
        )
    }
}

data class GeoResourceRefreshTriggerObservation(
    val state: GeoResourceRefreshTriggerState,
    val oneTimeProfileId: Int?,
)

/** Mirrors the latest-profile guard used by ResourceAutoUpdateWorker. */
fun shouldRunGeoResourceAutoUpdate(
    requestedProfileId: Int,
    latestTargets: List<TrafficConfigRefreshTarget>,
): Boolean {
    if (requestedProfileId == MissingTrafficConfigId) return false
    return latestTargets.firstOrNull { it.id == requestedProfileId }?.resourceAutoUpdate == true
}

/** Retry delay for Android's one-time resource request, which keeps WorkManager's default 30s base. */
fun trafficConfigGeoOnceRetryDelayMillis(runAttemptCount: Int): Long =
    workManagerExponentialBackoffDelayMillis(
        runAttemptCount = runAttemptCount,
        baseDelayMillis = WorkManagerDefaultBackoffMillis,
    )

private fun incrementSaturated(value: Int): Int = if (value == Int.MAX_VALUE) value else value + 1

private fun incrementSaturated(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1

private const val MissingTrafficConfigId = -1
private const val WorkManagerDefaultBackoffMillis = 30_000L
