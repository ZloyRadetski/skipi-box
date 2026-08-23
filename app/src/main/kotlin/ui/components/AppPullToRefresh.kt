// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.OverScrollState

private const val THRESHOLD_DP = 72f

sealed interface AppRefreshState {
    data object Idle : AppRefreshState
    data object Pulling : AppRefreshState
    data object ThresholdReached : AppRefreshState
    data object Refreshing : AppRefreshState
    data object RefreshComplete : AppRefreshState
}

class AppPullToRefreshState(
    internal var coroutineScope: CoroutineScope,
) {
    internal var maxDragDistancePx: Float = 0f
    internal var refreshThresholdOffset: Float = 0f

    var dragOffset by mutableFloatStateOf(0f)
    var cachedNestedScrollConnection: NestedScrollConnection? = null

    private var internalRefreshState by mutableStateOf<AppRefreshState>(AppRefreshState.Idle)
    val refreshState: AppRefreshState get() = internalRefreshState

    val pullProgress: Float by derivedStateOf {
        if (refreshThresholdOffset > 0f) {
            (dragOffset / refreshThresholdOffset).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    internal var currentTouch by mutableFloatStateOf(0f)
    internal var isTouching by mutableStateOf(false)
    private val refreshCompleteAnimProgressState = mutableFloatStateOf(0f)
    internal val refreshCompleteAnimProgress: Float get() = refreshCompleteAnimProgressState.floatValue

    internal var animationJob: Job? = null

    internal suspend fun animateTo(targetValue: Float) {
        animationJob?.cancel()
        val animatable = Animatable(dragOffset)
        val job = coroutineScope.launch {
            animatable.animateTo(
                targetValue = targetValue,
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
        dragOffset = targetValue
        currentTouch = targetValue
    }

    internal suspend fun showRefreshing(isRefreshingNow: () -> Boolean) {
        internalRefreshState = AppRefreshState.Refreshing
        animateTo(refreshThresholdOffset)
        if (!isRefreshingNow()) {
            finishRefreshing(isRefreshingNow)
        }
    }

    internal suspend fun finishRefreshing(isRefreshingNow: () -> Boolean) {
        internalRefreshState = AppRefreshState.RefreshComplete
        refreshCompleteAnimProgressState.floatValue = 0f
        val animatable = Animatable(0f)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        ) {
            refreshCompleteAnimProgressState.floatValue = value
        }

        animateTo(0f)
        if (isRefreshingNow()) {
            showRefreshing(isRefreshingNow)
        } else {
            internalRefreshState = AppRefreshState.Idle
        }
    }

    internal suspend fun handlePointerRelease(
        onRefresh: () -> Unit,
        isRefreshingNow: () -> Boolean,
    ) {
        isTouching = false
        if (internalRefreshState == AppRefreshState.ThresholdReached) {
            internalRefreshState = AppRefreshState.Refreshing
            onRefresh()
            animateTo(refreshThresholdOffset)
            if (!isRefreshingNow()) {
                finishRefreshing(isRefreshingNow)
            }
        } else {
            if (dragOffset > 0f || currentTouch > 0f) {
                animateTo(0f)
            }
            internalRefreshState = AppRefreshState.Idle
        }
    }

    internal fun resetToIdle() {
        if (internalRefreshState != AppRefreshState.Refreshing && internalRefreshState != AppRefreshState.RefreshComplete) {
            internalRefreshState = AppRefreshState.Idle
        }
    }

    internal fun getOrCreateNestedScrollConnection(
        overScrollState: OverScrollState,
    ): NestedScrollConnection {
        cachedNestedScrollConnection?.let { return it }

        return (
            object : NestedScrollConnection {
                private fun applyDrag(delta: Float) {
                    if (delta == 0f) return
                    currentTouch = (currentTouch + delta).coerceIn(0f, maxDragDistancePx * 1.5f)

                    val progress = (currentTouch / maxDragDistancePx).coerceIn(0f, 1f)
                    val factor = 1f - 0.45f * progress
                    dragOffset = (currentTouch * 0.48f * factor).coerceAtMost(maxDragDistancePx * 0.5f)

                    val nextState = when {
                        refreshThresholdOffset > 0f && dragOffset >= refreshThresholdOffset -> AppRefreshState.ThresholdReached
                        dragOffset > 0 -> AppRefreshState.Pulling
                        else -> AppRefreshState.Idle
                    }
                    if (internalRefreshState != nextState &&
                        internalRefreshState != AppRefreshState.Refreshing &&
                        internalRefreshState != AppRefreshState.RefreshComplete
                    ) {
                        internalRefreshState = nextState
                    }
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (overScrollState.isOverScrollActive && internalRefreshState == AppRefreshState.Idle) return Offset.Zero
                    if (internalRefreshState == AppRefreshState.Refreshing || internalRefreshState == AppRefreshState.RefreshComplete) {
                        return available
                    }
                    if (source == NestedScrollSource.UserInput && available.y < 0 && (dragOffset > 0f || currentTouch > 0f)) {
                        isTouching = true
                        animationJob?.cancel()
                        applyDrag(available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (internalRefreshState == AppRefreshState.Refreshing || internalRefreshState == AppRefreshState.RefreshComplete) {
                        return available
                    }
                    if (source == NestedScrollSource.UserInput && available.y > 0f) {
                        isTouching = true
                        animationJob?.cancel()
                        applyDrag(available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }
            }
        ).also { cachedNestedScrollConnection = it }
    }
}

@Composable
fun rememberAppPullToRefreshState(): AppPullToRefreshState {
    val coroutineScope = rememberCoroutineScope()
    val state = remember { AppPullToRefreshState(coroutineScope) }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { THRESHOLD_DP.dp.toPx() }
    state.maxDragDistancePx = windowInfo.containerSize.height.toFloat().coerceAtLeast(thresholdPx * 3)
    state.refreshThresholdOffset = thresholdPx

    SideEffect {
        if (state.refreshState == AppRefreshState.Refreshing &&
            state.animationJob == null &&
            state.dragOffset != state.refreshThresholdOffset
        ) {
            state.dragOffset = state.refreshThresholdOffset
            state.currentTouch = state.refreshThresholdOffset
        }
    }

    return state
}

private val LocalAppPullToRefreshState = staticCompositionLocalOf<AppPullToRefreshState?> { null }

@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    pullToRefreshState: AppPullToRefreshState = rememberAppPullToRefreshState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    topAppBarScrollBehavior: ScrollBehavior? = null,
    color: Color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    circleSize: Dp = 22.dp,
    refreshTexts: List<String> = emptyList(),
    refreshTextStyle: TextStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = color,
    ),
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val overScrollState = LocalOverScrollState.current
    val haptic = LocalHapticFeedback.current
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    val isRefreshingNow: () -> Boolean = remember { { currentIsRefreshing } }

    var lastState by remember { mutableStateOf(pullToRefreshState.refreshState) }
    LaunchedEffect(pullToRefreshState.refreshState) {
        if (lastState != pullToRefreshState.refreshState) {
            if (pullToRefreshState.refreshState == AppRefreshState.ThresholdReached) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            lastState = pullToRefreshState.refreshState
        }
    }

    LaunchedEffect(isRefreshing, pullToRefreshState.refreshState) {
        if (!isRefreshing && pullToRefreshState.refreshState == AppRefreshState.Refreshing) {
            coroutineScope.launch {
                pullToRefreshState.finishRefreshing(isRefreshingNow)
            }
        } else if (isRefreshing && pullToRefreshState.refreshState == AppRefreshState.Idle) {
            coroutineScope.launch {
                pullToRefreshState.showRefreshing(isRefreshingNow)
            }
        }
    }

    val nestedScrollConnection = remember(pullToRefreshState, topAppBarScrollBehavior, overScrollState) {
        pullToRefreshState.cachedNestedScrollConnection = null
        createAppPullToRefreshConnection(pullToRefreshState, topAppBarScrollBehavior, overScrollState)
    }

    val pointerModifier = remember(pullToRefreshState) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val isAllUp = event.changes.all { !it.pressed }
                    if (isAllUp && (pullToRefreshState.dragOffset > 0f || pullToRefreshState.isTouching)) {
                        coroutineScope.launch {
                            pullToRefreshState.handlePointerRelease(currentOnRefresh, isRefreshingNow)
                        }
                    }
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalAppPullToRefreshState provides pullToRefreshState,
    ) {
        val boxModifier = remember(modifier, nestedScrollConnection, pointerModifier) {
            modifier
                .nestedScroll(nestedScrollConnection)
                .then(pointerModifier)
        }

        Box(modifier = boxModifier) {
            Column {
                AppRefreshHeader(
                    pullToRefreshState = pullToRefreshState,
                    circleSize = circleSize,
                    color = color,
                    refreshTexts = refreshTexts,
                    refreshTextStyle = refreshTextStyle,
                    modifier = Modifier.offset(y = contentPadding.calculateTopPadding()),
                )
                content()
            }
        }
    }
}

private fun createAppPullToRefreshConnection(
    pullToRefreshState: AppPullToRefreshState,
    topAppBarScrollBehavior: ScrollBehavior?,
    overScrollState: OverScrollState,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return when (pullToRefreshState.refreshState) {
            AppRefreshState.Idle -> {
                val consumedByAppBar = topAppBarScrollBehavior?.nestedScrollConnection?.onPreScroll(available, source) ?: Offset.Zero
                val remaining = available - consumedByAppBar
                val consumedByRefresh = pullToRefreshState
                    .getOrCreateNestedScrollConnection(overScrollState)
                    .onPreScroll(remaining, source)
                consumedByAppBar + consumedByRefresh
            }
            AppRefreshState.RefreshComplete, AppRefreshState.Refreshing -> available
            else -> {
                val consumedByRefresh = pullToRefreshState
                    .getOrCreateNestedScrollConnection(overScrollState)
                    .onPreScroll(available, source)
                val remaining = available - consumedByRefresh
                val consumedByAppBar = topAppBarScrollBehavior?.nestedScrollConnection?.onPreScroll(remaining, source) ?: Offset.Zero
                consumedByRefresh + consumedByAppBar
            }
        }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        return when (pullToRefreshState.refreshState) {
            AppRefreshState.RefreshComplete, AppRefreshState.Refreshing -> available
            else -> {
                val consumedByAppBar = topAppBarScrollBehavior?.nestedScrollConnection?.onPostScroll(consumed, available, source) ?: Offset.Zero
                val remaining = available - consumedByAppBar
                val consumedByRefresh = pullToRefreshState
                    .getOrCreateNestedScrollConnection(overScrollState)
                    .onPostScroll(consumed, remaining, source)
                consumedByAppBar + consumedByRefresh
            }
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pullToRefreshState.dragOffset > 0f &&
            pullToRefreshState.refreshState != AppRefreshState.Refreshing &&
            pullToRefreshState.refreshState != AppRefreshState.RefreshComplete
        ) {
            pullToRefreshState.isTouching = false
            if (pullToRefreshState.refreshState != AppRefreshState.ThresholdReached) {
                pullToRefreshState.animateTo(0f)
                pullToRefreshState.resetToIdle()
            }
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (pullToRefreshState.dragOffset > 0f &&
            pullToRefreshState.refreshState != AppRefreshState.Refreshing &&
            pullToRefreshState.refreshState != AppRefreshState.RefreshComplete
        ) {
            pullToRefreshState.isTouching = false
            if (pullToRefreshState.refreshState != AppRefreshState.ThresholdReached) {
                pullToRefreshState.animateTo(0f)
                pullToRefreshState.resetToIdle()
            }
        }
        return Velocity.Zero
    }
}

@Composable
private fun AppRefreshHeader(
    pullToRefreshState: AppPullToRefreshState,
    circleSize: Dp,
    color: Color,
    refreshTexts: List<String>,
    refreshTextStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val refreshText = when (pullToRefreshState.refreshState) {
        AppRefreshState.Pulling -> refreshTexts.getOrNull(0).orEmpty()
        AppRefreshState.ThresholdReached -> refreshTexts.getOrNull(1).orEmpty()
        AppRefreshState.Refreshing -> refreshTexts.getOrNull(2).orEmpty()
        AppRefreshState.RefreshComplete -> refreshTexts.getOrNull(3).orEmpty()
        AppRefreshState.Idle -> ""
    }

    val headerHeight by remember(pullToRefreshState, circleSize, density) {
        derivedStateOf {
            val baseHeight = circleSize + 32.dp
            when {
                pullToRefreshState.refreshState == AppRefreshState.Refreshing -> baseHeight
                pullToRefreshState.refreshState == AppRefreshState.RefreshComplete -> baseHeight * (1f - pullToRefreshState.refreshCompleteAnimProgress)
                pullToRefreshState.dragOffset > 0f -> {
                    if (pullToRefreshState.refreshThresholdOffset > 0f && pullToRefreshState.dragOffset <= pullToRefreshState.refreshThresholdOffset) {
                        baseHeight * pullToRefreshState.pullProgress
                    } else {
                        val extraDp = with(density) {
                            (pullToRefreshState.dragOffset - pullToRefreshState.refreshThresholdOffset).coerceAtLeast(0f).toDp()
                        }
                        baseHeight + extraDp
                    }
                }
                else -> 0.dp
            }
        }
    }

    val textAlpha by remember(pullToRefreshState) {
        derivedStateOf {
            when {
                pullToRefreshState.refreshState == AppRefreshState.ThresholdReached -> 1f
                pullToRefreshState.refreshState == AppRefreshState.Refreshing -> 1f
                pullToRefreshState.refreshState == AppRefreshState.RefreshComplete -> (1f - pullToRefreshState.refreshCompleteAnimProgress).coerceIn(0f, 1f)
                pullToRefreshState.dragOffset > 0f -> (pullToRefreshState.pullProgress - 0.2f).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppRefreshIndicator(
            pullToRefreshState = pullToRefreshState,
            circleSize = circleSize,
            color = color,
        )
        if (refreshText.isNotBlank()) {
            Text(
                text = refreshText,
                style = refreshTextStyle,
                color = color,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .graphicsLayer { alpha = textAlpha },
            )
        }
    }
}

@Composable
private fun AppRefreshIndicator(
    pullToRefreshState: AppPullToRefreshState,
    circleSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val rotationState = if (
        pullToRefreshState.refreshState == AppRefreshState.Refreshing ||
        pullToRefreshState.refreshState == AppRefreshState.RefreshComplete
    ) {
        animateRotation()
    } else {
        null
    }

    val scale by remember(pullToRefreshState) {
        derivedStateOf {
            when {
                pullToRefreshState.refreshState == AppRefreshState.Refreshing -> 1f
                pullToRefreshState.refreshState == AppRefreshState.RefreshComplete -> (1f - pullToRefreshState.refreshCompleteAnimProgress * 0.3f).coerceIn(0f, 1f)
                pullToRefreshState.refreshState == AppRefreshState.ThresholdReached -> 1f + ((pullToRefreshState.dragOffset - pullToRefreshState.refreshThresholdOffset) * 0.0015f).coerceAtMost(0.12f)
                pullToRefreshState.dragOffset > 0f -> (pullToRefreshState.pullProgress * 1.05f).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }

    Box(
        modifier = modifier
            .size(circleSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(circleSize)) {
            val strokeWidth = size.minDimension / 8.5f
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            when (pullToRefreshState.refreshState) {
                AppRefreshState.Idle -> return@Canvas
                AppRefreshState.Pulling -> {
                    val progress = pullToRefreshState.pullProgress
                    val alpha = (progress / 0.3f).coerceIn(0f, 1f)
                    val sweepAngle = 300f * progress
                    val startAngle = -90f + (720f * progress)
                    drawArc(
                        color = color.copy(alpha = alpha),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                AppRefreshState.ThresholdReached -> {
                    val extraDrag = (pullToRefreshState.dragOffset - pullToRefreshState.refreshThresholdOffset).coerceAtLeast(0f)
                    val startAngle = -90f + (extraDrag * 0.8f)
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = 310f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                AppRefreshState.Refreshing -> {
                    val rotation = rotationState?.value ?: 0f
                    drawArc(
                        color = color,
                        startAngle = rotation,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                AppRefreshState.RefreshComplete -> {
                    val progress = pullToRefreshState.refreshCompleteAnimProgress
                    val alpha = (1f - progress).coerceIn(0f, 1f)
                    val scaleRadius = radius * (1f - (progress * 0.25f))
                    val rotation = rotationState?.value ?: 0f
                    drawArc(
                        color = color.copy(alpha = alpha),
                        startAngle = rotation,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(center.x - scaleRadius, center.y - scaleRadius),
                        size = Size(scaleRadius * 2, scaleRadius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

@Composable
private fun animateRotation(): State<Float> {
    val infiniteTransition = rememberInfiniteTransition(label = "pull_refresh_rotation")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pull_refresh_angle",
    )
}
