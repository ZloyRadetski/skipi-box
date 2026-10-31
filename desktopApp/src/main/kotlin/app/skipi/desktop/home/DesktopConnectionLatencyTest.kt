// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Measures the delay of the live connection and shows it as the selected server's delay. It is a
 * single run on its own, so the bulk latency tests and their cancel action stay out of its way.
 */
internal class DesktopConnectionLatencyTest(
    private val scope: CoroutineScope,
    private val measure: suspend () -> Long?,
    private val readTestingServerIds: () -> Set<Int>,
    private val publishTestingServerIds: (Set<Int>) -> Unit,
    private val readLatencyByServerId: () -> Map<Int, DesktopServerLatencyResult>,
    private val publishLatencyByServerId: (Map<Int, DesktopServerLatencyResult>) -> Unit,
) {
    private var job: Job? = null
    private var serverId: Int? = null

    fun start(serverId: Int): Job {
        cancel()
        this.serverId = serverId
        publishTestingServerIds(readTestingServerIds() + serverId)
        val run = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val milliseconds = measure()
                if (milliseconds == null) DesktopLogger.warn("Ping", "Connection latency probe failed for server $serverId")
                val result = milliseconds?.let(DesktopServerLatencyResult::Success) ?: DesktopServerLatencyResult.Timeout
                publishLatencyByServerId(readLatencyByServerId() + (serverId to result))
            } finally {
                if (this@DesktopConnectionLatencyTest.job === coroutineContext[Job]) {
                    publishTestingServerIds(readTestingServerIds() - serverId)
                    this@DesktopConnectionLatencyTest.job = null
                    this@DesktopConnectionLatencyTest.serverId = null
                }
            }
        }
        job = run
        run.start()
        return run
    }

    fun cancel() {
        val running = job ?: return
        val id = serverId
        job = null
        serverId = null
        running.cancel()
        if (id != null) publishTestingServerIds(readTestingServerIds() - id)
    }
}
