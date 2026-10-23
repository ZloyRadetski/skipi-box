// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.subscription.SubscriptionWorkerOutcome
import features.subscription.runtime.SubscriptionPeriodicWorkState
import features.subscription.runtime.SubscriptionRefreshTarget
import features.subscription.runtime.subscriptionRefreshWorkSpecOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** In-process Desktop owner for independent periodic subscription refresh jobs. */
internal class DesktopSubscriptionAutoUpdater(
    private val readLatestTargets: () -> List<SubscriptionRefreshTarget>,
    private val isNetworkConnected: () -> Boolean,
    private val updateAutomatically: suspend (Int) -> SubscriptionWorkerOutcome,
    private val nowMillis: () -> Long,
) {
    private val lock = Any()
    private val entriesByGroupId = mutableMapOf<Int, Entry>()

    /** Creates, updates, or removes eligible subscription schedules while retaining current state. */
    fun observeTargets(targets: List<SubscriptionRefreshTarget>) {
        val jobsToCancel = synchronized(lock) {
            reconcileTargetsLocked(targets, nowMillis())
        }
        jobsToCancel.forEach(Job::cancel)
    }

    /** Reconciles latest eligibility and runs due connected groups as structured children. */
    suspend fun runDue() = coroutineScope {
        val now = nowMillis()
        val latestTargets = readLatestTargets()
        val connected = isNetworkConnected()
        val jobsToCancel: List<Job>
        val dueEntries: List<Entry>
        synchronized(lock) {
            jobsToCancel = reconcileTargetsLocked(latestTargets, now)
            dueEntries = if (connected) {
                entriesByGroupId.values.filter { entry ->
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

    /** The earliest deadline among currently eligible subscriptions, if any. */
    fun nextWakeupAtMillis(): Long? = synchronized(lock) {
        entriesByGroupId.values.minOfOrNull { entry -> entry.workState.nextRunAtMillis }
    }

    private fun reconcileTargetsLocked(
        targets: List<SubscriptionRefreshTarget>,
        nowMillis: Long,
    ): List<Job> {
        val desiredSpecs = targets.mapNotNull(SubscriptionRefreshTarget::subscriptionRefreshWorkSpecOrNull)
            .associateBy { spec -> spec.groupId }
        val removedJobs = (entriesByGroupId.keys - desiredSpecs.keys).mapNotNull { groupId ->
            entriesByGroupId.remove(groupId)?.job
        }

        desiredSpecs.values.forEach { spec ->
            val entry = entriesByGroupId[spec.groupId]
            if (entry == null) {
                entriesByGroupId[spec.groupId] = Entry(
                    groupId = spec.groupId,
                    workState = SubscriptionPeriodicWorkState(
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
                entriesByGroupId[entry.groupId] === entry &&
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
                if (entriesByGroupId[entry.groupId] === entry && entry.job === job) {
                    entry.job = null
                }
            }
        }
        return job
    }

    private suspend fun runEntry(entry: Entry) {
        val context = currentCoroutineContext()
        if (!isNetworkConnected()) return

        val outcome = updateAutomatically(entry.groupId)
        if (!context.isActive) return

        val completedAtMillis = nowMillis()
        synchronized(lock) {
            if (context.isActive && entriesByGroupId[entry.groupId] === entry) {
                entry.workState = entry.workState.recordAttemptResult(
                    retryable = outcome == SubscriptionWorkerOutcome.RETRY,
                    atMillis = completedAtMillis,
                )
            }
        }
    }

    private class Entry(
        val groupId: Int,
        var workState: SubscriptionPeriodicWorkState,
        var job: Job? = null,
    )
}
