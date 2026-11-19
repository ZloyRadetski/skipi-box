// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreMemoryKilobytesTest {
    @Test
    fun showsWhatTheGoRuntimeHoldsFromTheSystem() {
        // Live objects alone (allocBytes) leave out free heap not yet returned, goroutine stacks and
        // runtime metadata, so the figure is what the runtime took from the OS minus what it gave back.
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

        assertEquals(20_480L, coreMemoryKilobytes(raw))
    }

    @Test
    fun fallsBackToTheLiveHeapWhenTheRuntimeTotalIsMissing() {
        assertEquals(17_408L, coreMemoryKilobytes("""{"allocBytes": 17825792}"""))
        assertEquals(17_408L, coreMemoryKilobytes("""{"allocBytes": 17825792, "sysBytes": 1048576, "heapReleased": 1048576}"""))
    }

    @Test
    fun givesNoFigureWhenTheCoreHasNone() {
        assertNull(coreMemoryKilobytes(""))
        assertNull(coreMemoryKilobytes("   "))
        assertNull(coreMemoryKilobytes("{}"))
        assertNull(coreMemoryKilobytes("""{"allocBytes": 0}"""))
        assertNull(coreMemoryKilobytes("""{"allocBytes": 0, "sysBytes": 0}"""))
        assertNull(coreMemoryKilobytes("not json"))
    }
}
