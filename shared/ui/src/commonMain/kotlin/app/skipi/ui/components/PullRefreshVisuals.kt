// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text

/** Where a pull-to-refresh gesture is: what the indicator and its label show. */
enum class PullRefreshPhase {
    Idle,
    Pulling,
    ThresholdReached,
    Refreshing,
    RefreshComplete,
}

/**
 * What the pull-to-refresh visuals read from whichever gesture machinery drives them (the list one with a top
 * bar, or the Home one): the phase, how far the content is pulled, and how far the completion animation is.
 */
interface PullRefreshVisualState {
    val phase: PullRefreshPhase

    /** How far the content is pulled, in pixels. */
    val dragOffset: Float

    /** The pull at which releasing starts a refresh, in pixels. */
    val thresholdPx: Float

    /** 0 to 1 over the animation that ends a completed refresh. */
    val completeProgress: Float
}

/** The share of the threshold that is pulled, 0 to 1. */
val PullRefreshVisualState.pullProgress: Float
    get() = if (thresholdPx > 0f) (dragOffset / thresholdPx).coerceIn(0f, 1f) else 0f

/** The height of the header the pull opens above the content. */
internal fun pullRefreshHeaderHeightPx(state: PullRefreshVisualState, baseHeightPx: Float): Float = when {
    state.phase == PullRefreshPhase.Refreshing -> baseHeightPx
    state.phase == PullRefreshPhase.RefreshComplete -> baseHeightPx * (1f - state.completeProgress.coerceIn(0f, 1f))
    state.thresholdPx > 0f && state.dragOffset > 0f && state.dragOffset <= state.thresholdPx ->
        baseHeightPx * state.pullProgress
    state.dragOffset > state.thresholdPx -> baseHeightPx + (state.dragOffset - state.thresholdPx)
    else -> 0f
}

/** The label is clear once a fifth of the pull is made, and fades out as a completed refresh ends. */
internal fun pullRefreshTextAlpha(state: PullRefreshVisualState): Float = when {
    state.phase == PullRefreshPhase.ThresholdReached || state.phase == PullRefreshPhase.Refreshing -> 1f
    state.phase == PullRefreshPhase.RefreshComplete -> (1f - state.completeProgress).coerceIn(0f, 1f)
    state.dragOffset > 0f -> (state.pullProgress - 0.2f).coerceIn(0f, 1f)
    else -> 0f
}

/** The indicator grows with the pull, swells a little past the threshold and shrinks as the refresh completes. */
internal fun pullRefreshIndicatorScale(state: PullRefreshVisualState): Float = when {
    state.phase == PullRefreshPhase.Refreshing -> 1f
    state.phase == PullRefreshPhase.RefreshComplete -> (1f - state.completeProgress * 0.3f).coerceIn(0f, 1f)
    state.phase == PullRefreshPhase.ThresholdReached ->
        1f + ((state.dragOffset - state.thresholdPx) * 0.0015f).coerceAtMost(0.12f)
    state.dragOffset > 0f -> (state.pullProgress * 1.05f).coerceIn(0f, 1f)
    else -> 0f
}

/** The header above the content: the indicator and the label of the phase under it. */
@Composable
internal fun PullRefreshHeader(
    state: PullRefreshVisualState,
    circleSize: Dp,
    color: Color,
    label: String,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val baseHeightPx = with(density) { (circleSize + 32.dp).toPx() }
    val headerHeightPx by remember(state, baseHeightPx) {
        derivedStateOf { pullRefreshHeaderHeightPx(state, baseHeightPx) }
    }
    val textAlpha by remember(state) { derivedStateOf { pullRefreshTextAlpha(state) } }
    Column(
        modifier = modifier.fillMaxWidth().height(with(density) { headerHeightPx.toDp() }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PullRefreshIndicator(state, circleSize, color)
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = labelStyle,
                color = color,
                modifier = Modifier.padding(top = 4.dp).graphicsLayer { alpha = textAlpha },
            )
        }
    }
}

/** The arc that is drawn as the pull is made, spins while refreshing and shrinks away when done. */
@Composable
internal fun PullRefreshIndicator(state: PullRefreshVisualState, circleSize: Dp, color: Color) {
    val rotation = if (state.phase == PullRefreshPhase.Refreshing || state.phase == PullRefreshPhase.RefreshComplete) {
        rememberInfiniteTransition(label = "pull_refresh_rotation").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(750, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "pull_refresh_angle",
        )
    } else {
        null
    }
    val scale by remember(state) { derivedStateOf { pullRefreshIndicatorScale(state) } }
    Box(
        modifier = Modifier.size(circleSize).graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(circleSize)) {
            val strokeWidth = size.minDimension / 8.5f
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            val box = Offset(center.x - radius, center.y - radius)
            val boxSize = Size(radius * 2, radius * 2)
            when (state.phase) {
                PullRefreshPhase.Idle -> return@Canvas
                PullRefreshPhase.Pulling -> {
                    val progress = state.pullProgress
                    drawArc(
                        color = color.copy(alpha = (progress / 0.3f).coerceIn(0f, 1f)),
                        startAngle = -90f + 720f * progress,
                        sweepAngle = 300f * progress,
                        useCenter = false,
                        topLeft = box,
                        size = boxSize,
                        style = arcStyle,
                    )
                }

                PullRefreshPhase.ThresholdReached -> {
                    val extra = (state.dragOffset - state.thresholdPx).coerceAtLeast(0f)
                    drawArc(
                        color = color,
                        startAngle = -90f + extra * 0.8f,
                        sweepAngle = 310f,
                        useCenter = false,
                        topLeft = box,
                        size = boxSize,
                        style = arcStyle,
                    )
                }

                PullRefreshPhase.Refreshing -> drawArc(
                    color = color,
                    startAngle = rotation?.value ?: 0f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = box,
                    size = boxSize,
                    style = arcStyle,
                )

                PullRefreshPhase.RefreshComplete -> {
                    val progress = state.completeProgress
                    val endRadius = radius * (1f - progress * 0.25f)
                    drawArc(
                        color = color.copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                        startAngle = rotation?.value ?: 0f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(center.x - endRadius, center.y - endRadius),
                        size = Size(endRadius * 2, endRadius * 2),
                        style = arcStyle,
                    )
                }
            }
        }
    }
}
