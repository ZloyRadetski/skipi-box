// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ConnectionLogEntry
import app.skipi.app.home.ConnectionLogItem
import app.skipi.app.home.ConnectionLogLevel
import app.skipi.app.home.ConnectionLogMessage
import app.skipi.app.home.ConnectionLogSource
import app.skipi.app.home.ConnectionLogState
import app.skipi.app.home.connectionLogLevel
import app.skipi.app.home.parseConnectionLogSnapshot
import features.logs.ParsedCoreLogLine
import features.logs.parseCoreLogTimeMillis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Log lines newer than the cursor passed to [DesktopLogFeed.after], and the cursor for the next read. */
internal data class DesktopLogFeedSnapshot(
    val seq: Long,
    val messages: List<ConnectionLogMessage>,
)

/** The newest lines of the app's and Xray's logs for the Home log, in the order they came. */
internal class DesktopLogFeed(
    private val capacity: Int = ConnectionLogState.Capacity,
) {
    private val lines = ArrayDeque<ConnectionLogMessage>()
    private var seq = 0L

    @Synchronized
    fun add(message: ConnectionLogMessage) {
        seq++
        lines.addLast(message)
        if (lines.size > capacity) lines.removeFirst()
    }

    @Synchronized
    fun after(cursor: Long): DesktopLogFeedSnapshot {
        val newer = (seq - cursor).coerceIn(0L, lines.size.toLong()).toInt()
        return DesktopLogFeedSnapshot(seq, lines.takeLast(newer))
    }
}

/** Fed from the first line the app logs, so the Home log can show how the start went. */
internal val DesktopAppLogFeed = DesktopLogFeed()

/** An Xray line for the Home log, whose row shows the time and level on its own. */
internal fun ParsedCoreLogLine.toConnectionLogMessage(nowMillis: Long = System.currentTimeMillis()): ConnectionLogMessage =
    ConnectionLogMessage(
        timeMillis = time?.let(::parseCoreLogTimeMillis) ?: nowMillis,
        level = connectionLogLevel(level),
        source = ConnectionLogSource.Core,
        text = time?.let { message.removePrefix("$it [$level] ").removePrefix("$it ") } ?: message,
    )

/** The least severe line shown beside the connections at [levelName]; null at "none", which shows nothing. */
internal fun connectionLogThreshold(levelName: String): ConnectionLogLevel? =
    if (levelName.trim().equals("none", ignoreCase = true)) null else connectionLogLevel(levelName)

/** Host of the Home log: reads what Core and the logs recorded since the last poll. */
internal class DesktopConnectionLog(
    private val read: (Long) -> Result<String>,
    private val readMessages: (Long) -> DesktopLogFeedSnapshot = { DesktopLogFeedSnapshot(0, emptyList()) },
) {
    private val mutableState = MutableStateFlow(ConnectionLogState())
    private var cursor = 0L
    private var messageCursor = 0L

    val state: StateFlow<ConnectionLogState> = mutableState.asStateFlow()

    /** The log level chosen in settings: it sets which lines beside the connections are shown. */
    @Volatile
    var logLevel: String = "error"

    @Synchronized
    fun poll() {
        // Both sources are read either way, so a level raised later does not replay old lines.
        val connections = newConnections()
        val messages = newMessages()
        val threshold = connectionLogThreshold(logLevel)
        if (threshold == null) {
            mutableState.update(ConnectionLogState::clear)
            return
        }
        val items: List<ConnectionLogItem> = connections + messages.filter { it.level >= threshold }
        mutableState.update { state -> state.append(items) }
    }

    private fun newConnections(): List<ConnectionLogEntry> {
        val snapshot = read(cursor).mapCatching(::parseConnectionLogSnapshot).getOrNull() ?: return emptyList()
        // Every Core session counts from zero, so a smaller cursor means a new session.
        if (snapshot.seq < cursor) {
            cursor = 0
            return emptyList()
        }
        cursor = snapshot.seq
        return snapshot.entries
    }

    private fun newMessages(): List<ConnectionLogMessage> {
        val snapshot = readMessages(messageCursor)
        messageCursor = snapshot.seq
        return snapshot.messages
    }

    /** A session change keeps the rows, so what led to a failure stays readable. */
    @Synchronized
    fun startSession() {
        cursor = 0
    }

    @Synchronized
    fun clear() {
        cursor = 0
        messageCursor = 0
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

/** How often the Home log asks for new lines. */
internal const val ConnectionLogPollMillis = 250L

/** Runs for one session or the lack of one, until cancelled; a log switched off forgets its rows. */
internal suspend fun DesktopConnectionLog.follow(enabled: Boolean) {
    if (!enabled) {
        clear()
        return
    }
    startSession()
    while (true) {
        withContext(Dispatchers.IO) { poll() }
        delay(ConnectionLogPollMillis)
    }
}

private val ConnectionLogTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal fun formatDesktopConnectionLogTime(timeMillis: Long): String =
    ConnectionLogTimeFormat.format(Instant.ofEpochMilli(timeMillis))
