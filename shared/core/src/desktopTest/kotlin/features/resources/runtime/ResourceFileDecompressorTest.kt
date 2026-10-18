// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

class ResourceFileDecompressorTest {

    @Test
    fun decompressesAnXzStreamWhenTheResolvedAssetNameEndsInXz() {
        val original = "XZ name hint test: ".repeat(25).encodeToByteArray()
        val compressed = compressWithXz(original)

        val result = openDecompressedStream(ByteArrayInputStream(compressed), "geoip.dat.xz").use { it.readBytes() }

        assertContentEquals(original, result)
    }

    @Test
    fun detectsXzMagicWhenTheResolvedAssetNameHasNoXzSuffix() {
        val original = "XZ magic test: ".repeat(30).encodeToByteArray()
        val compressed = compressWithXz(original)

        val result = openDecompressedStream(ByteArrayInputStream(compressed), "geoip.dat").use { it.readBytes() }

        assertContentEquals(original, result)
    }

    @Test
    fun passesOrdinaryUncompressedBytesThroughUnchanged() {
        val original = "Plain resource file bytes".encodeToByteArray()

        val result = openDecompressedStream(ByteArrayInputStream(original), "geoip.dat").use { it.readBytes() }

        assertContentEquals(original, result)
    }

    @Test
    fun xzHeaderDetectionRequiresTheCompleteMagicPrefix() {
        val truncatedXzHeader = byteArrayOf(
            0xFD.toByte(),
            0x37,
            0x7A,
            0x58,
            0x5A,
        )
        val compressedBytes = compressWithXz("encoded".encodeToByteArray())

        assertFalse(isXzStream(ByteArrayInputStream(truncatedXzHeader).buffered()))
        assertTrue(isXzStream(ByteArrayInputStream(compressedBytes).buffered()))
    }

    @Test
    fun recognizesXzSuffixWithoutCaseSensitivity() {
        val original = "case-insensitive suffix".encodeToByteArray()
        val compressed = compressWithXz(original)

        val result = openDecompressedStream(ByteArrayInputStream(compressed), "geoip.dat.XZ").use { it.readBytes() }

        assertContentEquals(original, result)
    }

    private fun compressWithXz(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        XZOutputStream(output, LZMA2Options(6)).use { it.write(data) }
        return output.toByteArray()
    }
}
