// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tail files slowly in the background and only use responsive polling while a
 * user is actively reading logs. File tailers are intentionally kept simple
 * because Xray may recreate its output files during a tunnel restart.
 */
internal object CoreLogTailerPollingPolicy {
    private val activeViewers = AtomicInteger(0)

    fun acquireViewer(): Closeable {
        activeViewers.incrementAndGet()
        return ViewerLease()
    }

    fun intervalMillis(): Long = coreLogTailIntervalMillis(activeViewers.get() > 0)

    private class ViewerLease : Closeable {
        private val released = AtomicBoolean(false)

        override fun close() {
            if (released.compareAndSet(false, true)) {
                activeViewers.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
            }
        }
    }
}

internal fun coreLogTailIntervalMillis(hasVisibleViewer: Boolean): Long {
    return if (hasVisibleViewer) CoreLogForegroundTailIntervalMillis else CoreLogBackgroundTailIntervalMillis
}

internal const val CoreLogForegroundTailIntervalMillis = 500L
internal const val CoreLogBackgroundTailIntervalMillis = 5_000L
