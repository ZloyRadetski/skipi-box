// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppPullToRefresh as SharedAppPullToRefresh
import app.skipi.ui.components.AppPullToRefreshState as SharedAppPullToRefreshState
import app.skipi.ui.components.AppRefreshState as SharedAppRefreshState
import app.skipi.ui.components.rememberAppPullToRefreshState as sharedRememberAppPullToRefreshState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.text.themedFontWeight

typealias AppRefreshState = SharedAppRefreshState
typealias AppPullToRefreshState = SharedAppPullToRefreshState

@Composable
fun rememberAppPullToRefreshState(): AppPullToRefreshState =
    sharedRememberAppPullToRefreshState()

@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    pullToRefreshState: AppPullToRefreshState = rememberAppPullToRefreshState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    topAppBarScrollBehavior: ScrollBehavior? = null,
    color: Color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    circleSize: Dp = 22.dp,
    refreshTexts: List<String> = emptyList(),
    refreshTextStyle: TextStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = themedFontWeight(FontWeight.Medium),
        color = color,
    ),
    content: @Composable () -> Unit,
) {
    SharedAppPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        hapticFeedback = LocalHapticFeedback.current,
        modifier = modifier,
        pullToRefreshState = pullToRefreshState,
        contentPadding = contentPadding,
        topAppBarScrollBehavior = topAppBarScrollBehavior,
        color = color,
        circleSize = circleSize,
        refreshTexts = refreshTexts,
        refreshTextStyle = refreshTextStyle,
        content = content,
    )
}
