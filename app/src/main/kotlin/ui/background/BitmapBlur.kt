// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.background

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

private const val SmallBlurRadiusPx = 3
private const val MinSmallSide = 48

/**
 * Blur for devices without `Modifier.blur` (API < 31): the bitmap is scaled down so that [radiusPx] becomes a few
 * pixels, box-blurred there (cheap) and scaled back up with filtering. Call it off the main thread.
 * The result has the size of the source, so it is drawn exactly like the original photo.
 */
fun blurBitmapFallback(source: Bitmap, radiusPx: Float): Bitmap {
    if (radiusPx <= 0.5f) return source
    val factor = (SmallBlurRadiusPx / radiusPx).coerceIn(0.02f, 1f)
    val smallW = max((source.width * factor).roundToInt(), minOf(MinSmallSide, source.width))
    val smallH = max((source.height * factor).roundToInt(), minOf(MinSmallSide, source.height))
    val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
    val pixels = IntArray(smallW * smallH)
    small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
    val blurred = boxBlurArgb(boxBlurArgb(pixels, smallW, smallH, SmallBlurRadiusPx), smallW, smallH, SmallBlurRadiusPx)
    val out = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
    out.setPixels(blurred, 0, smallW, 0, 0, smallW, smallH)
    if (small !== source) small.recycle()
    val result = Bitmap.createScaledBitmap(out, source.width, source.height, true)
    if (result !== out) out.recycle()
    return result
}

fun blurredImageBitmap(source: Bitmap, radiusPx: Float): ImageBitmap = blurBitmapFallback(source, radiusPx).asImageBitmap()

/** Separable box blur of ARGB pixels with edge clamping; one horizontal and one vertical pass. */
fun boxBlurArgb(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    if (radius <= 0 || width == 0 || height == 0) return pixels.copyOf()
    val tmp = IntArray(pixels.size)
    boxPass(pixels, tmp, width, height, radius, horizontal = true)
    val out = IntArray(pixels.size)
    boxPass(tmp, out, width, height, radius, horizontal = false)
    return out
}

private fun boxPass(src: IntArray, dst: IntArray, width: Int, height: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val len = if (horizontal) width else height
    val window = 2 * r + 1
    for (line in 0 until lines) {
        fun idx(i: Int) = if (horizontal) line * width + i else i * width + line
        var a = 0; var rr = 0; var g = 0; var b = 0
        for (k in -r..r) {
            val p = src[idx(k.coerceIn(0, len - 1))]
            a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        for (i in 0 until len) {
            dst[idx(i)] = ((a / window) shl 24) or ((rr / window) shl 16) or ((g / window) shl 8) or (b / window)
            val add = src[idx((i + r + 1).coerceIn(0, len - 1))]
            val rem = src[idx((i - r).coerceIn(0, len - 1))]
            a += (add ushr 24) - (rem ushr 24)
            rr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
            b += (add and 0xFF) - (rem and 0xFF)
        }
    }
}
