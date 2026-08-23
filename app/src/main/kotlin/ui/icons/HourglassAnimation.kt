// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.icons

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An animated hourglass icon showing falling sand, visible accumulation in the bottom bulb,
 * and smooth 180° rotation cycles during pinging.
 */
@Composable
fun AnimatedHourglassIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    isPinging: Boolean = true,
    size: Dp = 20.dp,
) {
    if (!isPinging) {
        StaticHourglass(
            modifier = modifier,
            color = color,
            size = size,
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "hourglassInfinite")

    // Cycle: 0..900ms sand flowing, 900..1300ms rotating 180°, 1300..1600ms pause
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                0f at 0
                0f at 900
                180f at 1300 using CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
                180f at 1600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "hourglassRotation",
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
        label = "sandFlow",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            rotate(rotation) {
                drawHourglass(
                    sandProgress = sandFlow,
                    tint = color,
                )
            }
        }
    }
}

/**
 * A static hourglass icon matching the style of [AnimatedHourglassIcon] with sand in the top bulb.
 */
@Composable
fun StaticHourglass(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: Dp = 20.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawHourglass(
                sandProgress = 0f,
                tint = color,
            )
        }
    }
}

private fun DrawScope.drawHourglass(
    sandProgress: Float,
    tint: Color,
) {
    val w = size.width
    val h = size.height
    val strokeWidth = w * 0.085f

    val topY = h * 0.12f
    val botY = h * 0.88f
    val leftX = w * 0.18f
    val rightX = w * 0.82f
    val neckLeft = w * 0.44f
    val neckRight = w * 0.56f
    val midY = h * 0.5f

    // 1. Top and Bottom Bars
    drawLine(
        color = tint,
        start = Offset(leftX, topY),
        end = Offset(rightX, topY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = tint,
        start = Offset(leftX, botY),
        end = Offset(rightX, botY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )

    // 2. Glass Outline Path
    val glassPath = Path().apply {
        moveTo(w * 0.26f, topY)
        cubicTo(
            w * 0.28f, h * 0.32f,
            neckLeft, h * 0.42f,
            neckLeft, midY,
        )
        cubicTo(
            neckLeft, h * 0.58f,
            w * 0.28f, h * 0.68f,
            w * 0.26f, botY,
        )

        moveTo(w * 0.74f, topY)
        cubicTo(
            w * 0.72f, h * 0.32f,
            neckRight, h * 0.42f,
            neckRight, midY,
        )
        cubicTo(
            neckRight, h * 0.58f,
            w * 0.72f, h * 0.68f,
            w * 0.74f, botY,
        )
    }

    drawPath(
        path = glassPath,
        color = tint,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // 3. Sand in Top Chamber (empties as sandProgress goes 0 -> 1)
    val topSandRemaining = (1f - sandProgress).coerceIn(0f, 1f)
    if (topSandRemaining > 0.04f) {
        val topSandTopY = topY + (midY - topY) * (1f - topSandRemaining) * 0.88f
        val topSandLeftX = leftX + (neckLeft - leftX) * (1f - topSandRemaining) * 0.75f
        val topSandRightX = rightX - (rightX - neckRight) * (1f - topSandRemaining) * 0.75f

        val topSandPath = Path().apply {
            moveTo(topSandLeftX, topSandTopY)
            lineTo(topSandRightX, topSandTopY)
            lineTo(w * 0.5f, midY)
            close()
        }
        drawPath(
            path = topSandPath,
            color = tint.copy(alpha = 0.88f),
            style = Fill,
        )
    }

    // 4. Sand in Bottom Chamber (visibly fills up as sandProgress goes 0 -> 1)
    val botSandAmt = sandProgress.coerceIn(0f, 1f)
    var pileTopY = botY - strokeWidth * 0.8f

    if (botSandAmt > 0.03f) {
        val botBaseY = botY - strokeWidth * 0.8f
        val maxBotHeight = (botBaseY - midY) * 0.90f
        val currentHeight = maxBotHeight * botSandAmt
        pileTopY = botBaseY - currentHeight

        val baseSpread = (botSandAmt * 2.2f).coerceAtMost(1f)
        val curBaseLeft = w * 0.5f - (w * 0.24f) * baseSpread
        val curBaseRight = w * 0.5f + (w * 0.24f) * baseSpread

        val topSpread = ((botSandAmt - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val curTopLeft = w * 0.5f - (w * 0.16f) * topSpread
        val curTopRight = w * 0.5f + (w * 0.16f) * topSpread

        val botSandPath = Path().apply {
            moveTo(curBaseLeft, botBaseY)
            lineTo(curBaseRight, botBaseY)
            if (topSpread > 0.05f) {
                // Wide trapezoid with smooth crest
                lineTo(curTopRight, pileTopY)
                quadraticTo(w * 0.5f, pileTopY - h * 0.02f * (1f - botSandAmt), curTopLeft, pileTopY)
            } else {
                // Cone peak mound
                quadraticTo(w * 0.5f, pileTopY, curBaseLeft, botBaseY)
            }
            close()
        }
        drawPath(
            path = botSandPath,
            color = tint.copy(alpha = 0.88f),
            style = Fill,
        )
    }

    // 5. Sand Stream falling through neck down to pile top
    if (sandProgress > 0.04f && sandProgress < 0.96f) {
        drawLine(
            color = tint.copy(alpha = 0.92f),
            start = Offset(w * 0.5f, midY - h * 0.02f),
            end = Offset(w * 0.5f, pileTopY.coerceAtMost(botY - strokeWidth)),
            strokeWidth = strokeWidth * 0.85f,
            cap = StrokeCap.Round,
        )
    }
}
