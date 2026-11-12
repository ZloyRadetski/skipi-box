// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** How many resource files are downloaded at the same time. */
const val MaxParallelResourceFileDownloads = 4

/**
 * Runs `task(0 until count)` on up to [maxParallel] threads and returns when every task is over. A task is expected to
 * handle its own failures: the batch executor records them per file, so one bad file never hides the others.
 */
fun parallelResourceFileTaskRunner(maxParallel: Int = MaxParallelResourceFileDownloads): ResourceFileTaskRunner =
    { count, task ->
        if (count == 1) {
            task(0)
        } else if (count > 1) {
            val pool = Executors.newFixedThreadPool(minOf(count, maxParallel.coerceAtLeast(1))) { runnable ->
                Thread(runnable, "resource-file-download").apply { isDaemon = true }
            }
            try {
                pool.invokeAll((0 until count).map { index -> Callable { task(index) } }).forEach { it.get() }
            } finally {
                pool.shutdownNow()
            }
        }
    }
