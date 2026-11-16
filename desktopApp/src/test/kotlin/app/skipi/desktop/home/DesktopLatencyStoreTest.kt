// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLatencyStoreTest {
    @Test
    fun resultsSurviveARestart() {
        val dir = Files.createTempDirectory("latency")
        val saved = mapOf(
            1 to DesktopServerLatencyResult.Success(42L),
            2 to DesktopServerLatencyResult.Timeout,
            3 to DesktopServerLatencyResult.Error("no route"),
        )
        assertTrue(DesktopLatencyStore(dir).save(saved))
        assertEquals(saved, DesktopLatencyStore(dir).load())
    }

    @Test
    fun missingOrDamagedFileLoadsAsEmptyOrSkipsBadLines() {
        val dir = Files.createTempDirectory("latency")
        assertEquals(emptyMap(), DesktopLatencyStore(dir).load())
        Files.write(dir.resolve("latency.txt"), listOf("x\tok\t5", "7\tok\tabc", "8\tok\t9"))
        assertEquals(mapOf(8 to DesktopServerLatencyResult.Success(9L)), DesktopLatencyStore(dir).load())
    }
}
