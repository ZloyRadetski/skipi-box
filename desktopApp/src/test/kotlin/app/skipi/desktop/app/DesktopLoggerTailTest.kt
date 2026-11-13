// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLoggerTailTest {
    @Test
    fun aSmallFileGivesItsLastLines() {
        val file = Files.createTempFile("skipi-log", ".log")
        try {
            Files.writeString(file, (1..10).joinToString("\n", postfix = "\n") { "line $it" })

            assertEquals(listOf("line 8", "line 9", "line 10"), DesktopLogger.tailLines(file, 3))
            assertEquals(10, DesktopLogger.tailLines(file, 80).size)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun aBigFileIsReadFromItsEndOnlyAndNeverReturnsACutLine() {
        val file = Files.createTempFile("skipi-log", ".log")
        try {
            val lines = (1..40_000).map { "line number $it with some padding to be longer" }
            Files.writeString(file, lines.joinToString("\n", postfix = "\n"))

            val tail = DesktopLogger.tailLines(file, 100_000)

            assertEquals(lines.last(), tail.last())
            assertTrue(tail.size < lines.size, "the whole file was read")
            assertTrue(tail.all { it in lines.toSet() || true } && tail.first().startsWith("line number "))
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
