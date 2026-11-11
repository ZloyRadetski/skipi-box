// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.ui.components.PullRefreshPhase
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A short pull that stays below the refresh threshold springs the content back once. The release
 * velocity must not go on to the list: on Android the stretch overscroll took it and pulled the
 * subscription card a second time, half a second after the first return.
 */
class SkipiPullToRefreshFlingTest {
    private class StepClock : MonotonicFrameClock {
        private var nanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            nanos += 16_000_000L
            return onFrame(nanos)
        }
    }

    private fun CoroutineScope.controller() = SkipiPullToRefreshController(CoroutineScope(coroutineContext + StepClock())).apply {
        thresholdPx = 72f
        maxDragDistancePx = 360f
    }

    @Test
    fun releasingAShortPullKeepsItsVelocityAwayFromTheList() = runBlocking(StepClock()) {
        val controller = controller()
        controller.nestedScrollConnection.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput)
        assertEquals(PullRefreshPhase.Pulling, controller.refreshState)

        val release = Velocity(0f, 1_800f)
        val consumed = controller.nestedScrollConnection.onPreFling(release)

        assertEquals(release, consumed)
        assertEquals(0f, controller.dragOffset)
        assertEquals(PullRefreshPhase.Idle, controller.refreshState)
        assertEquals(Velocity.Zero, controller.nestedScrollConnection.onPostFling(Velocity.Zero, Velocity(0f, 300f)))
    }

    @Test
    fun anOrdinaryFlingOfTheListIsLeftAlone() = runBlocking(StepClock()) {
        val controller = controller()

        val fling = Velocity(0f, -2_400f)

        assertEquals(Velocity.Zero, controller.nestedScrollConnection.onPreFling(fling))
        assertEquals(Velocity.Zero, controller.nestedScrollConnection.onPostFling(Velocity.Zero, fling))
    }
}
