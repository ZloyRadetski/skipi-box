// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop.app

import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * jpackage falls back to the default (Compose/Kotlin) icon when a platform has no icon file, so every
 * installer platform must ship the SKIPI icon. macOS needs a real .icns container.
 */
class DesktopPackagingIconsTest {
    private val icons = File("packaging/icons")

    private fun icnsChunks(bytes: ByteArray): Map<String, Int> {
        val buffer = ByteBuffer.wrap(bytes)
        val magic = ByteArray(4).also(buffer::get).decodeToString()
        assertEquals("icns", magic)
        assertEquals(bytes.size, buffer.int, "the icns header length must equal the file size")
        val chunks = linkedMapOf<String, Int>()
        while (buffer.hasRemaining()) {
            val type = ByteArray(4).also(buffer::get).decodeToString()
            val length = buffer.int
            chunks[type] = length - 8
            buffer.position(buffer.position() + length - 8)
        }
        return chunks
    }

    @Test
    fun theMacIconIsAValidIcnsWithPngImagesFromSmallToRetina() {
        val file = File(icons, "skipi.icns")
        assertTrue(file.isFile, "packaging/icons/skipi.icns is missing")
        val chunks = icnsChunks(file.readBytes())
        // ic07 128, ic08 256, ic09 512, ic10 1024 (retina of 512): PNG-compressed images.
        listOf("ic07", "ic08", "ic09", "ic10").forEach { type ->
            assertTrue((chunks[type] ?: 0) > 100, "icns chunk $type is missing or empty")
        }
    }

    @Test
    fun windowsAndLinuxIconsStillExist() {
        assertTrue(File(icons, "skipi.ico").length() > 1000)
        assertTrue(File(icons, "skipi.png").length() > 1000)
    }
}
