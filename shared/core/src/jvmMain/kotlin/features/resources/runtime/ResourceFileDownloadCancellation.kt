// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.resources.ResourceFileDownloadCancelledException
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

/** Global cancellation state for the active resource-file downloads, which may run side by side. */
object ResourceFileDownloadCancellation {
    private val cancelled = AtomicBoolean(false)
    private val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()

    /**
     * Starts a batch. Connections of a batch that is still running are kept so that [cancel] reaches them;
     * a finished download removes its own connection through [untrack].
     */
    fun begin() {
        cancelled.set(false)
    }

    fun cancel() {
        cancelled.set(true)
        // Take the connections out first: they are disconnected once, and a later begin() has nothing stale.
        val tracked = connections.toList()
        connections.removeAll(tracked.toSet())
        tracked.forEach { it.disconnect() }
    }

    fun track(connection: HttpURLConnection) {
        connections.add(connection)
        throwIfCancelled()
    }

    fun untrack(connection: HttpURLConnection) {
        connections.remove(connection)
    }

    fun isCancelled(): Boolean {
        return cancelled.get()
    }

    fun throwIfCancelled() {
        if (cancelled.get()) {
            throw ResourceFileDownloadCancelledException()
        }
    }
}
