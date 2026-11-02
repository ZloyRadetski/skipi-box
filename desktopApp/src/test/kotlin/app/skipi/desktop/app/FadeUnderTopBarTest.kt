// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.skipi.ui.layout.fadeUnderTopBar
import app.skipi.ui.layout.withTopBarGap
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every scrolling page sits under a transparent top bar: what scrolls under the bar must be hidden
 * instead of showing through the title, and fade in across the gap where the first item rests.
 */
class FadeUnderTopBarTest {
    private val barHeight = 56.dp

    private fun render(scrollPx: Int): org.jetbrains.skia.Bitmap {
        val scene = ImageComposeScene(width = 100, height = 300, density = Density(1f)) {
            val state = rememberLazyListState()
            val scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = state,
                    modifier = Modifier.fadeUnderTopBar(barHeight).fillMaxSize(),
                    contentPadding = PaddingValues(top = barHeight).withTopBarGap(),
                ) {
                    items(20) { Box(Modifier.fillMaxWidth().height(40.dp).background(Color.Red)) }
                }
            }
            if (scrollPx > 0) androidx.compose.runtime.LaunchedEffect(Unit) { state.scrollBy(scrollPx.toFloat()) }
        }
        return try {
            scene.render()
            scene.render(100_000_000L)
            org.jetbrains.skia.Bitmap().also { bitmap ->
                val image = scene.render(200_000_000L)
                bitmap.allocN32Pixels(100, 300)
                image.readPixels(bitmap)
            }
        } finally {
            scene.close()
        }
    }

    private fun alphaAt(bitmap: org.jetbrains.skia.Bitmap, y: Int): Int = (bitmap.getColor(50, y) ushr 24) and 0xFF

    @Test
    fun atRestTheFirstItemIsFullyVisibleBelowTheGap() {
        val bitmap = render(scrollPx = 0)

        assertEquals(0, alphaAt(bitmap, 10), "nothing is drawn where the bar is")
        assertEquals(255, alphaAt(bitmap, 56 + 8 + 4), "the first item rests below the gap")
    }

    @Test
    fun whatScrollsUnderTheBarIsHiddenAndFadesInAcrossTheGap() = runBlocking {
        val bitmap = render(scrollPx = 100)

        assertEquals(0, alphaAt(bitmap, 10), "scrolled content must not show through the bar")
        assertEquals(0, alphaAt(bitmap, 55), "still hidden right at the bar's edge")
        val inGap = alphaAt(bitmap, 56 + 4)
        assertTrue(inGap in 1..254, "the gap is a gradient, got alpha $inGap")
        assertEquals(255, alphaAt(bitmap, 56 + 8 + 20), "fully visible below the gap")
    }
}
