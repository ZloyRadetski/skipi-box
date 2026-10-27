// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.skipi.ui.server.editor.SkipiProxyServerQrCodeDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

/** Desktop QR image adapter; the shared dialog owns the presentation. */
@Composable
internal fun DesktopProxyServerQrCodeDialog(
    title: String,
    text: String,
    generationFailedMessage: String,
    onDismissRequest: () -> Unit,
) {
    val qrCode = remember(text) {
        runCatching { generateDesktopQrCodeImageBitmap(text, QrCodeBitmapSizePx) }.getOrNull()
    }
    SkipiProxyServerQrCodeDialog(
        title = title,
        qrCode = qrCode,
        generationFailedMessage = generationFailedMessage,
        onDismissRequest = onDismissRequest,
    )
}

internal fun generateDesktopQrCodeImageBitmap(
    text: String,
    sizePx: Int,
): ImageBitmap {
    require(text.isNotBlank()) { "QR code text is required" }
    require(sizePx > 0) { "QR code size must be positive" }
    val matrix = MultiFormatWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 1,
        ),
    )
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val offset = y * sizePx
        for (x in 0 until sizePx) {
            pixels[offset + x] = if (matrix[x, y]) Black else White
        }
    }
    val image = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, sizePx, sizePx, pixels, 0, sizePx)
    return image.toComposeImageBitmap()
}

private const val QrCodeBitmapSizePx = 768
private const val Black = -0x1000000
private const val White = -0x1
