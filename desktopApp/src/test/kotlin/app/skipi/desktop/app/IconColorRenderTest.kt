// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.SkipiContentIcon
import app.skipi.ui.components.SkipiIcon
import app.skipi.ui.settings.ProvideIconColor
import kotlin.test.Test
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh

/** Draws real icons inside the library's buttons and looks at the pixels: the picked color must be what is on screen. */
class IconColorRenderTest {
    private val picked = 0xFFFF00FFL

    private fun render(content: @androidx.compose.runtime.Composable () -> Unit): org.jetbrains.skia.Bitmap {
        val scene = ImageComposeScene(width = 200, height = 80, density = Density(1f)) {
            DesktopAppTheme { ProvideIconColor(picked) { content() } }
        }
        return try {
            scene.render()
            org.jetbrains.skia.Bitmap().also { bitmap ->
                bitmap.allocN32Pixels(200, 80)
                scene.render(100_000_000L).readPixels(bitmap)
            }
        } finally {
            scene.close()
        }
    }

    private fun magentaPixels(bitmap: org.jetbrains.skia.Bitmap, xRange: IntRange): Int {
        var count = 0
        for (x in xRange) for (y in 0 until bitmap.height) {
            val c = bitmap.getColor(x, y)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (r > 200 && b > 200 && g < 90) count++
        }
        return count
    }

    @Test
    fun theThreeDotsAndTheRefreshInsideButtonsAreDrawnInThePickedColor() {
        val bitmap = render {
            Row {
                IconButton(onClick = {}, modifier = Modifier.size(80.dp)) {
                    SkipiContentIcon(Icons.Outlined.MoreVert, contentDescription = null)
                }
                IconButton(onClick = {}, modifier = Modifier.size(80.dp)) {
                    SkipiIcon(MiuixIcons.Refresh, contentDescription = null)
                }
            }
        }

        assertTrue(magentaPixels(bitmap, 0..79) > 10, "the three dots must be drawn in the picked color")
        assertTrue(magentaPixels(bitmap, 80..159) > 10, "the refresh must be drawn in the picked color")
    }
}
