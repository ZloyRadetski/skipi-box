// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class XrayTrafficStatsTest {
    @Test
    fun sizesSwitchUnitAtMultiplesOf1024() = withUsLocale {
        assertEquals("0.0 B", 0L.toTrafficSizeString())
        assertEquals("1000.0 B", 1000L.toTrafficSizeString())
        assertEquals("1023.0 B", 1023L.toTrafficSizeString())
        assertEquals("1.0 KB", 1024L.toTrafficSizeString())
        assertEquals("1000.0 KB", (1000L * 1024).toTrafficSizeString())
        assertEquals("1.0 MB", (1024L * 1024).toTrafficSizeString())
        assertEquals("1.0 GB", (1024L * 1024 * 1024).toTrafficSizeString())
        assertEquals("1.5 KB/s", 1536L.toTrafficSpeedString())
    }

    private inline fun withUsLocale(block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
