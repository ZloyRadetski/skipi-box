// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkipiPullToRefreshTest {
    @Test
    fun dragShowsPullingBeforeTheLegacyThresholdAndThresholdAfterEnoughPull() {
        val earlyDrag = skipiPullRefreshDrag(currentTouch = 0f, delta = 60f, maxDragDistance = 360f)
        assertTrue(earlyDrag.indicatorOffset < 72f)
        assertEquals(
            SkipiPullRefreshState.Pulling,
            skipiPullRefreshStateForOffset(earlyDrag.indicatorOffset, threshold = 72f),
        )

        val thresholdDrag = skipiPullRefreshDrag(currentTouch = 0f, delta = 200f, maxDragDistance = 360f)
        assertEquals(72f, thresholdDrag.indicatorOffset)
        assertEquals(
            SkipiPullRefreshState.ThresholdReached,
            skipiPullRefreshStateForOffset(thresholdDrag.indicatorOffset, threshold = 72f),
        )
    }

    @Test
    fun pullingBackToTheTopResetsTheIndicatorState() {
        val drag = skipiPullRefreshDrag(currentTouch = 60f, delta = -60f, maxDragDistance = 360f)
        assertEquals(0f, drag.indicatorOffset)
        assertEquals(SkipiPullRefreshState.Idle, skipiPullRefreshStateForOffset(drag.indicatorOffset, threshold = 72f))
    }
}
