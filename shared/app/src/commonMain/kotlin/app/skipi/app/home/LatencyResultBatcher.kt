// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collapses a burst of probe results into one state update per [intervalMillis]. A mass latency
 * test otherwise rewrites the whole server list (and re-projects Home) once per finished probe.
 * Call [flush] when the run ends so nothing stays pending.
 */
class LatencyResultBatcher<K, V>(
    private val scope: CoroutineScope,
    private val intervalMillis: Long,
    private val apply: suspend (Map<K, V>) -> Unit,
) {
    private val mutex = Mutex()
    private val pending = LinkedHashMap<K, V>()
    private var scheduled = false

    suspend fun add(key: K, value: V) {
        val schedule = mutex.withLock {
            pending[key] = value
            (!scheduled).also { scheduled = true }
        }
        if (schedule) {
            scope.launch {
                delay(intervalMillis)
                flush()
            }
        }
    }

    suspend fun flush() {
        val batch = mutex.withLock {
            scheduled = false
            if (pending.isEmpty()) null else LinkedHashMap(pending).also { pending.clear() }
        }
        batch?.let { apply(it) }
    }
}
