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

internal enum class SkipiPullRefreshState {
    Idle,
    Pulling,
    ThresholdReached,
    Refreshing,
    RefreshComplete,
}

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

internal fun skipiPullRefreshStateForOffset(offset: Float, threshold: Float): SkipiPullRefreshState = when {
    offset >= threshold && threshold > 0f -> SkipiPullRefreshState.ThresholdReached
    offset > 0f -> SkipiPullRefreshState.Pulling
    else -> SkipiPullRefreshState.Idle
}

internal fun skipiShouldFinishRefresh(
    isRefreshing: Boolean,
    refreshState: SkipiPullRefreshState,
    cycleReady: Boolean,
): Boolean = !isRefreshing && refreshState == SkipiPullRefreshState.Refreshing && cycleReady

internal fun skipiPullRefreshHeaderHeightPx(
    refreshState: SkipiPullRefreshState,
    dragOffsetPx: Float,
    thresholdPx: Float,
    baseHeightPx: Float,
    completionProgress: Float,
): Float = when {
    refreshState == SkipiPullRefreshState.Refreshing -> baseHeightPx
    refreshState == SkipiPullRefreshState.RefreshComplete ->
        baseHeightPx * (1f - completionProgress.coerceIn(0f, 1f))
    thresholdPx > 0f && dragOffsetPx > 0f && dragOffsetPx <= thresholdPx ->
        baseHeightPx * (dragOffsetPx / thresholdPx).coerceIn(0f, 1f)
    dragOffsetPx > thresholdPx -> baseHeightPx + (dragOffsetPx - thresholdPx)
    else -> 0f
}

