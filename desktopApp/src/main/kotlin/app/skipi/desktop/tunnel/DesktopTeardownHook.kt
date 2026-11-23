// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Best-effort teardown for a JVM that is going away without the window closing first (SIGTERM, logout, an
 * installer closing the process): the normal path runs through Compose, which never gets to run then, and
 * would leave the system proxy pointing at a dead port and a TUN device with its routes behind.
 * Every step is idempotent, so the normal shutdown running first (or the hook running twice) does no harm.
 */
internal class DesktopTeardownHook(
    private val hasWorkToDo: () -> Boolean,
    private val releaseSystemProxy: () -> Unit,
    private val stopTun: () -> Unit,
    private val stopCore: () -> Unit,
    private val timeoutMillis: Long = 8_000,
) {
    private val ran = AtomicBoolean(false)

    fun run() {
        if (!hasWorkToDo() || !ran.compareAndSet(false, true)) return
        // The steps make native calls that can hang; the process is exiting, so wait a bounded time only.
        val worker = Thread({
            // The proxy goes first: it is what breaks the user's internet while the process is still winding down.
            runCatching(releaseSystemProxy)
            runCatching(stopTun)
            runCatching(stopCore)
        }, "skipi-teardown").apply { isDaemon = true }
        worker.start()
        worker.join(timeoutMillis)
    }

    fun install() {
        Runtime.getRuntime().addShutdownHook(Thread(::run, "skipi-teardown-hook"))
    }
}
