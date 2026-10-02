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

    @Test
    fun refreshFinishesOnlyAfterTheSingleRefreshAnimationIsReadyAndBusyStateIsFalse() {
        assertTrue(
            skipiShouldFinishRefresh(
                isRefreshing = false,
                refreshState = SkipiPullRefreshState.Refreshing,
                cycleReady = true,
            ),
        )
        assertEquals(
            false,
            skipiShouldFinishRefresh(
                isRefreshing = false,
                refreshState = SkipiPullRefreshState.Refreshing,
                cycleReady = false,
            ),
        )
        assertEquals(
            false,
            skipiShouldFinishRefresh(
                isRefreshing = true,
                refreshState = SkipiPullRefreshState.Refreshing,
                cycleReady = true,
            ),
        )
    }

    @Test
    fun pullRefreshHeaderAndContentPushReturnToZeroTogether() {
        val baseHeightPx = 56f
        assertEquals(
            baseHeightPx,
            skipiPullRefreshHeaderHeightPx(
                refreshState = SkipiPullRefreshState.Refreshing,
                dragOffsetPx = 72f,
                thresholdPx = 72f,
                baseHeightPx = baseHeightPx,
                completionProgress = 0f,
            ),
        )
        val completionHeight = skipiPullRefreshHeaderHeightPx(
                refreshState = SkipiPullRefreshState.RefreshComplete,
                dragOffsetPx = 72f,
                thresholdPx = 72f,
                baseHeightPx = baseHeightPx,
                completionProgress = 0.6f,
            )
        assertTrue(kotlin.math.abs(completionHeight - baseHeightPx * 0.4f) < 0.001f)
        assertEquals(
            0f,
            skipiPullRefreshHeaderHeightPx(
                refreshState = SkipiPullRefreshState.RefreshComplete,
                dragOffsetPx = 72f,
                thresholdPx = 72f,
                baseHeightPx = baseHeightPx,
                completionProgress = 1f,
            ),
        )
        assertEquals(
            0f,
            skipiPullRefreshHeaderHeightPx(
                refreshState = SkipiPullRefreshState.Idle,
                dragOffsetPx = 0f,
                thresholdPx = 72f,
                baseHeightPx = baseHeightPx,
                completionProgress = 1f,
            ),
        )
    }
}
