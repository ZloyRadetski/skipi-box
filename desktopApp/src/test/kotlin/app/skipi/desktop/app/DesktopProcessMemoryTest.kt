// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopProcessMemoryTest {
    @Test
    fun readsResidentSetFromLinuxProcStatus() {
        val status = "Name:\tskipi\nVmPeak:\t 900000 kB\nVmRSS:\t  123456 kB\nThreads:\t12\n"
        assertEquals(123_456L, parseLinuxVmRssKb(status))
        assertNull(parseLinuxVmRssKb("Name:\tskipi\n"))
    }

    @Test
    fun readsResidentSetFromPsOutput() {
        assertEquals(204_800L, parsePsRssKb(" 204800\n"))
        assertNull(parsePsRssKb(""))
        assertNull(parsePsRssKb("error"))
    }

    @Test
    fun measuresTheRunningProcessOnThisMachine() {
        val kb = currentProcessResidentKb()
        assertTrue(kb != null && kb > 10_000L, "resident set of the test JVM should be tens of MB, was $kb")
    }
}
