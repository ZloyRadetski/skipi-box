// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import features.logs.AndroidAppLogger
import features.tools.dnsleak.DnsLeakTestSession
import features.tools.speedtest.SpeedTestSession
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks active latency test jobs and diagnostic network tests across the app.
 * Allows cancelling them immediately when a tunnel starts, avoiding deadlocks,
 * socket contention, or UI freezes.
 */
internal object ProxyServerLatencyTracker {
    private const val LogTag = "LatencyTracker"
    private val activeJobs = ConcurrentHashMap<Job, Unit>()

    fun register(job: Job) {
        activeJobs[job] = Unit
        job.invokeOnCompletion {
            activeJobs.remove(job)
        }
    }

    fun unregister(job: Job) {
        activeJobs.remove(job)
    }

    fun cancelAll() {
        val jobs = activeJobs.keys.toList()
        if (jobs.isNotEmpty()) {
            AndroidAppLogger.info(LogTag, "Cancelling ${jobs.size} active latency test job(s)")
            for (job in jobs) {
                runCatching { job.cancel() }
            }
            activeJobs.clear()
        }

        // Also stop speedtest and DNS leak tests if running
        runCatching {
            SpeedTestSession.stop()
        }
        runCatching {
            if (DnsLeakTestSession.running) {
                DnsLeakTestSession.stop()
            }
        }
    }
}
