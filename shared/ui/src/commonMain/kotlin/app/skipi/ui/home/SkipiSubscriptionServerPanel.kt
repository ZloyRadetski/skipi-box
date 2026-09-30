// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class SkipiSubscriptionPanelSegment {
    CollapsedHeader,
    ExpandedHeader,
    Body,
    LastBody,
}

/**
 * One lazy item in a continuous provider panel. Each segment paints only the
 * panel edges it owns, so subscription rows can remain lazy without putting a
 * nested eager column inside the grid.
 */
@Composable
internal fun SkipiSubscriptionServerPanelSegment(
    segment: SkipiSubscriptionPanelSegment,
    surfaceColor: Color,
    borderColor: Color,
    dividerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = when (segment) {
        SkipiSubscriptionPanelSegment.CollapsedHeader -> RoundedCornerShape(18.dp)
        SkipiSubscriptionPanelSegment.ExpandedHeader -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        SkipiSubscriptionPanelSegment.Body -> RoundedCornerShape(0.dp)
        SkipiSubscriptionPanelSegment.LastBody -> RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceColor)
            .drawBehind {
                val borderWidth = 1.dp.toPx()
                val edge = borderWidth / 2f
                val left = edge
                val top = edge
                val right = size.width - edge
                val bottom = size.height - edge
                val radius = 18.dp.toPx()
                val outline = Path()

                when (segment) {
                    SkipiSubscriptionPanelSegment.CollapsedHeader -> {
                        outline.moveTo(left, top + radius)
                        outline.quadraticTo(left, top, left + radius, top)
                        outline.lineTo(right - radius, top)
                        outline.quadraticTo(right, top, right, top + radius)
                        outline.lineTo(right, bottom - radius)
                        outline.quadraticTo(right, bottom, right - radius, bottom)
                        outline.lineTo(left + radius, bottom)
                        outline.quadraticTo(left, bottom, left, bottom - radius)
                        outline.close()
                        drawPath(outline, borderColor, style = Stroke(borderWidth))
                    }

                    SkipiSubscriptionPanelSegment.ExpandedHeader -> {
                        outline.moveTo(left, size.height)
                        outline.lineTo(left, top + radius)
                        outline.quadraticTo(left, top, left + radius, top)
                        outline.lineTo(right - radius, top)
                        outline.quadraticTo(right, top, right, top + radius)
                        outline.lineTo(right, size.height)
                        drawPath(outline, borderColor, style = Stroke(borderWidth))
                    }

                    SkipiSubscriptionPanelSegment.Body -> {
                        drawLine(
                            borderColor,
                            start = androidx.compose.ui.geometry.Offset(left, 0f),
                            end = androidx.compose.ui.geometry.Offset(left, size.height),
                            strokeWidth = borderWidth,
                        )
                        drawLine(
                            borderColor,
                            start = androidx.compose.ui.geometry.Offset(right, 0f),
                            end = androidx.compose.ui.geometry.Offset(right, size.height),
                            strokeWidth = borderWidth,
                        )
                        val dividerWidth = 0.8.dp.toPx()
                        drawLine(
                            dividerColor,
                            start = androidx.compose.ui.geometry.Offset(20.dp.toPx(), size.height - dividerWidth / 2f),
                            end = androidx.compose.ui.geometry.Offset(size.width - 20.dp.toPx(), size.height - dividerWidth / 2f),
                            strokeWidth = dividerWidth,
                        )
                    }

                    SkipiSubscriptionPanelSegment.LastBody -> {
                        outline.moveTo(left, 0f)
                        outline.lineTo(left, bottom - radius)
                        outline.quadraticTo(left, bottom, left + radius, bottom)
                        outline.lineTo(right - radius, bottom)
                        outline.quadraticTo(right, bottom, right, bottom - radius)
                        outline.lineTo(right, 0f)
                        drawPath(outline, borderColor, style = Stroke(borderWidth))
                    }
                }
            },
        content = content,
    )
}
