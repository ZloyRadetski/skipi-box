// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/** Shared country/emoji badge. Hosts supply their own fallback asset and colors. */
@Composable
fun SkipiProxyServerFlagBadge(
    flag: String?,
    fallbackPainter: Painter,
    containerColor: Color,
    fallbackTint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shapeRadius: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(shapeRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (flag != null) {
            Text(
                text = flag,
                fontSize = (size.value * 0.58f).sp,
                lineHeight = (size.value * 0.58f).sp,
            )
        } else {
            Icon(
                painter = fallbackPainter,
                contentDescription = null,
                tint = fallbackTint,
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}

/** Shared compact protocol tag; a host resolves its protocol color. */
@Composable
fun SkipiProxyProtocolChip(
    text: String,
    chipColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selected: Boolean = false,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(chipColor.copy(alpha = if (selected) 0.22f else 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = fontWeight,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shared compact transport tag; a host resolves the selected-theme colors. */
@Composable
fun SkipiProxyTransportChip(
    text: String,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = fontWeight,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
