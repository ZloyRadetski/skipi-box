// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.R
import app.skipi.ui.server.editor.SkipiProxyServerQrCodeDialog
import utils.generateQrCodeImageBitmap

/** Android adapter for QR image generation; the shared dialog owns presentation. */
@Composable
internal fun ProxyServerQrCodeDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
) {
    val qrCode = remember(text) { runCatching { generateQrCodeImageBitmap(text, QrCodeBitmapSizePx) }.getOrNull() }
    SkipiProxyServerQrCodeDialog(
        title = title,
        qrCode = qrCode,
        generationFailedMessage = stringResource(R.string.proxy_server_qr_generate_failed),
        onDismissRequest = onDismissRequest,
    )
}

private const val QrCodeBitmapSizePx = 768
