// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.resources

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared strings are read by Compose as written; Android-style escapes would show as stray backslashes. */
class StringResourceSourceTest {
    private val resourceDirectory = File("src/commonMain/composeResources")
    private val stringFiles = resourceDirectory.listFiles { file -> file.name.startsWith("values") }
        .orEmpty()
        .map { File(it, "strings.xml") }
        .filter(File::isFile)

    @Test
    fun everyShippedLanguageHasAStringFile() {
        assertEquals(
            setOf("values", "values-fa", "values-ru", "values-zh", "values-zh-rCN"),
            stringFiles.map { it.parentFile.name }.toSet(),
        )
    }

    @Test
    fun stringsAreWrittenWithoutAndroidQuoteEscapes() {
        val backslash = 92.toChar()
        val offenders = stringFiles.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> line.contains("$backslash'") || line.contains("$backslash\"") }
                .map { (index, _) -> "${file.parentFile.name}:${index + 1}" }
        }
        assertTrue(offenders.isEmpty(), "Escaped quotes show as a backslash on Desktop; write them plain: $offenders")
    }
}
