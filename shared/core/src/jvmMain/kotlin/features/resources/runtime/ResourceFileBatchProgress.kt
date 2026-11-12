// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

/** What a progress notification shows: the file that leads the batch and the share of the whole batch done. */
data class ResourceFileBatchProgressSnapshot(
    val fileName: String,
    /** Whole-batch percent, or null while no file has reported a length yet. */
    val percent: Int?,
)

/**
 * Overall progress of files that download side by side. Each file keeps its own fraction; the batch shows their mean and
 * names the first file that is not done yet, so the text does not flicker between whichever file reported last.
 */
class ResourceFileBatchProgress(private val names: List<String>) {
    private val fractions = DoubleArray(names.size)
    private val known = BooleanArray(names.size)
    private val done = BooleanArray(names.size)

    @Synchronized
    fun report(index: Int, downloadedBytes: Long, totalBytes: Long): ResourceFileBatchProgressSnapshot {
        if (index in names.indices && !done[index] && totalBytes > 0L) {
            fractions[index] = (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
            known[index] = true
        }
        return snapshotLocked()
    }

    @Synchronized
    fun finish(index: Int) {
        if (index !in names.indices) return
        done[index] = true
        fractions[index] = 1.0
        known[index] = true
    }

    @Synchronized
    fun snapshot(): ResourceFileBatchProgressSnapshot = snapshotLocked()

    private fun snapshotLocked(): ResourceFileBatchProgressSnapshot {
        if (names.isEmpty()) return ResourceFileBatchProgressSnapshot("", null)
        val leading = done.indexOfFirst { !it }.takeIf { it >= 0 } ?: names.lastIndex
        val percent = if (known.any { it }) {
            ((fractions.sum() / names.size) * 100).toInt().coerceIn(0, 100)
        } else {
            null
        }
        return ResourceFileBatchProgressSnapshot(names[leading], percent)
    }
}
