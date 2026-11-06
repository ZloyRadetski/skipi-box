// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ConnectionLogLevel
import app.skipi.app.home.ConnectionLogMessage
import app.skipi.app.home.ConnectionLogMessageRow
import app.skipi.app.home.ConnectionLogRow
import app.skipi.app.home.ConnectionLogSource
import app.skipi.app.home.ConnectionLogState
import features.logs.ParsedCoreLogLine
import features.logs.parseCoreLogLine
import features.logs.parseCoreLogTimeMillis
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopConnectionLogTest {
    private val ConnectionLogState.connections get() = rows.filterIsInstance<ConnectionLogRow>()

    private val ConnectionLogState.messages get() = rows.filterIsInstance<ConnectionLogMessageRow>()

    private fun appError(text: String) = ConnectionLogMessage(1_000, ConnectionLogLevel.Error, ConnectionLogSource.App, text)

    private fun snapshot(seq: Long, vararg hosts: String): String {
        val entries = hosts.withIndex().joinToString(",") { (index, host) ->
            val entrySeq = seq - hosts.size + index + 1
            """{"seq":$entrySeq,"time":1000,"host":"$host","port":443,"network":"tcp","outbound":"proxy"}"""
        }
        return """{"seq":$seq,"entries":[$entries]}"""
    }

    @Test
    fun eachPollAsksOnlyForWhatIsNew() {
        val cursors = mutableListOf<Long>()
        val replies = ArrayDeque(listOf(snapshot(2, "a.test", "b.test"), snapshot(3, "c.test")))
        val log = DesktopConnectionLog(read = { after ->
            cursors += after
            Result.success(replies.removeFirst())
        })

        log.poll()
        log.poll()

        assertEquals(listOf(0L, 2L), cursors)
        assertEquals(listOf("c.test", "b.test", "a.test"), log.state.value.connections.map { it.host })
    }

    @Test
    fun aNewCoreSessionStartsCountingFromZeroAgain() {
        val cursors = mutableListOf<Long>()
        val replies = ArrayDeque(listOf(snapshot(5, "old.test"), snapshot(1, "new.test"), snapshot(1, "new.test")))
        val log = DesktopConnectionLog(read = { after ->
            cursors += after
            Result.success(replies.removeFirst())
        })

        log.poll()
        log.poll()
        log.poll()

        assertEquals(listOf(0L, 5L, 0L), cursors)
        assertEquals("new.test", log.state.value.connections.first().host)
    }

    @Test
    fun aFailedOrMalformedReadLeavesTheRowsAlone() {
        val replies = ArrayDeque(
            listOf(
                Result.success(snapshot(1, "a.test")),
                Result.failure(IllegalStateException("old library")),
                Result.success("not json"),
            ),
        )
        val log = DesktopConnectionLog(read = { replies.removeFirst() })

        log.poll()
        log.poll()
        log.poll()

        assertEquals(listOf("a.test"), log.state.value.connections.map { it.host })
    }

    @Test
    fun clearForgetsRowsAndTheCursor() {
        val cursors = mutableListOf<Long>()
        val log = DesktopConnectionLog(read = { after ->
            cursors += after
            Result.success(snapshot(1, "a.test"))
        })

        log.poll()
        log.clear()
        assertTrue(log.state.value.rows.isEmpty())
        log.poll()

        assertEquals(listOf(0L, 0L), cursors)
    }

    @Test
    fun holdingFreezesTheRowsUntilReleased() {
        val replies = ArrayDeque(listOf(snapshot(1, "a.test"), snapshot(2, "b.test")))
        val log = DesktopConnectionLog(read = { Result.success(replies.removeFirst()) })

        log.poll()
        log.setHeld(true)
        log.poll()
        assertEquals(listOf("a.test"), log.state.value.connections.map { it.host })
        assertEquals(1, log.state.value.heldCount)

        log.setHeld(false)
        assertEquals(listOf("b.test", "a.test"), log.state.value.connections.map { it.host })
    }

    private fun lineAt(level: ConnectionLogLevel, text: String, source: ConnectionLogSource = ConnectionLogSource.App) =
        ConnectionLogMessage(1_000, level, source, text)

    private fun shownAt(levelName: String): List<String> {
        val feed = DesktopLogFeed()
        val log = DesktopConnectionLog(read = { Result.success(snapshot(1, "a.test")) }, readMessages = feed::after)
        log.logLevel = levelName
        ConnectionLogLevel.entries.forEach { level ->
            feed.add(lineAt(level, "app $level"))
            feed.add(lineAt(level, "xray $level", ConnectionLogSource.Core))
        }
        log.poll()
        return log.state.value.rows.map { row ->
            when (row) {
                is ConnectionLogRow -> row.host
                is ConnectionLogMessageRow -> row.title
            }
        }.sorted()
    }

    @Test
    fun domainsAreShownAtEveryLevelAndTheLevelSetsTheLinesBesideThem() {
        assertEquals(
            listOf("a.test", "app Error", "xray Error"),
            shownAt("error"),
        )
        assertEquals(
            listOf("a.test", "app Error", "app Warning", "xray Error", "xray Warning"),
            shownAt("warning"),
        )
        assertEquals(8 + 1, shownAt("debug").size)
        assertEquals(6 + 1, shownAt("info").size)
    }

    @Test
    fun noneShowsNothingAtAll() {
        assertEquals(emptyList(), shownAt("none"))
    }

    @Test
    fun logLinesReachTheListWhileCoreIsNotRunning() {
        val feed = DesktopLogFeed()
        val log = DesktopConnectionLog(
            read = { Result.failure(IllegalStateException("SKIPI Core is not running")) },
            readMessages = feed::after,
        )

        feed.add(appError("Tunnel: Connection failed"))
        log.poll()

        assertEquals(listOf("Tunnel: Connection failed"), log.state.value.messages.map { it.title })
    }

    @Test
    fun eachLogLineIsShownOnce() {
        val feed = DesktopLogFeed()
        val log = DesktopConnectionLog(read = { Result.success(snapshot(0)) }, readMessages = feed::after)

        feed.add(appError("one"))
        log.poll()
        log.poll()
        feed.add(appError("two"))
        log.poll()

        assertEquals(listOf("two", "one"), log.state.value.messages.map { it.title })
    }

    @Test
    fun aNewSessionKeepsTheRowsAndAsksCoreFromTheStart() {
        val cursors = mutableListOf<Long>()
        val replies = ArrayDeque(listOf(snapshot(5, "old.test"), snapshot(1, "new.test")))
        val log = DesktopConnectionLog(read = { after ->
            cursors += after
            Result.success(replies.removeFirst())
        })

        log.poll()
        log.startSession()
        log.poll()

        assertEquals(listOf(0L, 0L), cursors)
        assertEquals(listOf("new.test", "old.test"), log.state.value.connections.map { it.host })
    }

    @Test
    fun aNewSessionDoesNotShowTheLogLinesAgain() {
        val feed = DesktopLogFeed()
        val log = DesktopConnectionLog(read = { Result.success(snapshot(0)) }, readMessages = feed::after)

        feed.add(appError("Tunnel: Connection failed"))
        log.poll()
        log.startSession()
        log.poll()

        assertEquals(1, log.state.value.messages.size)
    }

    @Test
    fun aFollowedLogKeepsReadingWithNoSessionAndKeepsItsRowsAcrossSessions() = runBlocking {
        val feed = DesktopLogFeed()
        val log = DesktopConnectionLog(
            read = { Result.failure(IllegalStateException("SKIPI Core is not running")) },
            readMessages = feed::after,
        )
        feed.add(appError("Tunnel: Connection failed"))

        val disconnected = launch { log.follow(enabled = true) }
        withTimeout(3_000) { log.state.first { it.rows.size == 1 } }
        disconnected.cancelAndJoin()

        val nextSession = launch { log.follow(enabled = true) }
        feed.add(appError("Tunnel: Connected"))
        withTimeout(3_000) { log.state.first { it.rows.size == 2 } }
        nextSession.cancelAndJoin()

        assertEquals(listOf("Tunnel: Connected", "Tunnel: Connection failed"), log.state.value.messages.map { it.title })
    }

    @Test
    fun switchingTheLogOffForgetsItsRowsAndStopsReading() = runBlocking {
        var reads = 0
        val log = DesktopConnectionLog(read = {
            reads++
            Result.success(snapshot(1, "a.test"))
        })
        log.poll()

        log.follow(enabled = false)

        assertTrue(log.state.value.rows.isEmpty())
        assertEquals(1, reads)
    }

    @Test
    fun theFeedAnswersWithWhatIsNewerThanTheCursor() {
        val feed = DesktopLogFeed()
        feed.add(appError("one"))
        feed.add(appError("two"))

        val first = feed.after(0)
        feed.add(appError("three"))
        val second = feed.after(first.seq)

        assertEquals(listOf("one", "two"), first.messages.map { it.text })
        assertEquals(listOf("three"), second.messages.map { it.text })
        assertTrue(feed.after(second.seq).messages.isEmpty())
    }

    @Test
    fun theFeedKeepsOnlyTheNewestLines() {
        val feed = DesktopLogFeed(capacity = 2)
        listOf("one", "two", "three").forEach { feed.add(appError(it)) }

        assertEquals(listOf("two", "three"), feed.after(0).messages.map { it.text })
    }

    @Test
    fun appLogLinesReachTheFeedWithTheirTagLevelAndCause() {
        val cursor = DesktopAppLogFeed.after(0).seq

        DesktopLogger.error("Tunnel", "Connection failed", IllegalStateException("port is busy"))

        val message = DesktopAppLogFeed.after(cursor).messages.single { "Connection failed" in it.text }
        assertEquals(ConnectionLogLevel.Error, message.level)
        assertEquals(ConnectionLogSource.App, message.source)
        assertEquals("Tunnel: Connection failed: port is busy", message.text.lineSequence().first())
        assertTrue("IllegalStateException" in message.text.lines().drop(1).joinToString("\n"), message.text)
    }

    @Test
    fun everyAppLogLevelReachesTheFeed() {
        val cursor = DesktopAppLogFeed.after(0).seq

        DesktopLogger.debug("Levels", "a debug line")
        DesktopLogger.info("Levels", "an info line")
        DesktopLogger.warn("Levels", "a warning line")

        val levels = DesktopAppLogFeed.after(cursor).messages.filter { it.text.startsWith("Levels: ") }.map { it.level }
        assertEquals(listOf(ConnectionLogLevel.Debug, ConnectionLogLevel.Info, ConnectionLogLevel.Warning), levels)
    }

    @Test
    fun anXrayLineLosesTheStampAndLevelThatTheRowShowsOnItsOwn() {
        val line = checkNotNull(parseCoreLogLine("2026/11/05 10:00:00.123456 [Warning] app/dns: timed out", "error"))

        val message = line.toConnectionLogMessage()

        assertEquals("app/dns: timed out", message.text)
        assertEquals(ConnectionLogLevel.Warning, message.level)
        assertEquals(ConnectionLogSource.Core, message.source)
        assertEquals(parseCoreLogTimeMillis(checkNotNull(line.time)), message.timeMillis)
    }

    @Test
    fun anXrayLineWithoutAStampTakesTheCurrentTimeAndTheFileLevel() {
        val message = ParsedCoreLogLine(time = null, level = "error", message = "panic: boom")
            .toConnectionLogMessage(nowMillis = 7_000)

        assertEquals("panic: boom", message.text)
        assertEquals(ConnectionLogLevel.Error, message.level)
        assertEquals(7_000, message.timeMillis)
    }

    @Test
    fun timeIsShownAsLocalHoursMinutesAndSeconds() {
        val text = formatDesktopConnectionLogTime(0L)

        assertTrue(Regex("""\d{2}:\d{2}:\d{2}""").matches(text), text)
    }

    @Test
    fun aSessionGetsTheLogSwitchBeforeCoreStarts() {
        val native = RecordingNative()
        val controller = controllerOver(native)

        controller.setConnectionLogEnabled(true)
        controller.start("{}").getOrThrow()

        assertEquals(listOf("log:true", "start"), native.calls)
    }

    @Test
    fun theSwitchReachesARunningSession() {
        val native = RecordingNative()
        val controller = controllerOver(native)
        controller.start("{}").getOrThrow()
        native.calls.clear()

        controller.setConnectionLogEnabled(true)

        assertEquals(listOf("log:true"), native.calls)
    }

    @Test
    fun aLibraryWithoutTheConnectionLogStillStartsAndReadsFailQuietly() {
        val native = RecordingNative(supportsConnectionLog = false)
        val controller = controllerOver(native)
        controller.setConnectionLogEnabled(true)

        assertTrue(controller.start("{}").getOrThrow().isRunning)

        assertTrue(controller.readConnectionLog(0).isFailure)
        assertEquals("", controller.state().lastError)
    }

    @Test
    fun readingWithoutASessionFails() {
        val controller = controllerOver(RecordingNative())

        assertFalse(controller.readConnectionLog(0).isSuccess)
    }

    private fun controllerOver(native: RecordingNative) = DesktopCoreController(
        runtimeProvider = {
            Result.success(
                DesktopCoreRuntime(
                    directory = Path.of("runtime"),
                    library = Path.of("runtime", "libskipicore.so"),
                    geoIp = Path.of("runtime", "geoip.dat"),
                    geoSite = Path.of("runtime", "geosite.dat"),
                ),
            )
        },
        nativeLoader = { native },
    )

    private class RecordingNative(private val supportsConnectionLog: Boolean = true) : DesktopCoreNative {
        override val apiVersion: String = DesktopCoreApiVersion
        override val coreVersion: String = "connection-log-test"
        val calls = mutableListOf<String>()
        private var running = false

        override fun initializeAssets(directory: String) = Unit
        override fun createController(): Long = 7L
        override fun destroyController(handle: Long) = Unit
        override fun start(handle: Long, configJson: String, tunFd: Long) {
            calls += "start"
            running = true
        }
        override fun stop(handle: Long) {
            running = false
        }
        override fun isRunning(handle: Long): Boolean = running
        override fun queryTrafficStats(handle: Long): String = "{}"
        override fun measureDelay(handle: Long, targetUrl: String): Long = 1L
        override fun readMemoryStats(): String = "{}"
        override fun forceFreeMemory() = Unit
        override fun setConnectionLogEnabled(handle: Long, enabled: Boolean) {
            if (!supportsConnectionLog) return super.setConnectionLogEnabled(handle, enabled)
            calls += "log:$enabled"
        }
        override fun readConnectionLog(handle: Long, afterSeq: Long): String {
            if (!supportsConnectionLog) return super.readConnectionLog(handle, afterSeq)
            return """{"seq":0,"entries":[]}"""
        }
        override fun close() = Unit
    }
}
