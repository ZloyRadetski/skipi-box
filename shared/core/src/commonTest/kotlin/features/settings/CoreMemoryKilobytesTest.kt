// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreMemoryKilobytesTest {
    @Test
    fun showsTheGoHeapTheCoreReports() {
        val raw = """
            {
                "allocBytes": 17825792,
                "allocMb": "17.00 MB",
                "totalAlloc": 25165824,
                "sysBytes": 33554432,
                "sysMb": "32.00 MB",
                "heapInuse": 18874368,
                "heapIdle": 14680064,
                "heapReleased": 12582912,
                "numGoroutines": 24,
                "numGc": 12
            }
        """.trimIndent()

        assertEquals(17_408L, coreMemoryKilobytes(raw))
    }

    @Test
    fun givesNoFigureWhenTheCoreHasNone() {
        assertNull(coreMemoryKilobytes(""))
        assertNull(coreMemoryKilobytes("   "))
        assertNull(coreMemoryKilobytes("{}"))
        assertNull(coreMemoryKilobytes("""{"allocBytes": 0}"""))
        assertNull(coreMemoryKilobytes("not json"))
    }
}
