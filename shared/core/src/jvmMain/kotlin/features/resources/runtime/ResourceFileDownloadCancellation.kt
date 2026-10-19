// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.resources.ResourceFileDownloadCancelledException
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Global cancellation state for the one active resource-file download. */
object ResourceFileDownloadCancellation {
    private val cancelled = AtomicBoolean(false)
    private val connection = AtomicReference<HttpURLConnection?>(null)

    fun begin() {
        cancelled.set(false)
        connection.set(null)
    }

    fun cancel() {
        cancelled.set(true)
        connection.get()?.disconnect()
    }

    fun track(connection: HttpURLConnection) {
        this.connection.set(connection)
        throwIfCancelled()
    }

    fun untrack(connection: HttpURLConnection) {
        this.connection.compareAndSet(connection, null)
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
