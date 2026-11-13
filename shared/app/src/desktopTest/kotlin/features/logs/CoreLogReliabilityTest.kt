// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CoreLogReliabilityTest {
    private val directory: File = Files.createTempDirectory("skipi-logs-rel-").toFile()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun restoredContinuationLinesStayRightAfterTheTimedLineTheyBelongTo() {
        val file = File(directory, "x.log")
        file.writeText(
            listOf(
                "2026/01/01 10:00:00 [Info] second",
                "continuation of second",
                "2026/01/01 09:00:00 [Info] first",
            ).joinToString("\n", postfix = "\n"),
        )
        val repository = XrayFileLogRepository("repo") { _, _ -> }

        repository.initialize(CoreLogFile(file.absolutePath, "error"))

        val messages = repository.entries.value.map { it.message }
        assertEquals(3, messages.size)
        assertEquals(true, messages[0].endsWith("first"))
        assertEquals(true, messages[1].endsWith("second"))
        assertEquals("continuation of second", messages[2])
    }

    @Test
    fun appendBatchIsPersistedAndRestored() {
        val file = File(directory, "app.log")
        val first = PersistedAppLogRepository("app") { _, _ -> }
        first.initialize(file)
        first.appendBatch(
            listOf(
                ParsedCoreLogLine("2026-01-01 10:00:00", "info", "one"),
                ParsedCoreLogLine("2026-01-01 10:00:01", "error", "two"),
            ),
        )

        val restored = PersistedAppLogRepository("app") { _, _ -> }
        restored.initialize(file)

        assertEquals(listOf("one", "two"), restored.entries.value.map { it.message })
    }


    private fun store(file: File, ownsFile: Boolean) = BoundedLogFileStore(
        file = { file },
        logTag = "test",
        maxLines = 5,
        trimExtraLines = 2,
        ownsFile = ownsFile,
        onFailure = { message, _ -> error(message) },
    )

    @Test
    fun pruningAForeignFileKeepsLinesOutsideTheWindowAndOnlyDropsWhatTheRuleDrops() {
        val file = File(directory, "foreign.log")
        file.writeText((1..9).joinToString("\n", postfix = "\n") { "n$it" })
        val store = store(file, ownsFile = false)

        store.prune { true }
        assertEquals((1..9).map { "n$it" }, file.readLines())

        store.prune { it != "n7" }
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5", "n6", "n8", "n9"), file.readLines())
    }

    @Test
    fun pollerDoesNotReturnALineBeforeItsNewlineArrives() {
        val file = File(directory, "p.log")
        file.writeText("")
        val poller = CoreLogFilePoller(file, startAtEnd = false)

        file.appendText("one\ntw")
        assertEquals(listOf("one"), poller.poll())
        assertEquals(emptyList(), poller.poll())

        file.appendText("o\n")
        assertEquals(listOf("two"), poller.poll())
    }

    @Test
    fun pollerDecodesMultibyteUtf8AndCrlf() {
        val file = File(directory, "u.log")
        val japanese = "日本".toByteArray(Charsets.UTF_8)
        file.writeBytes("привет\r\n".toByteArray(Charsets.UTF_8) + japanese.copyOf(2))
        val poller = CoreLogFilePoller(file, startAtEnd = false)

        assertEquals(listOf("привет"), poller.poll())
        file.appendBytes(japanese.copyOfRange(2, 6) + "\n".toByteArray())
        assertEquals(listOf("日本"), poller.poll())
    }

    @Test
    fun pollerDoesNotRepublishLinesWhenRetentionRewritesTheFileShorter() {
        val file = File(directory, "r.log")
        file.writeText("a\nb\nc\n")
        val poller = CoreLogFilePoller(file, startAtEnd = false)
        assertEquals(listOf("a", "b", "c"), poller.poll())

        file.writeText("b\nc\n") // pruning dropped the oldest line
        assertEquals(emptyList(), poller.poll())
        file.appendText("d\n")
        assertEquals(listOf("d"), poller.poll())
    }

    @Test
    fun pollerDoesNotRepublishWhenARewriteIsNotShorterThanThePosition() {
        val file = File(directory, "r2.log")
        file.writeText("a\nb\nc\n")
        val poller = CoreLogFilePoller(file, startAtEnd = false)
        poller.poll()

        file.writeText("b\nc\nd\ne\n") // pruned "a" while Xray appended two more lines
        assertEquals(listOf("d", "e"), poller.poll())
    }

    @Test
    fun pollerRestartsFromTheBeginningWhenTheFileWasTruncated() {
        val file = File(directory, "t.log")
        file.writeText("old one\nold two\n")
        val poller = CoreLogFilePoller(file, startAtEnd = false)
        poller.poll()

        file.writeText("new\n")
        assertEquals(listOf("new"), poller.poll())
    }

    private class CountingRepository : CoreLogRepository {
        val prunedDays = mutableListOf<Int>()
        override val entries = kotlinx.coroutines.flow.MutableStateFlow(emptyList<CoreLogEntry>())
        override fun append(level: String, message: String, time: String) = Unit
        override fun clear() = Unit
        override fun pruneOlderThanDays(days: Int) {
            prunedDays += days
            if (prunedDays.size == 1) error("first round fails")
        }
    }

    @Test
    fun retentionLoopPrunesImmediatelyThenEveryIntervalAndSkipsWhenDisabled() = runTest {
        val repository = CountingRepository()
        val other = CountingRepository()
        var days = 7
        val job = launch {
            runLogRetention(listOf(repository, other), { days }, intervalMillis = 1_000, ioDispatcher = Dispatchers.Unconfined)
        }
        runCurrent()
        assertEquals(listOf(7), repository.prunedDays)
        assertEquals(listOf(7), other.prunedDays, "a failing repository must not stop the others")

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(7, 7), repository.prunedDays)

        days = 0
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, repository.prunedDays.size)
        job.cancelAndJoin()
    }

    @Test
    fun refreshKeepsLinesTheTailerAppendedWhileTheFileWasBeingRead() = runBlocking {
        val file = File(directory, "race.log")
        file.writeText("2026/01/01 10:00:00 [Info] first\n")
        val repository = XrayFileLogRepository("repo") { _, _ -> }
        repository.initialize(CoreLogFile(file.absolutePath, "error"))
        repository.afterRefreshRead = {
            repository.append("info", "2026-01-01 10:00:05 tailer line", "2026-01-01 10:00:05")
        }

        repository.refresh()

        assertTrue(
            repository.entries.value.any { it.message.contains("tailer line") },
            "entries: ${repository.entries.value}",
        )
    }

    @Test
    fun inMemoryRepositoryKeepsTheLastEntriesWithGrowingIdsAndImmutableSnapshots() {
        val repository = InMemoryCoreLogRepository(maxEntries = 5)
        repository.appendBatch((1..3).map { ParsedCoreLogLine("t", "info", "m$it") })
        val before = repository.entries.value

        repository.appendBatch((4..8).map { ParsedCoreLogLine("t", "info", "m$it") })
        repeat(30) { repository.append("info", "x$it", "t") }

        assertEquals(listOf("m1", "m2", "m3"), before.map { it.message })
        assertEquals((34L..38L).toList(), repository.entries.value.map { it.id })
        assertEquals((25..29).map { "x$it" }, repository.entries.value.map { it.message })
        repository.clear()
        repository.append("info", "again", "t")
        assertEquals(listOf(1L), repository.entries.value.map { it.id })
    }
}
