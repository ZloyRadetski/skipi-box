// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.logs

import features.logs.CoreLogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogLineTextTest {
    private val entry = CoreLogEntry(id = 1, time = "12:03:41", level = "info", message = "started")

    @Test
    fun metadataLineIsTimeLevelMessage() {
        val line = entry.logLine(showMetadata = true)
        assertEquals("12:03:41", line.time)
        assertEquals("INFO", line.level)
        assertEquals("started", line.message)
        assertEquals("12:03:41  INFO  started", line.plain)
    }

    @Test
    fun lineWithoutMetadataIsTheMessageOnly() {
        val line = entry.logLine(showMetadata = false)
        assertNull(line.time)
        assertNull(line.level)
        assertEquals("started", line.plain)
    }

    @Test
    fun shownTextMatchesWhatCopyAndExportWrite() {
        for (metadata in listOf(true, false)) {
            assertEquals(entry.exportText(metadata), entry.logLine(metadata).plain)
        }
    }

    @Test
    fun levelsMapToTones() {
        assertEquals(LogTone.Error, logTone("ERROR"))
        assertEquals(LogTone.Error, logTone("fatal"))
        assertEquals(LogTone.Warning, logTone("warn"))
        assertEquals(LogTone.Warning, logTone("Warning"))
        assertEquals(LogTone.Neutral, logTone("info"))
        assertEquals(LogTone.Neutral, logTone("debug"))
    }

    @Test
    fun blockTextJoinsLinesInShownOrder() {
        val second = entry.copy(id = 2, message = "again")
        assertEquals(
            "12:03:41  INFO  started\n12:03:41  INFO  again",
            logBlockText(listOf(entry, second), showMetadata = true),
        )
    }
}
