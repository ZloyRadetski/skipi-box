// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.delay

internal data class HeapUsage(val committedBytes: Long, val usedBytes: Long) {
    val freeCommittedBytes: Long get() = committedBytes - usedBytes

    companion object {
        fun current(): HeapUsage = Runtime.getRuntime().let { runtime ->
            HeapUsage(committedBytes = runtime.totalMemory(), usedBytes = runtime.totalMemory() - runtime.freeMemory())
        }
    }
}

internal const val DesktopHeapTrimIntervalMillis = 60_000L
internal const val DesktopHeapTrimExcessBytes = 64L * 1_024L * 1_024L

/** True when the heap holds at least [excessBytes] of committed but unused space, e.g. after a burst. */
internal fun shouldTrimHeap(usage: HeapUsage, excessBytes: Long = DesktopHeapTrimExcessBytes): Boolean =
    usage.freeCommittedBytes >= excessBytes

/**
 * The serial collector keeps what a burst made it commit until a full collection runs (G1's periodic collection did
 * this on its own, at ~65 MB of permanent bookkeeping). Once a minute, give the space back if there is a lot of it.
 */
internal suspend fun runDesktopHeapTrim(
    readUsage: () -> HeapUsage = HeapUsage::current,
    trim: () -> Unit = System::gc,
    intervalMillis: Long = DesktopHeapTrimIntervalMillis,
    excessBytes: Long = DesktopHeapTrimExcessBytes,
) {
    while (true) {
        delay(intervalMillis)
        if (shouldTrimHeap(readUsage(), excessBytes)) trim()
    }
}
