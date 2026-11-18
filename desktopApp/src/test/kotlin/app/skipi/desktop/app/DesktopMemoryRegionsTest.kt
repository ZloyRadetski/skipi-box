// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopMemoryRegionsTest {
    private val mib = 1_048_576L

    @Test
    fun committedBytesAreTotalledPerKindAndFreeOrReservedOnesAreIgnored() {
        val text = summarizeMemoryRegions(
            listOf(
                MemoryRegion(0x1000, 100 * mib, committed = true, kind = "private", protect = 0x04),
                MemoryRegion(0x9000, 50 * mib, committed = true, kind = "private", protect = 0x04),
                MemoryRegion(0xA000, 30 * mib, committed = true, kind = "image", protect = 0x20),
                MemoryRegion(0xB000, 900 * mib, committed = false, kind = "private", protect = 0x01),
            ),
        )

        assertTrue(text.contains("private committed=150.0 MB"), text)
        assertTrue(text.contains("image committed=30.0 MB"), text)
        assertTrue(!text.contains("900.0"), text)
    }

    @Test
    fun theLargestCommittedRegionsAreListedBiggestFirstWithTheirAddress() {
        val regions = (1..20).map { MemoryRegion(it * 0x10000L, it * mib, committed = true, kind = "private", protect = 0x04) }

        val text = summarizeMemoryRegions(regions, topCount = 3)

        val first = text.indexOf("20.0 MB")
        val second = text.indexOf("19.0 MB")
        assertTrue(first in 0 until second, text)
        assertTrue(!text.contains("17.0 MB"), text)
        assertTrue(text.contains("0x140000"), text)
    }

    @Test
    fun writeCombinedRegionsAreTotalledSeparatelyBecauseTheyAreGpuDriverMemory() {
        val text = summarizeMemoryRegions(
            listOf(
                MemoryRegion(0x1000, 32 * mib, committed = true, kind = "private", protect = 0x404),
                MemoryRegion(0x9000, 32 * mib, committed = true, kind = "private", protect = 0x404),
                MemoryRegion(0xA000, 100 * mib, committed = true, kind = "private", protect = 0x04),
            ),
        )

        assertTrue(text.contains("write-combined committed=64.0 MB regions=2"), text)
    }

    @Test
    fun privateRegionsAreCountedBySizeBucketsToSeeManySmallBlocksApartFromFewBigOnes() {
        val kib = 1_024L
        val regions = listOf(
            MemoryRegion(0x1000, 64 * kib, committed = true, kind = "private", protect = 0x04),
            MemoryRegion(0x2000, 128 * kib, committed = true, kind = "private", protect = 0x04),
            MemoryRegion(0x3000, 2 * mib, committed = true, kind = "private", protect = 0x04),
            MemoryRegion(0x4000, 40 * mib, committed = true, kind = "private", protect = 0x04),
            MemoryRegion(0x5000, 700 * mib, committed = true, kind = "image", protect = 0x20),
        )

        val text = summarizeMemoryRegions(regions)

        assertTrue(text.contains("private by size: <256 KB: 2 regions 0.2 MB"), text)
        assertTrue(text.contains("1-16 MB: 1 regions 2.0 MB"), text)
        assertTrue(text.contains(">=16 MB: 1 regions 40.0 MB"), text)
    }
}
