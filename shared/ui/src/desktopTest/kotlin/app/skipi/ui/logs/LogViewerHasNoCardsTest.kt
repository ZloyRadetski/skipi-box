// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.logs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The log pages are plain monospace text; a card per entry is the look this replaced. */
class LogViewerHasNoCardsTest {
    private val sources = File("src/commonMain/kotlin/app/skipi/ui/logs")
        .listFiles { file -> file.extension == "kt" }
        .orEmpty()

    @Test
    fun logSourcesExist() {
        assertTrue(sources.isNotEmpty())
    }

    @Test
    fun noCardOrChipIsUsedForLogRows() {
        for (file in sources) {
            val text = file.readText()
            assertTrue("LogEntryCard" !in text, "${file.name} still draws a card per entry")
            assertTrue("LogEmptyCard" !in text, "${file.name} still draws an empty-state card")
            assertTrue("miuix.kmp.basic.Card" !in text, "${file.name} imports Card")
        }
    }
}
