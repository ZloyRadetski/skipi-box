// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opens once the Room-backed lists have been merged into the state store.
 * Persistence and every background entry point that needs servers or groups waits
 * on it; saving before it opens would replace the database with empty lists.
 */
internal class InitialLoadGate {
    private val completion = CompletableDeferred<Unit>()
    private val mutableReady = MutableStateFlow(false)

    val isReady: StateFlow<Boolean> = mutableReady.asStateFlow()

    fun markReady() {
        mutableReady.value = true
        completion.complete(Unit)
    }

    suspend fun awaitReady() {
        completion.await()
    }

    /** Blocks the calling thread; never call it from the main thread. Returns false on timeout. */
    fun awaitReadyBlocking(timeoutMillis: Long = DefaultBlockingTimeoutMillis): Boolean {
        if (completion.isCompleted) return true
        return runBlocking { withTimeoutOrNull(timeoutMillis) { completion.await() } != null }
    }

    companion object {
        const val DefaultBlockingTimeoutMillis = 15_000L
    }
}
