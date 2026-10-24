// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.ProxyAppIconRequest
import features.proxy.app.model.name
import system.ANDROID_APP_ICON_SIZE_DP
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Android-only icon adapter; common rows receive this as their icon-content slot. */
@Composable
internal fun ProxyAppListNativeIcon(
    app: AppPackageEntry,
    enabled: Boolean,
    iconSizePx: Int,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) {
            MiuixTheme.colorScheme.primary.copy(alpha = if (app.system) 0.10f else 0.16f)
        } else {
            MiuixTheme.colorScheme.disabledOnSecondaryVariant.copy(alpha = 0.10f)
        },
        animationSpec = tween(180),
        label = "per-app-icon-background",
    )
    val appIconAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.42f,
        animationSpec = tween(180),
        label = "per-app-icon-alpha",
    )
    val appIconColorFilter = if (enabled) {
        null
    } else {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Box(
        modifier = Modifier
            .size(ANDROID_APP_ICON_SIZE_DP.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ProxyAppIconRequest(
                packageName = app.packageName,
                sizePx = iconSizePx,
            ),
            contentDescription = app.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appIconAlpha
                },
            contentScale = ContentScale.Fit,
            colorFilter = appIconColorFilter,
        )
    }
}
