// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.Job

/** Retains the host latency jobs so the shared Home cancel action cancels real work. */
internal class DesktopProxyHomeLatencyJobOwner {
    private val lock = Any()
    private val jobs = linkedSetOf<Job>()

    val hasActiveJobs: Boolean
        get() = synchronized(lock) { jobs.any(Job::isActive) }

    fun track(job: Job) {
        synchronized(lock) {
            if (job.isActive) jobs += job
        }
        job.invokeOnCompletion {
            synchronized(lock) { jobs -= job }
        }
    }

    fun cancel() {
        val activeJobs = synchronized(lock) { jobs.filter(Job::isActive) }
        activeJobs.forEach(Job::cancel)
    }
}
