// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Remembers when the current connection began. A screen cannot keep this itself: Home is rebuilt
 * every time the user returns to it, and a timer started there would count from zero again while
 * the connection had never stopped. The host keeps one clock for the whole process and feeds it the
 * connection state wherever that state is first known.
 */
class ConnectionClock(private val source: TimeSource = TimeSource.Monotonic) {
    private val lock = Any()
    private var since: TimeMark? = null

    /** When the connection began, or null while disconnected. */
    val connectedSince: TimeMark? get() = synchronized(lock) { since }

    /** Idempotent: repeating the same state changes nothing, so any number of callers may report it. */
    fun update(connected: Boolean): TimeMark? = synchronized(lock) {
        if (!connected) {
            since = null
        } else if (since == null) {
            since = source.markNow()
        }
        since
    }
}
