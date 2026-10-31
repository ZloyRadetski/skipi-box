// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Desktop counterpart of Android's custom background photo file (JPEG, longest side <= 2560). */
class DesktopBackgroundPhotoStoreTest {
    private fun withDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("skipi-background")
        try { block(directory) } finally { directory.toFile().deleteRecursively() }
    }

    private fun writePng(path: Path, width: Int, height: Int) {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", path.toFile())
    }

    @Test
    fun savingStoresAJpegAndReportsItExists() = withDirectory { directory ->
        val source = directory.resolve("source.png").also { writePng(it, 400, 200) }
        val store = DesktopBackgroundPhotoStore(directory.resolve("data"))

        assertFalse(store.exists())
        assertTrue(store.save(source))

        assertTrue(store.exists())
        val stored = assertNotNull(ImageIO.read(store.file().toFile()))
        assertEquals(400, stored.width)
        assertEquals(200, stored.height)
    }

    @Test
    fun largeImagesAreScaledDownToTheAndroidLimit() = withDirectory { directory ->
        val source = directory.resolve("big.png").also { writePng(it, 5200, 1300) }
        val store = DesktopBackgroundPhotoStore(directory.resolve("data"))

        assertTrue(store.save(source))

        val stored = assertNotNull(ImageIO.read(store.file().toFile()))
        assertTrue(stored.width <= 2560 && stored.height <= 2560)
        assertEquals(4, stored.width / stored.height)
    }

    @Test
    fun unreadableSourceKeepsThePreviousPhoto() = withDirectory { directory ->
        val good = directory.resolve("good.png").also { writePng(it, 40, 40) }
        val broken = directory.resolve("broken.png").also { it.writeText("not an image") }
        val store = DesktopBackgroundPhotoStore(directory.resolve("data"))
        assertTrue(store.save(good))
        val before = Files.readAllBytes(store.file())

        assertFalse(store.save(broken))

        assertTrue(store.exists())
        assertTrue(before.contentEquals(Files.readAllBytes(store.file())))
    }

    @Test
    fun clearRemovesThePhotoAndSucceedsWhenAbsent() = withDirectory { directory ->
        val source = directory.resolve("source.png").also { writePng(it, 40, 40) }
        val store = DesktopBackgroundPhotoStore(directory.resolve("data"))
        assertTrue(store.clear())
        store.save(source)

        assertTrue(store.clear())

        assertFalse(store.file().exists())
        assertFalse(store.exists())
    }
}
