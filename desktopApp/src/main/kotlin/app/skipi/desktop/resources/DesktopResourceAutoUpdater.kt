// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.runtime.ActiveGeoResourceRefreshTrigger
import features.config.runtime.GeoResourcePeriodicScheduleState
import features.config.runtime.GeoResourceRefreshTriggerState
import features.config.runtime.TrafficConfigRefreshTarget
import features.config.runtime.shouldRunGeoResourceAutoUpdate
import features.config.runtime.trafficConfigGeoOnceRetryDelayMillis
import features.config.runtime.trafficConfigGeoRetryDelayMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.net.SocketException

/**
 * App-lifetime Desktop host for Android's periodic and active-profile geo refresh requests.
 * The composition root owns wakeups and supplies current profile targets at each pass.
 */
internal class DesktopResourceAutoUpdater(
    private val readLatestTargets: () -> List<TrafficConfigRefreshTarget>,
    private val isNetworkConnected: () -> Boolean,
    private val updateAutomatically: suspend (profileId: Int) -> Unit,
    private val nowMillis: () -> Long,
) {
    private val lock = Any()
    private var periodicState = GeoResourcePeriodicScheduleState()
    private val periodicGenerations = mutableMapOf<Int, Any>()
    private var activeTriggerState = GeoResourceRefreshTriggerState()
    private val oneTimeRequests = mutableMapOf<Int, OneTimeRequest>()
    private val periodicProfilesInFlight = mutableSetOf<Int>()
    private val periodicJobs = mutableMapOf<Int, Job>()
    private val oneTimeJobs = mutableMapOf<Int, RunningOneTimeRequest>()

    /** Records the active profile projection and replaces any same-profile one-time request. */
    fun observeActiveProfile(trigger: ActiveGeoResourceRefreshTrigger) {
        val now = nowMillis()
        var jobToCancel: Job? = null
        synchronized(lock) {
            val observation = activeTriggerState.observe(trigger)
            activeTriggerState = observation.state
            val profileId = observation.oneTimeProfileId ?: return
            val request = OneTimeRequest(
                profileId = profileId,
                generation = Any(),
                runAttemptCount = 0,
                nextRunAtMillis = now,
            )
            oneTimeRequests[profileId] = request
            jobToCancel = oneTimeJobs[profileId]?.job
        }
        // Android uses ExistingWorkPolicy.REPLACE. Cancel only this profile's one-time job;
        // periodic work has a separate unique name and must continue independently.
        jobToCancel?.cancel()
    }

    /** Reconciles periodic eligibility immediately and cancels only removed periodic work. */
    fun observeTargets(targets: List<TrafficConfigRefreshTarget>) {
        val now = nowMillis()
        val jobsToCancel = synchronized(lock) {
            reconcilePeriodicTargetsLocked(targets, now)
        }
        jobsToCancel.forEach { job -> job.cancel() }
    }

    /** Reconciles current eligibility, then runs due connected work as structured children. */
    suspend fun runDue() = coroutineScope {
        val now = nowMillis()
        val latestTargets = readLatestTargets()
        val connected = isNetworkConnected()
        val duePeriodicIds: List<Int>
        val dueOneTimeRequests: List<OneTimeRequest>
        val periodicJobsToCancel: List<Job>

        synchronized(lock) {
            periodicJobsToCancel = reconcilePeriodicTargetsLocked(latestTargets, now)
            if (!connected) {
                duePeriodicIds = emptyList()
                dueOneTimeRequests = emptyList()
            } else {
                duePeriodicIds = periodicState.schedulesByProfileId.values
                    .asSequence()
                    .filter { it.nextRunAtMillis <= now }
                    .map { it.profileId }
                    .filter(periodicProfilesInFlight::add)
                    .toList()
                dueOneTimeRequests = oneTimeRequests.values
                    .filter { request ->
                        request.nextRunAtMillis <= now && request.profileId !in oneTimeJobs
                }
            }
        }
        periodicJobsToCancel.forEach { job -> job.cancel() }

        val taskScope = this
        val jobs = mutableListOf<Job>()
        duePeriodicIds.forEach { profileId ->
            val generation = synchronized(lock) { periodicGenerations[profileId] }
            if (generation == null) {
                synchronized(lock) { periodicProfilesInFlight.remove(profileId) }
                return@forEach
            }
            val job = taskScope.launch(start = CoroutineStart.LAZY) { runPeriodic(profileId, generation) }
            job.invokeOnCompletion {
                synchronized(lock) {
                    if (periodicJobs[profileId] === job) {
                        periodicJobs.remove(profileId)
                    }
                    periodicProfilesInFlight.remove(profileId)
                }
            }
            val shouldStart = synchronized(lock) {
                val stillEligible = profileId in periodicState.schedulesByProfileId
                val sameGeneration = periodicGenerations[profileId] === generation
                if (
                    stillEligible && sameGeneration && periodicProfilesInFlight.contains(profileId) &&
                    periodicJobs[profileId] == null
                ) {
                    periodicJobs[profileId] = job
                    true
                } else {
                    if (periodicJobs[profileId] == null) {
                        periodicProfilesInFlight.remove(profileId)
                    }
                    false
                }
            }
            if (shouldStart) {
                jobs += job
                job.start()
            } else {
                job.cancel()
            }
        }
        dueOneTimeRequests.forEach { request ->
            startOneTime(taskScope, request)?.let(jobs::add)
        }
        jobs.joinAll()
    }

    /** The earliest periodic or active-profile one-time deadline, if any. */
    fun nextWakeupAtMillis(): Long? = synchronized(lock) {
        sequenceOf(
            periodicState.schedulesByProfileId.values.asSequence().map { it.nextRunAtMillis },
            oneTimeRequests.values.asSequence().map { it.nextRunAtMillis },
        ).flatten().minOrNull()
    }

    /** Must be called while holding [lock]. */
    private fun reconcilePeriodicTargetsLocked(
        targets: List<TrafficConfigRefreshTarget>,
        nowMillis: Long,
    ): List<Job> {
        val reconciliation = periodicState.reconcile(targets, nowMillis)
        periodicState = reconciliation.state
        reconciliation.removedProfileIds.forEach { profileId -> periodicGenerations.remove(profileId) }
        reconciliation.newlyEligibleProfileIds.forEach { profileId ->
            periodicGenerations[profileId] = Any()
        }
        return reconciliation.removedProfileIds.mapNotNull(periodicJobs::get)
    }

    private fun startOneTime(scope: CoroutineScope, request: OneTimeRequest): Job? {
        val job = scope.launch(start = CoroutineStart.LAZY) { runOneTime(request) }
        val shouldStart = synchronized(lock) {
            val isCurrent = oneTimeRequests[request.profileId]?.generation === request.generation
            val alreadyRunning = request.profileId in oneTimeJobs
            if (isCurrent && !alreadyRunning) {
                oneTimeJobs[request.profileId] = RunningOneTimeRequest(job)
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
                if (oneTimeJobs[request.profileId]?.job === job) {
                    oneTimeJobs.remove(request.profileId)
                }
            }
        }
        job.start()
        return job
    }

    private suspend fun runPeriodic(profileId: Int, generation: Any) {
        if (!isNetworkConnected()) return
        if (!shouldRunGeoResourceAutoUpdate(profileId, readLatestTargets())) {
            recordPeriodicAttemptResult(profileId, generation, succeeded = true)
            return
        }

        try {
            updateAutomatically(profileId)
            recordPeriodicAttemptResult(profileId, generation, succeeded = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            recordPeriodicAttemptResult(profileId, generation, succeeded = false)
        }
    }

    private fun recordPeriodicAttemptResult(profileId: Int, generation: Any, succeeded: Boolean) {
        val completedAt = nowMillis()
        synchronized(lock) {
            if (periodicGenerations[profileId] === generation) {
                periodicState = periodicState.recordAttemptResult(
                    profileId = profileId,
                    succeeded = succeeded,
                    atMillis = completedAt,
                )
            }
        }
    }

    private suspend fun runOneTime(request: OneTimeRequest) {
        if (!isNetworkConnected()) return
        if (!shouldRunGeoResourceAutoUpdate(request.profileId, readLatestTargets())) {
            removeOneTimeRequestIfCurrent(request)
            return
        }

        try {
            updateAutomatically(request.profileId)
            removeOneTimeRequestIfCurrent(request)
        } catch (cancelled: CancellationException) {
            if (!isSuperseded(request)) throw cancelled
        } catch (_: Throwable) {
            if (!isSuperseded(request)) {
                val nextAttempt = request.runAttemptCount + 1
                val retryAtMillis = nowMillis() + trafficConfigGeoOnceRetryDelayMillis(nextAttempt)
                synchronized(lock) {
                    if (oneTimeRequests[request.profileId]?.generation === request.generation) {
                        oneTimeRequests[request.profileId] = request.copy(
                            runAttemptCount = nextAttempt,
                            nextRunAtMillis = retryAtMillis,
                        )
                    }
                }
            }
        }
    }

    private fun removeOneTimeRequestIfCurrent(request: OneTimeRequest) {
        synchronized(lock) {
            if (oneTimeRequests[request.profileId]?.generation === request.generation) {
                oneTimeRequests.remove(request.profileId)
            }
        }
    }

    private fun isSuperseded(request: OneTimeRequest): Boolean = synchronized(lock) {
        oneTimeRequests[request.profileId]?.generation !== request.generation
    }

    private data class OneTimeRequest(
        val profileId: Int,
        val generation: Any,
        val runAttemptCount: Int,
        val nextRunAtMillis: Long,
    )

    private data class RunningOneTimeRequest(
        val job: Job,
    )
}

/**
 * Uses operating-system interface state as Desktop's connectivity constraint. It makes no
 * external reachability probe; no non-loopback interface or an OS query failure means offline.
 */
internal fun isDesktopNetworkConnected(): Boolean {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isUp && !networkInterface.isLoopback) return true
        }
        false
    } catch (_: SocketException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
