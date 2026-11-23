// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPullToRefreshStateTest {
    private class StepClock : MonotonicFrameClock {
        private var nanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            nanos += 16_000_000L
            return onFrame(nanos)
        }
    }

    @Test
    fun aPointerReleaseWhileRefreshingKeepsTheIndicatorAtTheThreshold() = runBlocking(StepClock()) {
        val state = AppPullToRefreshState(CoroutineScope(coroutineContext)).apply {
            refreshThresholdOffset = 72f
            maxDragDistancePx = 360f
        }
        state.showRefreshing { true }
        assertEquals(AppRefreshState.Refreshing, state.refreshState)
        assertEquals(72f, state.dragOffset)

        var refreshes = 0
        state.handlePointerRelease(onRefresh = { refreshes++ }, isRefreshingNow = { true })

        assertEquals(AppRefreshState.Refreshing, state.refreshState)
        assertEquals(72f, state.dragOffset)
        assertEquals(0, refreshes)
    }
}
