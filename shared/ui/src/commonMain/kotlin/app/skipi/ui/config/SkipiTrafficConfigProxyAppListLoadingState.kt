// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.proxy_app_list_loading
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared rendering for the host-controlled minimum-visible package-loading overlay. */
@Composable
fun SkipiTrafficConfigProxyAppListLoadingState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(MiuixTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InfiniteProgressIndicator(
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            size = 28.dp,
            strokeWidth = 2.dp,
            orbitingDotSize = 2.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(Res.string.proxy_app_list_loading),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
