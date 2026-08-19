// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CountryFlagBadge(
    flag: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shapeRadius: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(shapeRadius)
    val containerColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f)

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
                painter = painterResource(R.drawable.ic_globe),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.85f),
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}
