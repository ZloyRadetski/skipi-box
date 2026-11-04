// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopConnectionLogTest {
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
        assertEquals(listOf("c.test", "b.test", "a.test"), log.state.value.rows.map { it.host })
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
        assertEquals("new.test", log.state.value.rows.first().host)
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

        assertEquals(listOf("a.test"), log.state.value.rows.map { it.host })
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
        assertEquals(listOf("a.test"), log.state.value.rows.map { it.host })
        assertEquals(1, log.state.value.heldCount)

        log.setHeld(false)
        assertEquals(listOf("b.test", "a.test"), log.state.value.rows.map { it.host })
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
