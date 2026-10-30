// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.skipi.ui.home.scrollBleed
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrollBleedLayoutTest {
    @Test
    fun regionGrowsOverThePaddingOfItsParentWithoutMovingTheParent() {
        val density = Density(1f)
        val scene = ImageComposeScene(width = 400, height = 800, density = density)
        var parentBounds: androidx.compose.ui.geometry.Rect? = null
        var childBounds: androidx.compose.ui.geometry.Rect? = null
        try {
            scene.setContent {
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { parentBounds = it.boundsInRoot() }
                            .scrollBleed(horizontal = 16.dp, top = 12.dp, bottom = 20.dp),
                    ) {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { childBounds = it.boundsInRoot() })
                    }
                }
            }
            scene.render(0L)
            scene.render(16_000_000L)
        } finally {
            scene.close()
        }
        val parent = requireNotNull(parentBounds)
        val child = requireNotNull(childBounds)
        // The reported box keeps its padded place; the content reaches the screen edges and grows up/down.
        assertEquals(16f, parent.left)
        assertEquals(0f, child.left)
        assertEquals(400f, child.right)
        assertEquals(parent.top - 12f, child.top)
        assertEquals(parent.bottom + 20f, child.bottom)
    }
}
