// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.graphics.toAwtImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopProxyServerQrCodeDialogTest {
    @Test
    fun generatedQrImageKeepsUtf8UrlTextAtAndroidBitmapSize() {
        val payload = "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@server.example:443?security=tls#Узел_日本"

        val qrCode = generateDesktopQrCodeImageBitmap(payload, sizePx = 768)

        assertEquals(768, qrCode.width)
        assertEquals(768, qrCode.height)

        val image = qrCode.toAwtImage()
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val decoded = MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels))),
            mapOf(DecodeHintType.CHARACTER_SET to "UTF-8"),
        )

        assertEquals(payload, decoded.text)
    }
}
