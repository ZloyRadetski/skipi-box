// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

/** An ARGB tray icon, one pixel per Int as in a BufferedImage. */
internal class DesktopTrayPixmap(val width: Int, val height: Int, val argb: IntArray) {
    override fun equals(other: Any?): Boolean =
        other is DesktopTrayPixmap && width == other.width && height == other.height && argb.contentEquals(other.argb)

    override fun hashCode(): Int = 31 * (31 * width + height) + argb.contentHashCode()
}

/** The StatusNotifierItem wire format: four bytes per pixel, alpha first, network byte order. */
internal fun trayPixmapBytes(argb: IntArray): ByteArray {
    val bytes = ByteArray(argb.size * 4)
    argb.forEachIndexed { index, pixel ->
        bytes[index * 4] = (pixel ushr 24).toByte()
        bytes[index * 4 + 1] = (pixel ushr 16).toByte()
        bytes[index * 4 + 2] = (pixel ushr 8).toByte()
        bytes[index * 4 + 3] = pixel.toByte()
    }
    return bytes
}
