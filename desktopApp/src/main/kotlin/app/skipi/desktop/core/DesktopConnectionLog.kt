// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ConnectionLogState
import app.skipi.app.home.parseConnectionLogSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Host of the Home connection log: reads what Core recorded since the last poll. Nothing reaches the disk. */
internal class DesktopConnectionLog(
    private val read: (Long) -> Result<String>,
) {
    private val mutableState = MutableStateFlow(ConnectionLogState())
    private var cursor = 0L

    val state: StateFlow<ConnectionLogState> = mutableState.asStateFlow()

    @Synchronized
    fun poll() {
        val snapshot = read(cursor).mapCatching(::parseConnectionLogSnapshot).getOrNull() ?: return
        // Every Core session counts from zero, so a smaller cursor means a new session.
        if (snapshot.seq < cursor) {
            cursor = 0
            return
        }
        cursor = snapshot.seq
        mutableState.update { state -> state.append(snapshot.entries) }
    }

    @Synchronized
    fun clear() {
        cursor = 0
        mutableState.update(ConnectionLogState::clear)
    }

    fun setHeld(held: Boolean) {
        mutableState.update { state ->
            when {
                held == state.held -> state
                held -> state.hold()
                else -> state.release()
            }
        }
    }
}

/** How often the Home log asks Core for new connections. */
internal const val ConnectionLogPollMillis = 250L

private val ConnectionLogTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal fun formatDesktopConnectionLogTime(timeMillis: Long): String =
    ConnectionLogTimeFormat.format(Instant.ofEpochMilli(timeMillis))
