// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * A strategy group's latency, as Android measures it: every member is probed, at most [concurrency] at
 * a time, and the best answer wins. Null when no member answered; a member whose probe throws counts
 * as not answering.
 */
suspend fun <M> strategyGroupLatency(
    members: List<M>,
    concurrency: Int,
    probe: suspend (M) -> Long?,
): Long? = coroutineScope {
    val permits = Semaphore(concurrency.coerceAtLeast(1))
    members.map { member ->
        async {
            permits.withPermit {
                try {
                    probe(member)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }.awaitAll().filterNotNull().filter { it >= 0L }.minOrNull()
}