internal class SkipiPullToRefreshController(
    private val coroutineScope: CoroutineScope,
) {
    var maxDragDistancePx: Float = 0f
    var thresholdPx: Float = 0f
    var dragOffset by mutableFloatStateOf(0f)
    var currentTouch by mutableFloatStateOf(0f)
    var isTouching by mutableStateOf(false)
    private var mutableRefreshState by mutableStateOf(SkipiPullRefreshState.Idle)
    val refreshState: SkipiPullRefreshState get() = mutableRefreshState
    private var mutableRefreshCycleReady by mutableStateOf(false)
    val refreshCycleReady: Boolean get() = mutableRefreshCycleReady
    val pullProgress: Float by derivedStateOf {
        if (thresholdPx > 0f) (dragOffset / thresholdPx).coerceIn(0f, 1f) else 0f
    }
    private val completionProgress = mutableFloatStateOf(0f)
    val refreshCompleteProgress: Float get() = completionProgress.floatValue
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
            settleOnFling()
            return Velocity.Zero
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
        if (mutableRefreshState != SkipiPullRefreshState.Refreshing &&
            mutableRefreshState != SkipiPullRefreshState.RefreshComplete
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
        mutableRefreshState = SkipiPullRefreshState.Refreshing
        animateTo(thresholdPx)
        mutableRefreshCycleReady = true
    }

    suspend fun finishRefreshing() {
        if (mutableRefreshState != SkipiPullRefreshState.Refreshing || !mutableRefreshCycleReady) return
        mutableRefreshCycleReady = false
        mutableRefreshState = SkipiPullRefreshState.RefreshComplete
        completionProgress.floatValue = 0f
        Animatable(0f).animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        ) { completionProgress.floatValue = value }
        animateTo(0f)
        mutableRefreshState = SkipiPullRefreshState.Idle
    }

    suspend fun release(onRefresh: () -> Unit) {
        if (mutableRefreshState == SkipiPullRefreshState.ThresholdReached) {
            mutableRefreshCycleReady = false
            mutableRefreshState = SkipiPullRefreshState.Refreshing
            onRefresh()
            animateTo(thresholdPx)
            mutableRefreshCycleReady = true
        } else {
            if (dragOffset > 0f || currentTouch > 0f) animateTo(0f)
            mutableRefreshState = SkipiPullRefreshState.Idle
        }
    }

    fun resetToIdle() {
        if (mutableRefreshState != SkipiPullRefreshState.Refreshing &&
            mutableRefreshState != SkipiPullRefreshState.RefreshComplete
        ) mutableRefreshState = SkipiPullRefreshState.Idle
    }

    fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (mutableRefreshState == SkipiPullRefreshState.Refreshing ||
            mutableRefreshState == SkipiPullRefreshState.RefreshComplete
        ) return available
        if (source == NestedScrollSource.UserInput && available.y < 0f && (dragOffset > 0f || currentTouch > 0f)) {
            isTouching = true
            animationJob?.cancel()
            return Offset(0f, applyDrag(available.y))
        }
        return Offset.Zero
    }

    fun onPostScroll(available: Offset, source: NestedScrollSource): Offset {
        if (mutableRefreshState == SkipiPullRefreshState.Refreshing ||
            mutableRefreshState == SkipiPullRefreshState.RefreshComplete
        ) return available
        if (source == NestedScrollSource.UserInput && available.y > 0f) {
            isTouching = true
            animationJob?.cancel()
            return Offset(0f, applyDrag(available.y))
        }
        return Offset.Zero
    }

    suspend fun settleOnFling() {
        if (dragOffset > 0f && mutableRefreshState != SkipiPullRefreshState.Refreshing &&
            mutableRefreshState != SkipiPullRefreshState.RefreshComplete
        ) {
            isTouching = false
            if (mutableRefreshState != SkipiPullRefreshState.ThresholdReached) {
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
            if (controller.refreshState == SkipiPullRefreshState.ThresholdReached) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            lastState = controller.refreshState
        }
    }

    LaunchedEffect(controller) {
        snapshotFlow {
            Triple(currentIsRefreshing.value, controller.refreshState, controller.refreshCycleReady)
        }.collect { (isRefreshingNow, refreshState, cycleReady) ->
            if (isRefreshingNow && refreshState != SkipiPullRefreshState.Refreshing &&
                refreshState != SkipiPullRefreshState.RefreshComplete
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

@Composable
internal fun SkipiPullRefreshHeader(
    controller: SkipiPullToRefreshController,
    presentation: SkipiPullRefreshPresentation,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val label = when (controller.refreshState) {
        SkipiPullRefreshState.Pulling -> presentation.pullText
        SkipiPullRefreshState.ThresholdReached -> presentation.releaseText
        SkipiPullRefreshState.Refreshing -> presentation.refreshingText
        SkipiPullRefreshState.RefreshComplete -> presentation.refreshedText
        SkipiPullRefreshState.Idle -> ""
    }
    val baseHeightPx = with(density) { (presentation.circleSize + 32.dp).toPx() }
    val headerHeightPx by remember(controller, baseHeightPx, density) {
        derivedStateOf {
            skipiPullRefreshHeaderHeightPx(
                refreshState = controller.refreshState,
                dragOffsetPx = controller.dragOffset,
                thresholdPx = controller.thresholdPx,
                baseHeightPx = baseHeightPx,
                completionProgress = controller.refreshCompleteProgress,
            )
        }
    }
    val headerHeight = with(density) { headerHeightPx.toDp() }
    val textAlpha by remember(controller) {
        derivedStateOf {
            when {
                controller.refreshState == SkipiPullRefreshState.ThresholdReached ||
                    controller.refreshState == SkipiPullRefreshState.Refreshing -> 1f
                controller.refreshState == SkipiPullRefreshState.RefreshComplete ->
                    (1f - controller.refreshCompleteProgress).coerceIn(0f, 1f)
                controller.dragOffset > 0f -> (controller.pullProgress - 0.2f).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().height(headerHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SkipiPullRefreshIndicator(controller, presentation.circleSize, presentation.color)
        if (label.isNotBlank()) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = presentation.color,
                modifier = Modifier.padding(top = 4.dp).graphicsLayer { alpha = textAlpha },
            )
        }
    }
}

@Composable
private fun SkipiPullRefreshIndicator(
    controller: SkipiPullToRefreshController,
    circleSize: Dp,
    color: Color,
) {
    val rotation = if (controller.refreshState == SkipiPullRefreshState.Refreshing ||
        controller.refreshState == SkipiPullRefreshState.RefreshComplete
    ) {
        val transition = rememberInfiniteTransition(label = "home_pull_refresh_rotation")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(750, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "home_pull_refresh_angle",
        )
    } else null
    val scale by remember(controller) {
        derivedStateOf {
            when {
                controller.refreshState == SkipiPullRefreshState.Refreshing -> 1f
                controller.refreshState == SkipiPullRefreshState.RefreshComplete ->
                    (1f - controller.refreshCompleteProgress * 0.3f).coerceIn(0f, 1f)
                controller.refreshState == SkipiPullRefreshState.ThresholdReached ->
                    1f + ((controller.dragOffset - controller.thresholdPx) * 0.0015f).coerceAtMost(0.12f)
                controller.dragOffset > 0f -> (controller.pullProgress * 1.05f).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }
    Box(
        modifier = Modifier.size(circleSize).graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(circleSize)) {
            val strokeWidth = size.minDimension / 8.5f
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val refreshBox = Offset(center.x - radius, center.y - radius)
            val refreshSize = Size(radius * 2, radius * 2)
            when (controller.refreshState) {
                SkipiPullRefreshState.Idle -> return@Canvas
                SkipiPullRefreshState.Pulling -> {
                    val progress = controller.pullProgress
                    drawArc(
                        color = color.copy(alpha = (progress / 0.3f).coerceIn(0f, 1f)),
                        startAngle = -90f + 720f * progress,
                        sweepAngle = 300f * progress,
                        useCenter = false,
                        topLeft = refreshBox,
                        size = refreshSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                SkipiPullRefreshState.ThresholdReached -> {
                    val extra = (controller.dragOffset - controller.thresholdPx).coerceAtLeast(0f)
                    drawArc(
                        color = color,
                        startAngle = -90f + extra * 0.8f,
                        sweepAngle = 310f,
                        useCenter = false,
                        topLeft = refreshBox,
                        size = refreshSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                SkipiPullRefreshState.Refreshing -> drawArc(
                    color = color,
                    startAngle = rotation?.value ?: 0f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = refreshBox,
                    size = refreshSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                SkipiPullRefreshState.RefreshComplete -> {
                    val progress = controller.refreshCompleteProgress
                    val endRadius = radius * (1f - progress * 0.25f)
                    drawArc(
                        color = color.copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                        startAngle = rotation?.value ?: 0f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(center.x - endRadius, center.y - endRadius),
                        size = Size(endRadius * 2, endRadius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}
