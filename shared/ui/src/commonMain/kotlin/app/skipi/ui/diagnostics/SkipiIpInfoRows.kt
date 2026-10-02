// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.skipi.ui.text.themedFontWeight

@Composable
fun SkipiIpAddressRow(label: String, ip: String, copyDescription: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCopy).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = themedFontWeight(FontWeight.SemiBold), color = MiuixTheme.colorScheme.primary)
            Spacer(Modifier.size(2.dp))
            Text(ip, fontSize = if (ip.length > 20) 17.sp else 22.sp, fontWeight = themedFontWeight(FontWeight.Bold), color = MiuixTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MiuixTheme.colorScheme.primary.copy(alpha = .1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MiuixIcons.Copy, contentDescription = copyDescription, modifier = Modifier.size(16.dp), tint = MiuixTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SkipiIpInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(16.dp))
        Text(value, fontSize = 14.sp, fontWeight = themedFontWeight(FontWeight.Medium), color = MiuixTheme.colorScheme.onSurface)
    }
}
