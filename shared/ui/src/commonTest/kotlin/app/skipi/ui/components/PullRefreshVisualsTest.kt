// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PullRefreshVisualsTest {
    private fun state(
        phase: PullRefreshPhase,
        dragOffset: Float = 0f,
        thresholdPx: Float = 72f,
        completeProgress: Float = 0f,
    ) = object : PullRefreshVisualState {
        override val phase = phase
        override val dragOffset = dragOffset
        override val thresholdPx = thresholdPx
        override val completeProgress = completeProgress
    }

    @Test
    fun theHeaderIsFullWhileRefreshingAndShrinksToNothingWhenTheRefreshCompletes() {
        assertEquals(56f, pullRefreshHeaderHeightPx(state(PullRefreshPhase.Refreshing, 72f), baseHeightPx = 56f))
        assertTrue(
            kotlin.math.abs(
                pullRefreshHeaderHeightPx(state(PullRefreshPhase.RefreshComplete, 72f, completeProgress = 0.6f), 56f) - 56f * 0.4f,
            ) < 0.001f,
        )
        assertEquals(0f, pullRefreshHeaderHeightPx(state(PullRefreshPhase.RefreshComplete, 72f, completeProgress = 1f), 56f))
        assertEquals(0f, pullRefreshHeaderHeightPx(state(PullRefreshPhase.Idle), 56f))
    }

    @Test
    fun theHeaderGrowsWithThePullAndKeepsFollowingTheFingerPastTheThreshold() {
        assertEquals(28f, pullRefreshHeaderHeightPx(state(PullRefreshPhase.Pulling, dragOffset = 36f), 56f))
        assertEquals(56f + 20f, pullRefreshHeaderHeightPx(state(PullRefreshPhase.ThresholdReached, dragOffset = 92f), 56f))
    }

    @Test
    fun theLabelFadesInAfterAFifthOfThePullIsStaysOnUntilTheRefreshCompletesThenFades() {
        assertEquals(0f, pullRefreshTextAlpha(state(PullRefreshPhase.Idle)))
        assertEquals(0f, pullRefreshTextAlpha(state(PullRefreshPhase.Pulling, dragOffset = 7.2f)))
        assertTrue(pullRefreshTextAlpha(state(PullRefreshPhase.Pulling, dragOffset = 60f)) > 0.5f)
        assertEquals(1f, pullRefreshTextAlpha(state(PullRefreshPhase.ThresholdReached, dragOffset = 80f)))
        assertEquals(1f, pullRefreshTextAlpha(state(PullRefreshPhase.Refreshing, dragOffset = 72f)))
        assertEquals(0.25f, pullRefreshTextAlpha(state(PullRefreshPhase.RefreshComplete, completeProgress = 0.75f)))
    }

    @Test
    fun theIndicatorGrowsWithThePullPopsPastTheThresholdAndShrinksWhenDone() {
        assertEquals(0f, pullRefreshIndicatorScale(state(PullRefreshPhase.Idle)))
        assertTrue(pullRefreshIndicatorScale(state(PullRefreshPhase.Pulling, dragOffset = 36f)) in 0.4f..0.6f)
        assertEquals(1f, pullRefreshIndicatorScale(state(PullRefreshPhase.Refreshing, dragOffset = 72f)))
        assertEquals(1.12f, pullRefreshIndicatorScale(state(PullRefreshPhase.ThresholdReached, dragOffset = 72f + 200f)))
        assertEquals(0.7f, pullRefreshIndicatorScale(state(PullRefreshPhase.RefreshComplete, completeProgress = 1f)))
    }

    @Test
    fun pullProgressIsTheShareOfTheThresholdAndNeverMoreThanOne() {
        assertEquals(0.5f, state(PullRefreshPhase.Pulling, dragOffset = 36f).pullProgress)
        assertEquals(1f, state(PullRefreshPhase.ThresholdReached, dragOffset = 300f).pullProgress)
        assertEquals(0f, state(PullRefreshPhase.Idle, thresholdPx = 0f).pullProgress)
    }
}
