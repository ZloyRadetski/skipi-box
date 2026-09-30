// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.ui.theme.SkipiWindowClass
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeLayoutTest {
    @Test
    fun compactAndMediumWindowsUseOneColumnWhileExpandedWindowsShowDetailsPane() {
        assertEquals(ProxyHomeLayoutMode.OneColumn, proxyHomeLayoutMode(SkipiWindowClass.Compact))
        assertEquals(ProxyHomeLayoutMode.OneColumn, proxyHomeLayoutMode(SkipiWindowClass.Medium))
        assertEquals(ProxyHomeLayoutMode.WideListAndDetails, proxyHomeLayoutMode(SkipiWindowClass.Expanded))
        assertEquals(false, proxyHomeGroupSelectorUsesFlowLayout(SkipiWindowClass.Compact, false))
        assertEquals(false, proxyHomeGroupSelectorUsesFlowLayout(SkipiWindowClass.Medium, false))
        assertEquals(true, proxyHomeGroupSelectorUsesFlowLayout(SkipiWindowClass.Expanded, false))
        assertEquals(true, proxyHomeGroupSelectorUsesFlowLayout(SkipiWindowClass.Compact, true))
    }

    @Test
    fun groupSwipeAdvancesInLogicalDirectionForLtrAndRtl() {
        assertEquals(1, proxyHomeSwipeGroupOffset(-100f, 72f, LayoutDirection.Ltr))
        assertEquals(-1, proxyHomeSwipeGroupOffset(100f, 72f, LayoutDirection.Ltr))
        assertEquals(1, proxyHomeSwipeGroupOffset(100f, 72f, LayoutDirection.Rtl))
        assertEquals(-1, proxyHomeSwipeGroupOffset(-100f, 72f, LayoutDirection.Rtl))
        assertEquals(0, proxyHomeSwipeGroupOffset(40f, 72f, LayoutDirection.Ltr))
    }

    @Test
    fun connectionStatusUsesSmallerTypeWhenTheCardHasLessTextWidth() {
        assertEquals(ProxyHomeHeroStatusScale.Emphasized, proxyHomeHeroStatusScale(274f))
        assertEquals(ProxyHomeHeroStatusScale.Compact, proxyHomeHeroStatusScale(196f))
        assertEquals(ProxyHomeHeroStatusScale.Large, proxyHomeHeroStatusScale(400f))
    }

    @Test
    fun compactLayoutForcesOneColumnWhileMediumAndExpandedHonorRequestedColumns() {
        assertEquals(1, proxyHomeResolveColumns(SkipiWindowClass.Compact, 1))
        assertEquals(1, proxyHomeResolveColumns(SkipiWindowClass.Compact, 2))
        assertEquals(1, proxyHomeResolveColumns(SkipiWindowClass.Compact, 3))
        assertEquals(1, proxyHomeResolveColumns(SkipiWindowClass.Medium, 1))
        assertEquals(2, proxyHomeResolveColumns(SkipiWindowClass.Medium, 2))
        assertEquals(3, proxyHomeResolveColumns(SkipiWindowClass.Medium, 3))
        assertEquals(1, proxyHomeResolveColumns(SkipiWindowClass.Expanded, 1))
        assertEquals(2, proxyHomeResolveColumns(SkipiWindowClass.Expanded, 2))
        assertEquals(3, proxyHomeResolveColumns(SkipiWindowClass.Expanded, 3))
    }

    @Test
    fun floatingNavigationInsetMovesFromViewportToScrollableClearance() {
        val treatment = proxyHomeBottomInsetTreatment(
            contentPadding = PaddingValues(start = 8.dp, top = 4.dp, end = 12.dp, bottom = 76.dp),
            bottomOverlayInset = 76.dp,
            listReservedBottom = 88.dp,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(4.dp, treatment.viewportPadding.calculateTopPadding())
        assertEquals(8.dp, treatment.viewportPadding.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, treatment.viewportPadding.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, treatment.viewportPadding.calculateBottomPadding())
        assertEquals(164.dp, treatment.listBottomPadding)
        assertEquals(76.dp, treatment.floatingToolbarBottomPadding)
    }

    @Test
    fun measuredFloatingNavigationInsetKeepsPinnedViewportFullAndAddsScrollableClearance() {
        val treatment = proxyHomeBottomInsetTreatment(
            contentPadding = PaddingValues(start = 8.dp, top = 4.dp, end = 12.dp, bottom = 0.dp),
            bottomOverlayInset = 96.dp,
            listReservedBottom = 88.dp,
            layoutDirection = LayoutDirection.Ltr,
        )

        assertEquals(0.dp, treatment.viewportPadding.calculateBottomPadding())
        assertEquals(184.dp, treatment.listBottomPadding)
        assertEquals(96.dp, treatment.floatingToolbarBottomPadding)
    }

    @Test
    fun desktopPaddingStaysOnViewportWithoutAddingItToListClearance() {
        val treatment = proxyHomeBottomInsetTreatment(
            contentPadding = PaddingValues(start = 8.dp, top = 4.dp, end = 12.dp, bottom = 20.dp),
            bottomOverlayInset = 0.dp,
            listReservedBottom = 24.dp,
            layoutDirection = LayoutDirection.Rtl,
        )

        assertEquals(8.dp, treatment.viewportPadding.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(12.dp, treatment.viewportPadding.calculateEndPadding(LayoutDirection.Rtl))
        assertEquals(20.dp, treatment.viewportPadding.calculateBottomPadding())
        assertEquals(24.dp, treatment.listBottomPadding)
        assertEquals(0.dp, treatment.floatingToolbarBottomPadding)
    }

}
