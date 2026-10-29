// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreLogStorageTest {
    private val directory: File = Files.createTempDirectory("skipi-logs-").toFile()
    private val failures = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun store(file: File, maxLines: Int = 5, extra: Int = 2) = BoundedLogFileStore(
        file = { file },
        logTag = "test",
        maxLines = maxLines,
        trimExtraLines = extra,
        onFailure = { message, _ -> failures += message },
    )

    @Test
    fun parserConvertsXrayUtcTimestampsToLocalTimeAndKeepsLevels() {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        try {
            val withLevel = parseCoreLogLine("2026/09/11 20:40:05.901279 [Warning] dns slow", "error")
            val withoutLevel = parseCoreLogLine("2026/09/11 20:40:05 plain event", "info")
            val plain = parseCoreLogLine("  just text  ", "error")

            assertEquals("2026-09-12 05:40:05", withLevel?.time)
            assertEquals("Warning", withLevel?.level)
            assertEquals("2026-09-12 05:40:05 [Warning] dns slow", withLevel?.message)
            assertEquals("info", withoutLevel?.level)
            assertEquals("2026-09-12 05:40:05 plain event", withoutLevel?.message)
            assertNull(plain?.time)
            assertEquals("error", plain?.level)
            assertEquals("just text", plain?.message)
            assertNull(parseCoreLogLine("   ", "error"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun boundedStoreTrimsOnlyAfterTheExtraLinesAreExceeded() {
        val file = File(directory, "a.log")
        val store = store(file)

        repeat(7) { store.appendLine("line $it") }
        assertEquals(7, file.readLines().size, "No trim until maxLines + extra is exceeded")

        store.appendLine("line 7")
        assertEquals(listOf("line 3", "line 4", "line 5", "line 6", "line 7"), file.readLines())
        assertEquals(listOf("line 3", "line 4", "line 5", "line 6", "line 7"), store.readLastLines())
    }

    @Test
    fun boundedStoreReadsOnlyTheLastLinesPrunesAndClears() {
        val file = File(directory, "b.log")
        file.writeText((1..9).joinToString("\n", postfix = "\n") { "n$it" })
        val store = store(file)

        assertEquals(listOf("n5", "n6", "n7", "n8", "n9"), store.readLastLines())
        store.prune { it != "n7" }
        assertEquals(listOf("n5", "n6", "n8", "n9"), file.readLines())
        store.clear()
        assertEquals("", file.readText())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun boundedStoreWithoutAFileDoesNothing() {
        val store = BoundedLogFileStore(file = { null }, logTag = "t", onFailure = { _, _ -> })

        store.appendLine("x")
        store.clear()
        assertEquals(emptyList(), store.readLastLines())
    }

    @Test
    fun xrayFileRepositoryRestoresSortedPreviousSessionAndDropsExpiredLines() = runBlocking {
        val file = File(directory, "error.log")
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        fun time(millis: Long) = timeFormat.format(Date(millis)).replace('-', '/')
        file.writeText(
            listOf(
                "${time(now - 1 * day)} [Info] newer",
                "${time(now - 10 * day)} [Info] expired",
                "${time(now - 2 * day)} [Error] older",
                "unparsed line",
            ).joinToString("\n", postfix = "\n"),
        )
        val repository = XrayFileLogRepository("repo") { message, _ -> failures += message }

        repository.initialize(CoreLogFile(file.absolutePath, "error"), retentionDays = 7)

        val messages = repository.entries.value.map { it.message.substringAfter(" ", it.message) }
        // Untimed lines sort first (empty time), then by timestamp; the expired one is gone.
        assertEquals(3, repository.entries.value.size)
        assertTrue(messages.last().endsWith("newer"))
        assertFalse(file.readText().contains("expired"))
        assertEquals((1L..3L).toList(), repository.entries.value.map { it.id })
    }

    @Test
    fun xrayFileRepositoryRefreshRereadsTheFile() = runBlocking {
        val file = File(directory, "refresh.log")
        file.writeText("2026/01/01 10:00:00 [Info] first\n")
        val repository = XrayFileLogRepository("repo") { _, _ -> }
        repository.initialize(CoreLogFile(file.absolutePath, "error"))
        assertEquals(1, repository.entries.value.size)

        file.appendText("2026/01/01 10:00:01 [Info] second\n")
        repository.refresh()

        assertEquals(2, repository.entries.value.size)
    }

    @Test
    fun persistedAppRepositoryRoundTripsMultilineMessagesAndMergesPendingEntries() = runBlocking {
        val file = File(directory, "app.log")
        val first = PersistedAppLogRepository("app") { _, _ -> }
        first.initialize(file)
        first.append("warn", "two\nlines", "2026-01-01 10:00:00")
        first.append("nonsense", "x", "2026-01-01 10:00:01")

        val restored = PersistedAppLogRepository("app") { _, _ -> }
        restored.append("info", "before init", "2026-01-01 10:00:02")
        restored.initialize(file)

        val entries = restored.entries.value
        assertEquals(listOf("two\nlines", "x", "before init"), entries.map { it.message })
        assertEquals(listOf("warning", "info", "info"), entries.map { it.level })
        assertTrue(file.readLines().last().startsWith("2026-01-01 10:00:02\tinfo\t"))
    }

    @Test
    fun foregroundViewersSpeedUpTailPollingUntilReleased() {
        assertEquals(CoreLogBackgroundTailIntervalMillis, CoreLogTailerPollingPolicy.intervalMillis())
        val lease = CoreLogTailerPollingPolicy.acquireViewer()
        assertEquals(CoreLogForegroundTailIntervalMillis, CoreLogTailerPollingPolicy.intervalMillis())
        lease.close()
        lease.close()
        assertEquals(CoreLogBackgroundTailIntervalMillis, CoreLogTailerPollingPolicy.intervalMillis())
    }
}
