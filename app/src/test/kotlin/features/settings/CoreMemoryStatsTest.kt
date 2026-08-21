// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CoreMemoryStatsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun coreMemoryStats_deserializes_valid_json() {
        val rawJson = """
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

        val stats = json.decodeFromString<CoreMemoryStats>(rawJson)
        assertNotNull(stats)
        assertEquals(17825792L, stats.allocBytes)
        assertEquals("17.00 MB", stats.allocMb)
        assertEquals(33554432L, stats.sysBytes)
        assertEquals("32.00 MB", stats.sysMb)
        assertEquals(24, stats.numGoroutines)
        assertEquals(12L, stats.numGc)

        val memoryKb = stats.allocBytes / 1024L
        assertEquals(17408L, memoryKb)
    }

    @Test
    fun coreMemoryStats_handles_empty_or_default_values() {
        val rawJson = "{}"
        val stats = json.decodeFromString<CoreMemoryStats>(rawJson)
        assertNotNull(stats)
        assertEquals(0L, stats.allocBytes)
        assertEquals("", stats.allocMb)
    }
}
