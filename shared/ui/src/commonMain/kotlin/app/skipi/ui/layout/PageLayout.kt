// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.skipi.ui.home.verticalEdgeFade
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Gap between a transparent top bar and the first item at rest; scrolled items fade in across it. */
val TopBarFadeGap: Dp = 8.dp

/**
 * Scrolling content under a transparent top bar of [barHeight]: what scrolls under the bar is hidden
 * instead of showing through its title, and fades in across [gap] below it. Pair it with
 * [withTopBarGap] on the list's content padding, so the first item rests where it is fully visible.
 */
fun Modifier.fadeUnderTopBar(barHeight: Dp, gap: Dp = TopBarFadeGap): Modifier =
    verticalEdgeFade(top = barHeight + gap, bottom = 0.dp, topClear = barHeight)

/** The list padding for [fadeUnderTopBar]: this padding, whose top is the bar's height, plus the gap. */
@Composable
fun PaddingValues.withTopBarGap(gap: Dp = TopBarFadeGap): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding() + gap,
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding(),
    )
}

fun Modifier.pageScrollModifiers(
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical()
    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
    .fillMaxHeight()

@Composable
fun Modifier.pageWindowPadding(
    outerPadding: PaddingValues,
): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    return padding(
        start = outerPadding.calculateStartPadding(layoutDirection),
        end = outerPadding.calculateEndPadding(layoutDirection),
    ).imePadding()
}

@Composable
fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    isWideScreen: Boolean,
    extraTop: Dp = 0.dp,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
): PaddingValues {
    val topPadding = innerPadding.calculateTopPadding() + extraTop
    val bottomPadding = if (isWideScreen) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + outerPadding.calculateBottomPadding()
    } else {
        outerPadding.calculateBottomPadding()
    }
    return remember(topPadding, bottomPadding, extraStart, extraEnd) {
        PaddingValues(
            top = topPadding,
            start = extraStart,
            end = extraEnd,
            bottom = bottomPadding,
        )
    }
}

@Composable
fun pageContentPaddingWithCutout(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    isWideScreen: Boolean,
    extraTop: Dp = 0.dp,
): PaddingValues {
    val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
    return pageContentPadding(
        innerPadding = innerPadding,
        outerPadding = outerPadding,
        isWideScreen = isWideScreen,
        extraTop = extraTop,
        extraStart = cutoutPadding.calculateStartPadding(LayoutDirection.Ltr),
        extraEnd = cutoutPadding.calculateEndPadding(LayoutDirection.Ltr),
    )
}

@Composable
fun pageContentPaddingWithIme(
    contentPadding: PaddingValues,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val topPadding = contentPadding.calculateTopPadding()
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val bottomPadding = contentPadding.calculateBottomPadding() +
        WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    return remember(topPadding, startPadding, endPadding, bottomPadding) {
        PaddingValues(
            top = topPadding,
            start = startPadding,
            end = endPadding,
            bottom = bottomPadding,
        )
    }
}

@Composable
fun pageListPadding(
    contentPadding: PaddingValues,
    bottomExtra: Dp = 12.dp,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        top = contentPadding.calculateTopPadding(),
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = contentPadding.calculateBottomPadding() + bottomExtra,
    )
}

@Composable
fun AdaptiveTopAppBar(
    title: String,
    isWideScreen: Boolean,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    color: Color = Color.Transparent,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    if (isWideScreen) {
        SmallTopAppBar(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            color = color,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = false,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    } else {
        TopAppBar(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            color = color,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}


