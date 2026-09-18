// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class ResourceFileDecompressorTest {

    @Test
    fun testDecompressXzStreamWithNameHint() {
        val originalText = "Test content for XZ decompression with repeated patterns. " +
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(20)
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        val compressedBytes = compressWithXz(originalBytes)

        val input = ByteArrayInputStream(compressedBytes)
        val decompressedBytes = openDecompressedStream(input, "geosite.dat.xz").use { it.readBytes() }

        assertArrayEquals(originalBytes, decompressedBytes)
        assertEquals(originalText, String(decompressedBytes, Charsets.UTF_8))
    }

    @Test
    fun testDecompressXzStreamByMagicBytesWithoutNameHint() {
        val originalText = "Magic bytes detection test: " + "repeat-data-block-".repeat(30)
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        val compressedBytes = compressWithXz(originalBytes)

        val input = ByteArrayInputStream(compressedBytes)
        val decompressedBytes = openDecompressedStream(input, "geosite.dat").use { it.readBytes() }

        assertArrayEquals(originalBytes, decompressedBytes)
    }

    @Test
    fun testPassThroughUncompressedStream() {
        val originalText = "Plain uncompressed text stream that should not be touched."
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val input = ByteArrayInputStream(originalBytes)
        val resultBytes = openDecompressedStream(input, "geosite.dat").use { it.readBytes() }

        assertArrayEquals(originalBytes, resultBytes)
    }

    @Test
    fun testIsXzStreamDetection() {
        val plainBytes = "Not an XZ file".toByteArray(Charsets.UTF_8)
        assertFalse(isXzStream(ByteArrayInputStream(plainBytes).buffered()))

        val compressedBytes = compressWithXz("Some data".toByteArray(Charsets.UTF_8))
        assertTrue(isXzStream(ByteArrayInputStream(compressedBytes).buffered()))
    }

    @Test
    fun testRealBundledAssetDecompression() {
        val assetFile = File("src/main/assets/geo/roscomvpn/geosite.dat.xz")
        if (!assetFile.isFile) return // only runs when asset is present in project path

        val decompressed = assetFile.inputStream().use { input ->
            openDecompressedStream(input, assetFile.name).use { it.readBytes() }
        }
        assertTrue("Decompressed asset should not be empty", decompressed.isNotEmpty())
        assertEquals(67824, decompressed.size)
    }

    private fun compressWithXz(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        XZOutputStream(baos, LZMA2Options(6)).use { xzOut ->
            xzOut.write(data)
        }
        return baos.toByteArray()
    }
}
