// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.ui.theme.SkipiTheme

/**
 * A vertical handle between two panes. It fills [width] of the row, so the panes keep their gap, and
 * reports the pointer's horizontal travel in dp; a thin line shows while the pointer is over it.
 */
@Composable
fun SkipiPaneDivider(
    onDrag: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 16.dp,
) {
    val density = LocalDensity.current
    val latestOnDrag by rememberUpdatedState(onDrag)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val lineColor by animateColorAsState(
        if (hovered || dragged) SkipiTheme.colors.outline else Color.Transparent,
    )
    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .hoverable(interaction)
            .draggable(
                orientation = Orientation.Horizontal,
                interactionSource = interaction,
                state = rememberDraggableState { pixels -> latestOnDrag(with(density) { pixels.toDp() }) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(lineColor),
        )
    }
}
