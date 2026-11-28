// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.background

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BitmapBlurTest {
    @Test
    fun uniformImageStaysUniform() {
        val px = IntArray(25) { 0xFF336699.toInt() }
        assertContentEquals(px, boxBlurArgb(px, 5, 5, 2))
    }

    @Test
    fun zeroRadiusKeepsPixels() {
        val px = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertContentEquals(px, boxBlurArgb(px, 2, 1, 0))
    }

    @Test
    fun edgeIsSpreadAcrossNeighbours() {
        val px = IntArray(9) { if (it % 3 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val out = boxBlurArgb(px, 3, 3, 1)
        val mid = out[4] and 0xFF
        assertTrue(mid in 1..254, "middle pixel should be grey, was $mid")
        assertEquals(0xFF, out[4] ushr 24)
    }
}
