// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset

/**
 * Lets a scrolling region draw over the padding of its parent: it keeps the box the parent gave it
 * but its content grows by [horizontal] on both sides, [top] upwards and [bottom] downwards.
 * The scroller then has no visible edge inside the padded column; the caller adds the same amounts
 * as content padding so items still rest where they did.
 */
fun Modifier.scrollBleed(horizontal: Dp, top: Dp = Dp(0f), bottom: Dp = Dp(0f)): Modifier =
    layout { measurable, constraints ->
        val h = horizontal.roundToPx()
        val t = top.roundToPx()
        val b = bottom.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = 2 * h, vertical = t + b))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-h, -t) }
    }

/**
 * Fades content out over the first [top] and last [bottom] of the box instead of cutting it off.
 * The first [topClear] of [top] is fully hidden before the fade begins, for a transparent bar that
 * content scrolls under.
 */
fun Modifier.verticalEdgeFade(top: Dp, bottom: Dp, topClear: Dp = Dp(0f)): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = top.toPx()
            val bottomPx = bottom.toPx()
            if (topPx > 0f) {
                val clearFraction = (topClear.toPx() / topPx).coerceIn(0f, 1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        clearFraction to Color.Transparent,
                        1f to Color.Black,
                        startY = 0f,
                        endY = topPx,
                    ),
                    size = Size(size.width, topPx),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottomPx > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        1f to Color.Transparent,
                        startY = size.height - bottomPx,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - bottomPx),
                    size = Size(size.width, bottomPx),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
