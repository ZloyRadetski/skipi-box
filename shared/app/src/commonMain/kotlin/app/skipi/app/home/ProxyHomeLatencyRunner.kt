// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

typealias ProxyHomeLatencyProbeLimiter<Outcome> = suspend (suspend () -> Outcome) -> Outcome

/** Runs independent latency probes with the same bounded, incremental sequencing on each host. */
class ProxyHomeLatencyRunner<Target, Outcome>(
    private val concurrency: Int,
    private val probe: suspend (Target) -> Outcome,
    private val onProbeFailure: (Throwable) -> Outcome,
    private val onResult: suspend (Target, Outcome) -> Unit,
    private val probeLimiter: ProxyHomeLatencyProbeLimiter<Outcome>? = null,
) {
    suspend fun execute(targets: List<Target>) {
        coroutineScope {
            val semaphore = if (probeLimiter == null) {
                Semaphore(concurrency.coerceIn(MinLatencyProbeConcurrency, MaxLatencyProbeConcurrency))
            } else {
                null
            }
            targets.map { target ->
                async {
                    val result = try {
                        val runProbe: suspend () -> Outcome = { probe(target) }
                        if (probeLimiter == null) {
                            checkNotNull(semaphore).withPermit { runProbe() }
                        } else {
                            probeLimiter.invoke(runProbe)
                        }
                    } catch (error: Throwable) {
                        onProbeFailure(error)
                    }
                    onResult(target, result)
                }
            }.awaitAll()
        }
    }

    private companion object {
        const val MinLatencyProbeConcurrency = 1
        const val MaxLatencyProbeConcurrency = 64
    }
}
