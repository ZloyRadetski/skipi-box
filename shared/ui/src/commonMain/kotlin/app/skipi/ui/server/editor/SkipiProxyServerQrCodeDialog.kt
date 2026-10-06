// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** QR presentation shared across hosts; QR generation and image access stay with each platform. */
@Composable
fun SkipiProxyServerQrCodeDialog(
    title: String,
    qrCode: ImageBitmap?,
    generationFailedMessage: String,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface, contentColor = SkipiTheme.colors.onSurface),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        modifier = Modifier.fillMaxWidth(),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = themedFontWeight(FontWeight.Medium),
                        color = MiuixTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    if (qrCode == null) {
                        Text(generationFailedMessage, modifier = Modifier.fillMaxWidth(), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.Center)
                    } else {
                        Image(
                            bitmap = qrCode,
                            contentDescription = title,
                            modifier = Modifier.size(288.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
