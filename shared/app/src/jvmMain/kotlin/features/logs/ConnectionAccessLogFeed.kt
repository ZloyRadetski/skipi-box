// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import app.skipi.app.home.ConnectionLogReader
import app.skipi.app.home.accessLogText

/**
 * Copies what Core recorded about routed connections (the host that was sniffed, where it went)
 * into [repository] as plain text lines. Xray's access.log only holds the address the client
 * asked for, which under a TUN is a bare IP.
 */
class ConnectionAccessLogFeed(
    private val repository: CoreLogRepository,
    read: (afterSeq: Long) -> Result<String>,
    private val formatTime: (Long) -> String,
) {
    private val reader = ConnectionLogReader(read)

    fun poll() {
        val entries = reader.next()
        if (entries.isEmpty()) return
        repository.appendBatch(
            entries.map { entry ->
                ParsedCoreLogLine(time = null, level = "info", message = entry.accessLogText(formatTime))
            },
        )
    }

    fun reset() = reader.reset()
}
