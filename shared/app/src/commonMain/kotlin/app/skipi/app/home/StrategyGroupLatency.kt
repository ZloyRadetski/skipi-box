// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Extra time a member is given beyond the best answer: probes report slightly different clocks (DNS, queueing). */
private const val GraceMillis = 200L

/**
 * A strategy group's latency, as Android measures it: every member is probed, at most [concurrency] at
 * a time, and the best answer wins. Null when no member answered; a member whose probe throws counts
 * as not answering.
 *
 * A member that is still silent once the best answer so far has been outrun (that answer plus [GraceMillis], or
 * twice it) cannot improve the group and is given up on, so a dead member no longer holds the whole group until
 * its own timeout. It frees its place at once; its probe is cancelled, and a probe that cannot be interrupted
 * (a blocking socket) is simply no longer waited for.
 */
suspend fun <M> strategyGroupLatency(
    members: List<M>,
    concurrency: Int,
    timeSource: TimeSource = TimeSource.Monotonic,
    probe: suspend (M) -> Long?,
): Long? {
    if (members.isEmpty()) return null
    // Probes live in their own scope so giving up on one never waits for it; the scope ends with this call.
    val probes = CoroutineScope(currentCoroutineContext().minusKey(kotlinx.coroutines.Job) + SupervisorJob())
    try {
        val permits = Semaphore(concurrency.coerceAtLeast(1))
        val best = MutableStateFlow<Long?>(null)
        return members.map { member ->
            probes.async {
                permits.withPermit { probeUnlessOutrun(probes, member, best, timeSource, probe) }
            }
        }.awaitAll().filterNotNull().filter { it >= 0L }.minOrNull()
    } finally {
        probes.cancel()
    }
}

private suspend fun <M> probeUnlessOutrun(
    scope: CoroutineScope,
    member: M,
    best: MutableStateFlow<Long?>,
    timeSource: TimeSource,
    probe: suspend (M) -> Long?,
): Long? {
    val started = timeSource.markNow()
    val outcome = CompletableDeferred<Long?>()
    val running = scope.launch {
        outcome.complete(
            try {
                probe(member)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            },
        )
    }
    val watcher = scope.launch {
        // A better answer moves the deadline closer, so the wait restarts with each one.
        best.filterNotNull().collectLatest { limit ->
            val deadline = limit + maxOf(limit, GraceMillis)
            delay((deadline - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L))
            outcome.complete(null)
            running.cancel()
        }
    }
    val answer = try {
        outcome.await()
    } finally {
        watcher.cancel()
    }
    if (answer != null && answer >= 0L) best.update { current -> if (current == null || answer < current) answer else current }
    return answer
}
