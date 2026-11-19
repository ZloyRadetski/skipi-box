// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.delay

/** The first check waits until the start has settled (window drawn, warm-up garbage dead), before the working-set trim. */
internal const val DesktopHeapTrimFirstDelayMillis = 12_000L
internal const val DesktopHeapTrimIntervalMillis = 60_000L
internal const val DesktopHeapTrimExcessBytes = 32L * 1_024L * 1_024L

/**
 * True when the committed heap is well above what was live right after the last trim. The serial collector's old
 * generation also holds promoted garbage that the runtime reports as used, so "committed minus used" stayed small after
 * a start burst (190 MB committed for ~40 MB of live data) and the heap was never given back. A large live set gets half
 * of itself as room, so it is not collected again every minute.
 */
internal fun shouldTrimHeap(committedBytes: Long, liveAfterLastTrimBytes: Long, excessBytes: Long = DesktopHeapTrimExcessBytes): Boolean =
    committedBytes - liveAfterLastTrimBytes >= maxOf(excessBytes, liveAfterLastTrimBytes / 2)

/**
 * The serial collector keeps what a burst made it commit until a full collection runs (G1's periodic collection did
 * this on its own, at ~65 MB of permanent bookkeeping). Soon after the start, and then once a minute, the space is given
 * back when there is a lot of it; what is used right after that collection is the live data the next check compares with.
 */
internal suspend fun runDesktopHeapTrim(
    readCommitted: () -> Long = { Runtime.getRuntime().totalMemory() },
    readUsed: () -> Long = { Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() } },
    trim: () -> Unit = System::gc,
    firstDelayMillis: Long = DesktopHeapTrimFirstDelayMillis,
    intervalMillis: Long = DesktopHeapTrimIntervalMillis,
    excessBytes: Long = DesktopHeapTrimExcessBytes,
) {
    var liveAfterLastTrim = 0L
    delay(firstDelayMillis)
    while (true) {
        if (shouldTrimHeap(readCommitted(), liveAfterLastTrim, excessBytes)) {
            trim()
            liveAfterLastTrim = readUsed()
        }
        delay(intervalMillis)
    }
}
