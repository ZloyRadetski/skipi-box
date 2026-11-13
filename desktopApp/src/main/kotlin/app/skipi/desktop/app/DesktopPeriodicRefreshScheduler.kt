// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.work.PeriodicWorkSpec
import features.work.PeriodicWorkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * In-process Desktop owner of independent periodic refresh jobs, one per item (a profile, a subscription).
 * It keeps each item's [PeriodicWorkState], starts the items that are due while the network is connected, and
 * drops a schedule when its item stops being eligible. What is refreshed, and what a result means, is decided by
 * the caller through [specOf] and [refresh].
 *
 * @param specOf the item's periodic request, or null when it is not eligible for automatic refresh
 * @param idOf the id the schedule is kept under
 * @param cancelRunningWhenDisconnected whether a refresh already running is cancelled once the network is gone
 * @param refresh runs one refresh: true asks for a retry with backoff, false completes the period, null records nothing
 */
internal class DesktopPeriodicRefreshScheduler<Target, Spec : PeriodicWorkSpec>(
    private val specOf: (Target) -> Spec?,
    private val idOf: (Spec) -> Int,
    private val readLatestTargets: () -> List<Target>,
    private val isNetworkConnected: () -> Boolean,
    private val cancelRunningWhenDisconnected: Boolean,
    private val refresh: suspend (Int) -> Boolean?,
    private val nowMillis: () -> Long,
) {
    private val lock = Any()
    private val entriesById = mutableMapOf<Int, Entry<Spec>>()

    /** Creates, updates, or removes schedules while retaining the state of those that stay. */
    fun observeTargets(targets: List<Target>) {
        val jobsToCancel = synchronized(lock) {
            reconcileTargetsLocked(targets, nowMillis())
        }
        jobsToCancel.forEach(Job::cancel)
    }

    /** Reconciles the latest eligibility and runs the connected, due items as structured children. */
    suspend fun runDue() = coroutineScope {
        val now = nowMillis()
        val latestTargets = readLatestTargets()
        val connected = isNetworkConnected()
        val jobsToCancel: List<Job>
        val dueEntries: List<Entry<Spec>>
        synchronized(lock) {
            val removedJobs = reconcileTargetsLocked(latestTargets, now)
            val disconnectedJobs = if (connected || !cancelRunningWhenDisconnected) {
                emptyList()
            } else {
                entriesById.values.mapNotNull { entry -> entry.job }
            }
            jobsToCancel = (removedJobs + disconnectedJobs).distinct()
            dueEntries = if (connected) {
                entriesById.values.filter { entry -> entry.job == null && entry.workState.nextRunAtMillis <= now }
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

    /** The earliest deadline among the items that remain eligible, if any. */
    fun nextWakeupAtMillis(): Long? = synchronized(lock) {
        entriesById.values.minOfOrNull { entry -> entry.workState.nextRunAtMillis }
    }

    /**
     * When the loop should look again: [nextWakeupAtMillis], but an item that is running (it cannot start twice) or
     * any item while offline (nothing can start) is not looked at sooner than [OfflineRecheckMillis] from now.
     * An overdue item otherwise makes the loop wake every second for as long as it runs or the network is gone.
     */
    fun nextCheckAtMillis(): Long? {
        val connected = isNetworkConnected()
        val floor = nowMillis() + OfflineRecheckMillis
        return synchronized(lock) {
            entriesById.values.minOfOrNull { entry ->
                val next = entry.workState.nextRunAtMillis
                if (!connected || entry.job != null) maxOf(next, floor) else next
            }
        }
    }

    private fun reconcileTargetsLocked(targets: List<Target>, nowMillis: Long): List<Job> {
        val desiredSpecs = targets.mapNotNull(specOf).associateBy(idOf)
        val removedJobs = (entriesById.keys - desiredSpecs.keys).mapNotNull { id -> entriesById.remove(id)?.job }

        desiredSpecs.forEach { (id, spec) ->
            val entry = entriesById[id]
            if (entry == null) {
                entriesById[id] = Entry(id, PeriodicWorkState(spec = spec, periodAnchorMillis = nowMillis))
            } else {
                entry.workState = entry.workState.withUpdatedSpec(spec)
            }
        }
        return removedJobs
    }

    private fun launchEntry(scope: CoroutineScope, entry: Entry<Spec>): Job? {
        val job = scope.launch(start = CoroutineStart.LAZY) { runEntry(entry) }
        val shouldStart = synchronized(lock) {
            if (
                entriesById[entry.id] === entry &&
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
                if (entriesById[entry.id] === entry && entry.job === job) {
                    entry.job = null
                }
            }
        }
        return job
    }

    private suspend fun runEntry(entry: Entry<Spec>) {
        val context = currentCoroutineContext()
        if (!isNetworkConnected()) return

        val retry = refresh(entry.id)
        if (!context.isActive) return
        if (retry == null) return

        val completedAtMillis = nowMillis()
        synchronized(lock) {
            if (context.isActive && entriesById[entry.id] === entry) {
                entry.workState = entry.workState.recordAttempt(retry = retry, atMillis = completedAtMillis)
            }
        }
    }

    private class Entry<Spec : PeriodicWorkSpec>(
        val id: Int,
        var workState: PeriodicWorkState<Spec>,
        var job: Job? = null,
    )
}

internal const val OfflineRecheckMillis = 60_000L
