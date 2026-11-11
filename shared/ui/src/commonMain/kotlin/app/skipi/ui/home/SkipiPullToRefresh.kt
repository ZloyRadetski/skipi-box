// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import app.skipi.ui.components.PullRefreshHeader
import app.skipi.ui.components.PullRefreshPhase
import app.skipi.ui.components.PullRefreshVisualState
import app.skipi.ui.components.pullProgress
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val pullRefreshThreshold = 72.dp
private val pullRefreshMaxDrag = 360.dp

internal data class SkipiPullRefreshDrag(
    val touchOffset: Float,
    val indicatorOffset: Float,
)

/** Retains the legacy Home pull resistance so the gesture feels like the Android 0.4.1 screen. */
internal fun skipiPullRefreshDrag(
    currentTouch: Float,
    delta: Float,
    maxDragDistance: Float,
): SkipiPullRefreshDrag {
    val safeMaxDrag = maxDragDistance.coerceAtLeast(1f)
    val touchOffset = (currentTouch + delta).coerceIn(0f, safeMaxDrag * 1.5f)
    val progress = (touchOffset / safeMaxDrag).coerceIn(0f, 1f)
    val resistance = 1f - 0.45f * progress
    val indicatorOffset = (touchOffset * 0.48f * resistance).coerceAtMost(safeMaxDrag * 0.5f)
    return SkipiPullRefreshDrag(touchOffset, indicatorOffset)
}

internal fun skipiPullRefreshStateForOffset(offset: Float, threshold: Float): PullRefreshPhase = when {
    offset >= threshold && threshold > 0f -> PullRefreshPhase.ThresholdReached
    offset > 0f -> PullRefreshPhase.Pulling
    else -> PullRefreshPhase.Idle
}

internal fun skipiShouldFinishRefresh(
    isRefreshing: Boolean,
    refreshState: PullRefreshPhase,
    cycleReady: Boolean,
): Boolean = !isRefreshing && refreshState == PullRefreshPhase.Refreshing && cycleReady

internal class SkipiPullToRefreshController(
    private val coroutineScope: CoroutineScope,
) : PullRefreshVisualState {
    var maxDragDistancePx: Float = 0f
    override var thresholdPx: Float = 0f
    override var dragOffset by mutableFloatStateOf(0f)
    var currentTouch by mutableFloatStateOf(0f)
    var isTouching by mutableStateOf(false)
    private var mutableRefreshState by mutableStateOf(PullRefreshPhase.Idle)
    val refreshState: PullRefreshPhase get() = mutableRefreshState
    private var mutableRefreshCycleReady by mutableStateOf(false)
    val refreshCycleReady: Boolean get() = mutableRefreshCycleReady
    private val completionProgress = mutableFloatStateOf(0f)
    val refreshCompleteProgress: Float get() = completionProgress.floatValue
    override val phase: PullRefreshPhase get() = refreshState
    override val completeProgress: Float get() = refreshCompleteProgress
    var animationJob: Job? = null
    private var onRefresh: () -> Unit = {}

    fun updateCallbacks(onRefresh: () -> Unit) {
        this.onRefresh = onRefresh
    }

    fun onPointerReleased() {
        if (!isTouching) return
        isTouching = false
        coroutineScope.launch { release(onRefresh) }
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            this@SkipiPullToRefreshController.onPreScroll(available, source)

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            this@SkipiPullToRefreshController.onPostScroll(available, source)

        override suspend fun onPreFling(available: Velocity): Velocity {
            // The release of a pull belongs to the pull: passed on, Android's stretch overscroll
            // took it and pulled the content a second time after the spring had returned it.
            val pulling = mutableRefreshState == PullRefreshPhase.Pulling ||
                mutableRefreshState == PullRefreshPhase.ThresholdReached
            settleOnFling()
            return if (pulling) available else Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settleOnFling()
            return Velocity.Zero
        }
    }

    fun applyDrag(delta: Float): Float {
        if (delta == 0f) return 0f
        val previousTouch = currentTouch
        val drag = skipiPullRefreshDrag(currentTouch, delta, maxDragDistancePx)
        currentTouch = drag.touchOffset
        dragOffset = drag.indicatorOffset
        if (mutableRefreshState != PullRefreshPhase.Refreshing &&
            mutableRefreshState != PullRefreshPhase.RefreshComplete
        ) {
            mutableRefreshState = skipiPullRefreshStateForOffset(dragOffset, thresholdPx)
        }
        return currentTouch - previousTouch
    }

    suspend fun animateTo(target: Float) {
        animationJob?.cancel()
        val animatable = Animatable(dragOffset)
        val job = coroutineScope.launch {
            animatable.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) {
                dragOffset = value
                currentTouch = value
            }
        }
        animationJob = job
        try {
            job.join()
        } finally {
            if (animationJob == job) animationJob = null
        }
        dragOffset = target
        currentTouch = target
    }

    suspend fun showRefreshing() {
        mutableRefreshCycleReady = false
        mutableRefreshState = PullRefreshPhase.Refreshing
        animateTo(thresholdPx)
        mutableRefreshCycleReady = true
    }

    suspend fun finishRefreshing() {
        if (mutableRefreshState != PullRefreshPhase.Refreshing || !mutableRefreshCycleReady) return
        mutableRefreshCycleReady = false
        mutableRefreshState = PullRefreshPhase.RefreshComplete
        completionProgress.floatValue = 0f
        Animatable(0f).animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        ) { completionProgress.floatValue = value }
        animateTo(0f)
        mutableRefreshState = PullRefreshPhase.Idle
    }

    suspend fun release(onRefresh: () -> Unit) {
        if (mutableRefreshState == PullRefreshPhase.ThresholdReached) {
            mutableRefreshCycleReady = false
            mutableRefreshState = PullRefreshPhase.Refreshing
            onRefresh()
            animateTo(thresholdPx)
            mutableRefreshCycleReady = true
        } else {
            if (dragOffset > 0f || currentTouch > 0f) animateTo(0f)
            mutableRefreshState = PullRefreshPhase.Idle
        }
    }

    fun resetToIdle() {
        if (mutableRefreshState != PullRefreshPhase.Refreshing &&
            mutableRefreshState != PullRefreshPhase.RefreshComplete
        ) mutableRefreshState = PullRefreshPhase.Idle
    }

    fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (mutableRefreshState == PullRefreshPhase.Refreshing ||
            mutableRefreshState == PullRefreshPhase.RefreshComplete
        ) return available
        if (source == NestedScrollSource.UserInput && available.y < 0f && (dragOffset > 0f || currentTouch > 0f)) {
            isTouching = true
            animationJob?.cancel()
            return Offset(0f, applyDrag(available.y))
        }
        return Offset.Zero
    }

    fun onPostScroll(available: Offset, source: NestedScrollSource): Offset {
        if (mutableRefreshState == PullRefreshPhase.Refreshing ||
            mutableRefreshState == PullRefreshPhase.RefreshComplete
        ) return available
        if (source == NestedScrollSource.UserInput && available.y > 0f) {
            isTouching = true
            animationJob?.cancel()
            return Offset(0f, applyDrag(available.y))
        }
        return Offset.Zero
    }

    suspend fun settleOnFling() {
        if (dragOffset > 0f && mutableRefreshState != PullRefreshPhase.Refreshing &&
            mutableRefreshState != PullRefreshPhase.RefreshComplete
        ) {
            isTouching = false
            if (mutableRefreshState != PullRefreshPhase.ThresholdReached) {
                animateTo(0f)
                resetToIdle()
            }
        }
    }
}

