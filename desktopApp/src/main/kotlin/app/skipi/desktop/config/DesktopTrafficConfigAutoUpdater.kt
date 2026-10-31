// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.runtime.TrafficConfigPeriodicWorkState
import features.config.runtime.TrafficConfigRefreshTarget
import features.config.runtime.profileRefreshWorkSpecOrNull
import app.skipi.app.config.TrafficConfigAutoUpdateExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** In-process Desktop owner for independent periodic profile refresh jobs. */
internal class DesktopTrafficConfigAutoUpdater(
    private val readLatestTargets: () -> List<TrafficConfigRefreshTarget>,
    private val isNetworkConnected: () -> Boolean,
    private val updateAutomatically: suspend (Int) -> TrafficConfigAutoUpdateExecutionResult,
    private val nowMillis: () -> Long,
) {
    private val lock = Any()
    private val entriesByProfileId = mutableMapOf<Int, Entry>()

    /** Creates, updates, or removes profile schedules while retaining existing work state. */
    fun observeTargets(targets: List<TrafficConfigRefreshTarget>) {
        val jobsToCancel = synchronized(lock) {
            reconcileTargetsLocked(targets, nowMillis())
        }
        jobsToCancel.forEach(Job::cancel)
    }

    /** Reconciles current eligibility and runs due connected profiles as structured children. */
    suspend fun runDue() = coroutineScope {
        val now = nowMillis()
        val latestTargets = readLatestTargets()
        val connected = isNetworkConnected()
        val jobsToCancel: List<Job>
        val dueEntries: List<Entry>
        synchronized(lock) {
            jobsToCancel = reconcileTargetsLocked(latestTargets, now)
            dueEntries = if (connected) {
                entriesByProfileId.values.filter { entry ->
                    entry.job == null && entry.workState.nextRunAtMillis <= now
                }
            } else {
                emptyList()
            }
        }
        jobsToCancel.forEach(Job::cancel)

        val jobs = mutableListOf<Job>()
        dueEntries.forEach { entry ->
            val job = launchEntry(this, entry)
            if (job != null) {
                jobs += job
                job.start()
            }
        }
        jobs.joinAll()
    }

    /** The earliest profile deadline, if any profile remains eligible. */
    fun nextWakeupAtMillis(): Long? = synchronized(lock) {
        entriesByProfileId.values.minOfOrNull { entry -> entry.workState.nextRunAtMillis }
    }

    private fun reconcileTargetsLocked(
        targets: List<TrafficConfigRefreshTarget>,
        nowMillis: Long,
    ): List<Job> {
        val desiredSpecs = targets.mapNotNull(TrafficConfigRefreshTarget::profileRefreshWorkSpecOrNull)
            .associateBy { spec -> spec.id }
        val removedJobs = (entriesByProfileId.keys - desiredSpecs.keys).mapNotNull { profileId ->
            entriesByProfileId.remove(profileId)?.job
        }

        desiredSpecs.values.forEach { spec ->
            val entry = entriesByProfileId[spec.id]
            if (entry == null) {
                entriesByProfileId[spec.id] = Entry(
                    profileId = spec.id,
                    workState = TrafficConfigPeriodicWorkState(
                        spec = spec,
                        periodAnchorMillis = nowMillis,
                    ),
                )
            } else {
                entry.workState = entry.workState.withUpdatedSpec(spec)
            }
        }
        return removedJobs
    }

    private fun launchEntry(scope: CoroutineScope, entry: Entry): Job? {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            runEntry(entry)
        }
        val shouldStart = synchronized(lock) {
            if (
                entriesByProfileId[entry.profileId] === entry &&
                entry.job == null &&
                entry.workState.nextRunAtMillis <= nowMillis() &&
                !job.isCancelled
            ) {
                entry.job = job
                true
            } else {
                false
            }
        }
        if (!shouldStart) {
            job.cancel()
            return null
        }
        job.invokeOnCompletion {
            synchronized(lock) {
                if (entriesByProfileId[entry.profileId] === entry && entry.job === job) {
                    entry.job = null
                }
            }
        }
        return job
    }

    private suspend fun runEntry(entry: Entry) {
        val context = currentCoroutineContext()
        if (!isNetworkConnected()) return

        val outcome = updateAutomatically(entry.profileId)
        if (!context.isActive) return

        val completedAt = nowMillis()
        synchronized(lock) {
            if (context.isActive && entriesByProfileId[entry.profileId] === entry) {
                entry.workState = entry.workState.recordAttemptResult(
                    succeeded = outcome == TrafficConfigAutoUpdateExecutionResult.Success,
                    atMillis = completedAt,
                )
            }
        }
    }

    private class Entry(
        val profileId: Int,
        var workState: TrafficConfigPeriodicWorkState,
        var job: Job? = null,
    )
}
