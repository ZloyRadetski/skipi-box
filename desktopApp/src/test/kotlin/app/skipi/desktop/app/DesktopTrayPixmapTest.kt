// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopTrayPixmapTest {
    /** Orange in the left half, nothing in the right half. */
    private class HalfOrange : Painter() {
        override val intrinsicSize = Size(10f, 10f)

        override fun DrawScope.onDraw() {
            drawRect(Color(0xFFFF8000), size = Size(size.width / 2, size.height))
        }
    }

    private fun pixel(pixmap: DesktopTrayPixmap, x: Int, y: Int) = pixmap.argb[y * pixmap.width + x]

    @Test
    fun theLogoIsRenderedAtEachRequestedSizeInColourWhileConnected() {
        val pixmaps = trayPixmaps(HalfOrange(), connected = true, density = Density(1f), sizes = listOf(16, 32))

        assertEquals(listOf(16 to 16, 32 to 32), pixmaps.map { it.width to it.height })
        pixmaps.forEach { pixmap ->
            assertEquals(pixmap.width * pixmap.height, pixmap.argb.size)
            assertEquals(0xFFFF8000.toInt(), pixel(pixmap, 2, pixmap.height / 2), "left half keeps its colour")
            assertEquals(0, pixel(pixmap, pixmap.width - 2, pixmap.height / 2) ushr 24, "right half stays transparent")
        }
    }

    @Test
    fun theLogoIsGreyWhileDisconnectedButKeepsItsShape() {
        val pixmap = trayPixmaps(HalfOrange(), connected = false, density = Density(1f), sizes = listOf(16)).single()

        val left = pixel(pixmap, 2, 8)
        val red = left shr 16 and 0xFF
        val green = left shr 8 and 0xFF
        val blue = left and 0xFF
        assertEquals(0xFF, left ushr 24, "still opaque")
        assertTrue(red == green && green == blue, "grey, not orange: $red $green $blue")
        assertEquals(0, pixel(pixmap, 14, 8) ushr 24, "the transparent half stays transparent")
    }
}
