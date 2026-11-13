// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hands log lines to one consumer coroutine so the logging thread never waits for file I/O.
 * Order is kept. When more than [maxPendingDebug] lines are waiting, further debug lines are
 * dropped; other levels are always queued.
 */
internal class BackgroundLogWriter(
    scope: CoroutineScope,
    private val maxPendingDebug: Int,
    private val clock: () -> String = { "" },
    private val write: (level: String, message: String, time: String) -> Unit,
) {
    private class Line(val level: String, val message: String, val time: String)

    private val channel = Channel<Line>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)

    init {
        scope.launch {
            for (line in channel) {
                try {
                    write(line.level, line.message, line.time)
                } catch (_: Throwable) {
                    // A failing log destination must not stop the consumer.
                } finally {
                    pending.decrementAndGet()
                }
            }
        }
    }

    fun post(level: String, message: String) {
        if (level == "debug" && pending.get() >= maxPendingDebug) return
        pending.incrementAndGet()
        if (channel.trySend(Line(level, message, clock())).isFailure) {
            pending.decrementAndGet()
        }
    }
}
