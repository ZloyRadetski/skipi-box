// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.R
import app.skipi.ui.home.SkipiProxyServerFlagBadge
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Android theme/resource adapter for the shared proxy-server flag badge. */
@Composable
internal fun CountryFlagBadge(
    flag: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shapeRadius: Dp = 8.dp,
) {
    SkipiProxyServerFlagBadge(
        flag = flag,
        fallbackPainter = painterResource(R.drawable.ic_globe),
        containerColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        fallbackTint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.85f),
        modifier = modifier,
        size = size,
        shapeRadius = shapeRadius,
    )
}
