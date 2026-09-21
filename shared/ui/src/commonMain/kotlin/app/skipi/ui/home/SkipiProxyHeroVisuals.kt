// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared power-symbol canvas used by every Proxy Home connection surface. */
@Composable
fun SkipiProxyHeroPowerIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color = color,
            startAngle = -55f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = Offset(center.x, center.y - radius * 1.05f),
            end = Offset(center.x, center.y - radius * 0.05f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/** Shared signal-strength icon for measured proxy latency. */
@Composable
fun SkipiProxyHeroSignalBarsIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val barCount = 3
        val spacing = size.width * 0.18f
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount
        for (index in 0 until barCount) {
            val barHeight = size.height * (0.35f + 0.32f * index)
            val left = index * (barWidth + spacing)
            val top = size.height - barHeight
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

/** Animated connection aura, composed only while the hero is connected. */
@Composable
fun SkipiProxyHeroPowerButtonWaves(
    accent: Color,
    size: Dp = 86.dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skipiProxyHeroPowerWaves")
    val firstProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skipiProxyHeroPowerWaveOne",
    )
    val secondProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = 1200, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skipiProxyHeroPowerWaveTwo",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        listOf(firstProgress, secondProgress).forEach { progress ->
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val scale = 1f + 0.35f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = (1f - progress) * 0.40f
                    }
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}

/** Shared spinner for an in-progress connection. */
@Composable
fun SkipiProxyHeroConnectingSpinner(
    accent: Color,
    size: Dp = 92.dp,
    modifier: Modifier = Modifier,
) {
    val rotation = rememberInfiniteTransition(label = "skipiProxyHeroConnectingSpinner")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "skipiProxyHeroConnectingRotation",
        )
        .value

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
            .drawBehind {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            accent.copy(alpha = 0f),
                            accent.copy(alpha = 0.35f),
                            accent,
                        ),
                    ),
                    startAngle = 0f,
                    sweepAngle = 280f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            },
    )
}

/** Resource-friendly pulse that stops its animation when [enabled] is false. */
@Composable
fun rememberSkipiProxyHeroPulse(
    enabled: Boolean,
    initialValue: Float,
    targetValue: Float,
    durationMillis: Int,
    restingValue: Float = initialValue,
): State<Float> {
    val animatable = remember { Animatable(restingValue) }
    LaunchedEffect(enabled) {
        if (enabled) {
            animatable.animateTo(
                targetValue = targetValue,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        } else {
            animatable.stop()
            animatable.snapTo(restingValue)
        }
    }
    return animatable.asState()
}

/** Shared animated hourglass used when a latency measurement is in progress. */
@Composable
fun SkipiProxyHeroAnimatedHourglassIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: Dp = 20.dp,
) {
    val transition = rememberInfiniteTransition(label = "skipiProxyHeroHourglass")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                0f at 0
                0f at 900
                180f at 1300 using CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
                180f at 1600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "skipiProxyHeroHourglassRotation",
    )
    val sandFlow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                0f at 0
                1f at 880
                1f at 1600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "skipiProxyHeroHourglassSand",
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            rotate(rotation) {
                drawSkipiProxyHeroHourglass(sandProgress = sandFlow, tint = color)
            }
        }
    }
}

private fun DrawScope.drawSkipiProxyHeroHourglass(
    sandProgress: Float,
    tint: Color,
) {
    val width = size.width
    val height = size.height
    val strokeWidth = width * 0.085f
    val topY = height * 0.12f
    val bottomY = height * 0.88f
    val leftX = width * 0.18f
    val rightX = width * 0.82f
    val neckLeft = width * 0.44f
    val neckRight = width * 0.56f
    val middleY = height * 0.5f

    drawLine(tint, Offset(leftX, topY), Offset(rightX, topY), strokeWidth, StrokeCap.Round)
    drawLine(tint, Offset(leftX, bottomY), Offset(rightX, bottomY), strokeWidth, StrokeCap.Round)

    val outline = Path().apply {
        moveTo(width * 0.26f, topY)
        cubicTo(width * 0.28f, height * 0.32f, neckLeft, height * 0.42f, neckLeft, middleY)
        cubicTo(neckLeft, height * 0.58f, width * 0.28f, height * 0.68f, width * 0.26f, bottomY)
        moveTo(width * 0.74f, topY)
        cubicTo(width * 0.72f, height * 0.32f, neckRight, height * 0.42f, neckRight, middleY)
        cubicTo(neckRight, height * 0.58f, width * 0.72f, height * 0.68f, width * 0.74f, bottomY)
    }
    drawPath(
        path = outline,
        color = tint,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    val topSand = (1f - sandProgress).coerceIn(0f, 1f)
    if (topSand > 0.04f) {
        val topSandY = topY + (middleY - topY) * (1f - topSand) * 0.88f
        val topSandLeft = leftX + (neckLeft - leftX) * (1f - topSand) * 0.75f
        val topSandRight = rightX - (rightX - neckRight) * (1f - topSand) * 0.75f
        val path = Path().apply {
            moveTo(topSandLeft, topSandY)
            lineTo(topSandRight, topSandY)
            lineTo(width * 0.5f, middleY)
            close()
        }
        drawPath(path = path, color = tint.copy(alpha = 0.88f), style = Fill)
    }

    val bottomSand = sandProgress.coerceIn(0f, 1f)
    var pileTopY = bottomY - strokeWidth * 0.8f
    if (bottomSand > 0.03f) {
        val baseY = bottomY - strokeWidth * 0.8f
        val maxHeight = (baseY - middleY) * 0.90f
        pileTopY = baseY - maxHeight * bottomSand
        val baseSpread = (bottomSand * 2.2f).coerceAtMost(1f)
        val baseLeft = width * 0.5f - width * 0.24f * baseSpread
        val baseRight = width * 0.5f + width * 0.24f * baseSpread
        val topSpread = ((bottomSand - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val topLeft = width * 0.5f - width * 0.16f * topSpread
        val topRight = width * 0.5f + width * 0.16f * topSpread
        val path = Path().apply {
            moveTo(baseLeft, baseY)
            lineTo(baseRight, baseY)
            if (topSpread > 0.05f) {
                lineTo(topRight, pileTopY)
                quadraticTo(width * 0.5f, pileTopY - height * 0.02f * (1f - bottomSand), topLeft, pileTopY)
            } else {
                quadraticTo(width * 0.5f, pileTopY, baseLeft, baseY)
            }
            close()
        }
        drawPath(path = path, color = tint.copy(alpha = 0.88f), style = Fill)
    }

    if (sandProgress > 0.04f && sandProgress < 0.96f) {
        drawLine(
            color = tint.copy(alpha = 0.92f),
            start = Offset(width * 0.5f, middleY - height * 0.02f),
            end = Offset(width * 0.5f, pileTopY.coerceAtMost(bottomY - strokeWidth)),
            strokeWidth = strokeWidth * 0.85f,
            cap = StrokeCap.Round,
        )
    }
}
