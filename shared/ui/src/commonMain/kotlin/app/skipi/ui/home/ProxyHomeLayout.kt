// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import app.skipi.ui.theme.SkipiWindowClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** The home page stays one column until the window has room for a useful detail pane. */
internal enum class ProxyHomeLayoutMode {
    OneColumn,
    WideListAndDetails,
}

/** Status labels step down with the text column so narrow cards never split the state name. */
internal enum class ProxyHomeHeroStatusScale {
    Compact,
    Emphasized,
    Large,
}

internal fun proxyHomeHeroStatusScale(availableWidthDp: Float): ProxyHomeHeroStatusScale = when {
    availableWidthDp < 220f -> ProxyHomeHeroStatusScale.Compact
    availableWidthDp < 360f -> ProxyHomeHeroStatusScale.Emphasized
    else -> ProxyHomeHeroStatusScale.Large
}

internal fun proxyHomeLayoutMode(windowClass: SkipiWindowClass): ProxyHomeLayoutMode =
    if (windowClass == SkipiWindowClass.Expanded) {
        ProxyHomeLayoutMode.WideListAndDetails
    } else {
        ProxyHomeLayoutMode.OneColumn
    }

internal fun proxyHomeGroupSelectorUsesFlowLayout(
    windowClass: SkipiWindowClass,
    subscriptionSwipeEnabled: Boolean,
): Boolean = subscriptionSwipeEnabled || windowClass == SkipiWindowClass.Expanded

/** Maps a completed physical swipe to the adjacent logical group, including RTL layouts. */
internal fun proxyHomeSwipeGroupOffset(
    horizontalDistance: Float,
    threshold: Float,
    layoutDirection: LayoutDirection,
): Int {
    val logicalDistance = horizontalDistance * if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    return when {
        logicalDistance <= -threshold -> 1
        logicalDistance >= threshold -> -1
        else -> 0
    }
}

/** Resolves grid column count: 1 on compact phones; 1..3 on medium or expanded windows. */
internal fun proxyHomeResolveColumns(
    windowClass: SkipiWindowClass,
    requestedColumns: Int,
): Int {
    val count = requestedColumns.coerceIn(1, 3)
    return when (windowClass) {
        SkipiWindowClass.Compact -> 1
        SkipiWindowClass.Medium,
        SkipiWindowClass.Expanded -> count
    }
}

/** Separates a floating parent bar's overlap from Home's viewport and scrollable content. */
internal data class ProxyHomeBottomInsetTreatment(
    val viewportPadding: PaddingValues,
    val listBottomPadding: Dp,
    val floatingToolbarBottomPadding: Dp,
)

/**
 * Keeps Home's background viewport behind an overlaid bottom bar while reserving its measured
 * clearance in the scrolling list and lifting the floating Home toolbar above the bar. Hosts that
 * use a conventional, non-overlaid bottom bar pass zero and retain their existing viewport inset.
 */
internal fun proxyHomeBottomInsetTreatment(
    contentPadding: PaddingValues,
    bottomOverlayInset: Dp,
    listReservedBottom: Dp,
    layoutDirection: LayoutDirection,
): ProxyHomeBottomInsetTreatment {
    val contentBottom = contentPadding.calculateBottomPadding()
    val measuredOverlayInset = bottomOverlayInset.coerceAtLeast(0.dp)
    val availableBottom = maxOf(contentBottom, measuredOverlayInset)
    val overlayInset = measuredOverlayInset.coerceAtMost(availableBottom)
    val viewportBottom = availableBottom - overlayInset
    val viewportPadding = if (viewportBottom == contentBottom) {
        contentPadding
    } else {
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = viewportBottom,
        )
    }

    return ProxyHomeBottomInsetTreatment(
        viewportPadding = viewportPadding,
        listBottomPadding = listReservedBottom + overlayInset,
        floatingToolbarBottomPadding = overlayInset,
    )
}
