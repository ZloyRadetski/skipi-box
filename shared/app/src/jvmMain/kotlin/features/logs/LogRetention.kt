// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

const val LogRetentionDefaultIntervalMillis = 60L * 60L * 1000L

/**
 * Applies the retention period to [repositories] now and then every [intervalMillis], so a
 * long-running session does not keep entries (and file lines) past the period until the next
 * settings save. [retentionDays] is read on every round (0 or less keeps everything). Never
 * returns; run it in a scope the host cancels. One failing repository does not stop the others.
 */
suspend fun runLogRetention(
    repositories: List<CoreLogRepository>,
    retentionDays: () -> Int,
    intervalMillis: Long = LogRetentionDefaultIntervalMillis,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): Nothing {
    while (true) {
        val days = retentionDays()
        if (days > 0) {
            withContext(ioDispatcher) {
                repositories.forEach { repository ->
                    try {
                        repository.pruneOlderThanDays(days)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Repositories report their own file failures; keep the rest going.
                    }
                }
            }
        }
        delay(intervalMillis)
    }
}
