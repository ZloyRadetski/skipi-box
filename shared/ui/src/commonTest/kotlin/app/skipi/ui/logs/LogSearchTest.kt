// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.logs

import features.logs.CoreLogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogSearchTest {
    private fun entry(message: String, level: String = "info") =
        CoreLogEntry(id = 1, time = "12:00:00", level = level, message = message)

    private fun matches(query: String, message: String, level: String = "info"): Boolean {
        val e = entry(message, level)
        return LogSearch.parse(query).matches(e, e.logLine(showMetadata = true).plain)
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(LogSearch.parse("  ").isEmpty)
        assertTrue(matches("   ", "anything"))
    }

    @Test
    fun caseDoesNotMatter() {
        assertTrue(matches("YOUTUBE", "tcp  youtube.com:443  → proxy"))
        assertTrue(matches("youtube", "tcp  YouTube.com:443"))
        assertTrue(matches("ПРИВЕТ", "привет мир"))
    }

    @Test
    fun yoAndYeAreTheSameLetter() {
        assertTrue(matches("еж", "Ёж на дороге"))
        assertTrue(matches("ёж", "еж на дороге"))
    }

    @Test
    fun everyWordMustBePresentInAnyOrder() {
        assertTrue(matches("proxy youtube", "tcp  youtube.com:443  → proxy"))
        assertFalse(matches("proxy google", "tcp  youtube.com:443  → proxy"))
    }

    @Test
    fun quotedPhraseMatchesAsOneTerm() {
        assertTrue(matches("\"failed to dial\"", "failed to dial tcp"))
        assertFalse(matches("\"dial failed\"", "failed to dial tcp"))
    }

    @Test
    fun minusExcludesAWord() {
        assertTrue(matches("tcp -google", "tcp  youtube.com:443"))
        assertFalse(matches("tcp -google", "tcp  google.com:443"))
        assertTrue(matches("-", "tcp"))
    }

    @Test
    fun levelFilterUsesTheEntryLevel() {
        assertTrue(matches("level:error", "boom", level = "error"))
        assertFalse(matches("level:error", "boom", level = "info"))
        assertTrue(matches("level:warn", "x", level = "warning"))
        assertTrue(matches("level:warn level:error boom", "boom", level = "error"))
        assertFalse(matches("level:error dns", "boom", level = "error"))
    }

    @Test
    fun searchSeesTheTimeOfTheShownLine() {
        assertTrue(matches("12:00", "x"))
    }

    @Test
    fun highlightMarksEveryOccurrenceIgnoringCase() {
        val ranges = LogSearch.parse("a").highlightRanges("aBa")
        assertEquals(listOf(0..0, 2..2), ranges)
    }

    @Test
    fun highlightIgnoresExcludedWordsAndLevels() {
        assertTrue(LogSearch.parse("-a level:error").highlightRanges("aaa").isEmpty())
    }

    @Test
    fun overlappingHighlightsMerge() {
        assertEquals(listOf(0..3), LogSearch.parse("abc bcd").highlightRanges("abcd"))
    }
}
