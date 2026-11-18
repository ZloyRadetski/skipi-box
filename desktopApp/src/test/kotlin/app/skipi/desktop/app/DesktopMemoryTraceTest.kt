// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMemoryTraceTest {
    private val file = Files.createTempDirectory("skipi-trace-").resolve("memory-trace.log").toFile()

    @Test
    fun aSampleWritesEverySectionUnderItsNameWithTheTime() {
        val trace = DesktopMemoryTrace(file, listOf("jvm" to { "heap=10" }, "process" to { "private=20" }), now = { "T1" })

        trace.sample()

        val text = file.readText()
        assertTrue(text.contains("T1"), text)
        assertTrue(text.indexOf("[jvm]") in 0 until text.indexOf("[process]"), text)
        assertTrue(text.contains("heap=10") && text.contains("private=20"), text)
    }

    @Test
    fun aSectionThatFailsIsReportedAndTheOthersStillRun() {
        val trace = DesktopMemoryTrace(
            file,
            listOf("broken" to { error("no access") }, "jvm" to { "heap=10" }),
            now = { "T1" },
        )

        trace.sample()

        val text = file.readText()
        assertTrue(text.contains("[broken]") && text.contains("no access"), text)
        assertTrue(text.contains("heap=10"), text)
    }

    @Test
    fun samplesAreAppendedNotOverwritten() {
        var tick = 0
        val trace = DesktopMemoryTrace(file, listOf("jvm" to { "heap=10" }), now = { "T${++tick}" })

        trace.sample()
        trace.sample()

        val text = file.readText()
        assertTrue(text.contains("T1") && text.contains("T2"), text)
    }

    @Test
    fun aFileOverTheLimitStartsAgainInsteadOfGrowingWithoutEnd() {
        var tick = 0
        val trace = DesktopMemoryTrace(
            file,
            listOf("jvm" to { "x".repeat(200) }),
            now = { "T${++tick}" },
            maxFileBytes = 500,
        )

        repeat(10) { trace.sample() }

        assertTrue(file.length() <= 500 + 400, "size ${file.length()}")
        assertEquals(true, file.readText().contains("T10"))
    }
}
