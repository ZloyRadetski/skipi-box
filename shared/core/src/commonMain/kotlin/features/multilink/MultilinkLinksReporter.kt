// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

/**
 * Hands the host's links to Core, but only when they changed, and never after [close]: networks report every
 * capability tweak, and each push is a JNI call that restarts Core's link probing.
 */
class MultilinkLinksReporter(private val push: (List<MultilinkHostLink>) -> Result<Unit>) {
    private val lock = Any()
    private var last: List<MultilinkHostLink>? = null
    private var closed = false

    fun report(links: List<MultilinkHostLink>) {
        synchronized(lock) {
            if (closed || links == last) return
            // A refused push is retried with the next report, even an identical one.
            last = if (push(links).isSuccess) links else null
        }
    }

    fun close() {
        synchronized(lock) { closed = true }
    }
}