internal data class SkipiPullRefreshPresentation(
    val pullText: String,
    val releaseText: String,
    val refreshingText: String,
    val refreshedText: String,
    val color: Color,
    val circleSize: Dp = 24.dp,
)

/** Remembers the legacy Home pull state and keeps its callbacks bound to the latest host state. */
@Composable
internal fun rememberSkipiPullToRefreshController(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
): SkipiPullToRefreshController {
    val coroutineScope = rememberCoroutineScope()
    val controller = remember(coroutineScope) { SkipiPullToRefreshController(coroutineScope) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val currentOnRefresh = rememberUpdatedState(onRefresh)
    val currentIsRefreshing = rememberUpdatedState(isRefreshing)
    val thresholdPx = with(density) { pullRefreshThreshold.toPx() }
    controller.maxDragDistancePx = with(density) { pullRefreshMaxDrag.toPx() }
    controller.thresholdPx = thresholdPx
    controller.updateCallbacks(
        onRefresh = { currentOnRefresh.value() },
    )

    var lastState by remember(controller) { mutableStateOf(controller.refreshState) }
    LaunchedEffect(controller.refreshState) {
        if (lastState != controller.refreshState) {
            if (controller.refreshState == PullRefreshPhase.ThresholdReached) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            lastState = controller.refreshState
        }
    }

    LaunchedEffect(controller) {
        snapshotFlow {
            Triple(currentIsRefreshing.value, controller.refreshState, controller.refreshCycleReady)
        }.collect { (isRefreshingNow, refreshState, cycleReady) ->
            if (isRefreshingNow && refreshState != PullRefreshPhase.Refreshing &&
                refreshState != PullRefreshPhase.RefreshComplete
            ) {
                controller.showRefreshing()
            } else if (skipiShouldFinishRefresh(isRefreshingNow, refreshState, cycleReady)) {
                controller.finishRefreshing()
            }
        }
    }

    return controller
}

internal fun Modifier.skipiPullToRefresh(controller: SkipiPullToRefreshController): Modifier =
    nestedScroll(controller.nestedScrollConnection)
        .pointerInput(controller) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.all { !it.pressed } &&
                        (controller.dragOffset > 0f || controller.isTouching)
                    ) controller.onPointerReleased()
                }
            }
        }

/** The Home header over the list: the shared pull-to-refresh header with Home's labels and text style. */
@Composable
internal fun SkipiPullRefreshHeader(
    controller: SkipiPullToRefreshController,
    presentation: SkipiPullRefreshPresentation,
    modifier: Modifier = Modifier,
) {
    val label = when (controller.refreshState) {
        PullRefreshPhase.Pulling -> presentation.pullText
        PullRefreshPhase.ThresholdReached -> presentation.releaseText
        PullRefreshPhase.Refreshing -> presentation.refreshingText
        PullRefreshPhase.RefreshComplete -> presentation.refreshedText
        PullRefreshPhase.Idle -> ""
    }
    PullRefreshHeader(
        state = controller,
        circleSize = presentation.circleSize,
        color = presentation.color,
        label = label,
        labelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        modifier = modifier,
    )
}
