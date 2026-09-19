// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.InputStream

private val XzHeaderMagic = byteArrayOf(
    0xFD.toByte(),
    0x37.toByte(),
    0x7A.toByte(),
    0x58.toByte(),
    0x5A.toByte(),
    0x00.toByte(),
)

/**
 * Checks if the given buffered input stream starts with XZ header magic bytes.
 */
internal fun isXzStream(bufferedStream: BufferedInputStream): Boolean {
    bufferedStream.mark(XzHeaderMagic.size)
    val header = ByteArray(XzHeaderMagic.size)
    var read = 0
    while (read < header.size) {
        val count = bufferedStream.read(header, read, header.size - read)
        if (count == -1) break
        read += count
    }
    bufferedStream.reset()
    return read == header.size && header.contentEquals(XzHeaderMagic)
}

/**
 * Wraps [inputStream] in an [XZInputStream] if it has an `.xz` name hint or starts with
 * XZ header magic bytes. Otherwise returns the stream unchanged.
 */
internal fun openDecompressedStream(
    inputStream: InputStream,
    nameHint: String = "",
): InputStream {
    val buffered = (inputStream as? BufferedInputStream) ?: BufferedInputStream(inputStream)
    val isXz = nameHint.endsWith(".xz", ignoreCase = true) || isXzStream(buffered)
    return if (isXz) {
        XZInputStream(buffered)
    } else {
        buffered
    }
}
