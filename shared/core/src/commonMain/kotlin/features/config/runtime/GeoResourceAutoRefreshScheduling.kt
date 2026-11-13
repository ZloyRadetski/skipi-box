// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

data class GeoResourcePeriodicScheduleState(
    val schedulesByProfileId: Map<Int, TrafficConfigPeriodicWorkState> = emptyMap(),
) {
    /**
     * Reconcile only resource-auto-update eligibility. A new schedule waits out one interval from the last known
     * fetch of the files, and is due immediately when nothing is known about them (the schedule itself lives only
     * in memory, so without this every start fetched every profile's files again). Existing schedules retain
     * their latest enqueue anchor and retry state when their interval is updated.
     */
    fun reconcile(
        targets: List<TrafficConfigRefreshTarget>,
        nowMillis: Long,
    ): GeoResourcePeriodicScheduleReconciliation {
        val nextSchedules = mutableMapOf<Int, TrafficConfigPeriodicWorkState>()
        targets.forEach { target ->
            val workSpec = target.geoRefreshWorkSpecOrNull() ?: return@forEach
            val existing = schedulesByProfileId[workSpec.id]
            nextSchedules[workSpec.id] = if (existing == null) {
                newSchedule(workSpec, lastFetchAtMillis = target.resourceLastUpdatedAtMillis, nowMillis = nowMillis)
            } else {
                existing.withUpdatedSpec(workSpec)
            }
        }

        val nextState = GeoResourcePeriodicScheduleState(nextSchedules.toMap())
        return GeoResourcePeriodicScheduleReconciliation(
            state = nextState,
            newlyEligibleProfileIds = nextSchedules.keys - schedulesByProfileId.keys,
            removedProfileIds = schedulesByProfileId.keys - nextSchedules.keys,
        )
    }

    private fun newSchedule(
        spec: TrafficConfigWorkSpec,
        lastFetchAtMillis: Long,
        nowMillis: Long,
    ): TrafficConfigPeriodicWorkState = if (lastFetchAtMillis in 1..nowMillis) {
        // One completed period anchored at the fetch: the next run is one interval after it.
        TrafficConfigPeriodicWorkState(spec = spec, periodAnchorMillis = lastFetchAtMillis, completedPeriods = 1L)
    } else {
        TrafficConfigPeriodicWorkState(spec = spec, periodAnchorMillis = nowMillis)
    }

    /** Apply the result of a scheduled attempt without changing captured request inputs. */
    fun recordAttemptResult(
        profileId: Int,
        succeeded: Boolean,
        atMillis: Long,
    ): GeoResourcePeriodicScheduleState {
        val schedule = schedulesByProfileId[profileId] ?: return this
        val updated = schedule.recordAttemptResult(succeeded = succeeded, atMillis = atMillis)
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

private const val MissingTrafficConfigId = -1
private const val WorkManagerDefaultBackoffMillis = 30_000L
