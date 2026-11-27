// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyHomeButtonPosition
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val SnapRadius = 52.dp

/** Top-left corner of the toolbar resting at [position] in an [area]; start and end mirror in right-to-left. */
internal fun floatingToolbarOffset(
    position: ProxyHomeButtonPosition,
    area: IntSize,
    toolbar: IntSize,
    layoutDirection: LayoutDirection,
): IntOffset {
    val freeWidth = (area.width - toolbar.width).coerceAtLeast(0)
    val freeHeight = (area.height - toolbar.height).coerceAtLeast(0)
    val fromStart = (position.x * freeWidth).roundToInt()
    val x = if (layoutDirection == LayoutDirection.Rtl) freeWidth - fromStart else fromStart
    return IntOffset(x, (position.y * freeHeight).roundToInt())
}

/** The sticky zone whose resting place is nearest to [offset] and within [snapRadius] pixels of it, if any. */
internal fun floatingToolbarZoneAt(
    offset: IntOffset,
    area: IntSize,
    toolbar: IntSize,
    layoutDirection: LayoutDirection,
    snapRadius: Float,
): ProxyHomeButtonPosition? {
    fun squaredDistance(zone: ProxyHomeButtonPosition): Float {
        val delta = floatingToolbarOffset(zone, area, toolbar, layoutDirection) - offset
        return delta.x.toFloat() * delta.x + delta.y.toFloat() * delta.y
    }
    return ProxyHomeButtonPosition.StickyZones.minBy(::squaredDistance)
        .takeIf { squaredDistance(it) <= snapRadius * snapRadius }
}

/**
 * Where a toolbar let go at [dropped] comes to rest: the nearest sticky zone if one is within [snapRadius] pixels,
 * otherwise the very place it was dropped.
 */
internal fun settledFloatingToolbarPosition(
    dropped: IntOffset,
    area: IntSize,
    toolbar: IntSize,
    layoutDirection: LayoutDirection,
    snapRadius: Float,
): ProxyHomeButtonPosition {
    val freeWidth = area.width - toolbar.width
    val freeHeight = area.height - toolbar.height
    if (freeWidth <= 0 || freeHeight <= 0) return ProxyHomeButtonPosition.BottomEnd

    floatingToolbarZoneAt(dropped, area, toolbar, layoutDirection, snapRadius)?.let { return it }

    val fromStart = if (layoutDirection == LayoutDirection.Rtl) freeWidth - dropped.x else dropped.x
    return ProxyHomeButtonPosition(
        x = (fromStart.toFloat() / freeWidth).coerceIn(0f, 1f),
        y = (dropped.y.toFloat() / freeHeight).coerceIn(0f, 1f),
    )
}

internal fun clampFloatingToolbarOffset(offset: IntOffset, area: IntSize, toolbar: IntSize): IntOffset = IntOffset(
    offset.x.coerceIn(0, (area.width - toolbar.width).coerceAtLeast(0)),
    offset.y.coerceIn(0, (area.height - toolbar.height).coerceAtLeast(0)),
)

/** Sizes known only while measuring and read only by gestures, so they are not Compose state. */
private class DockMeasurements {
    var area = IntSize.Zero
    var toolbar = IntSize.Zero
}

/**
 * Places the floating toolbar anywhere in the space [modifier] gives it. Dragging its grip moves it, with a vibration
 * each time it comes into the reach of a corner or a side middle; let go there it slides onto that zone and vibrates
 * again.
 * [onPositionChange] reports a new resting place. [content] must put the modifier it receives on the grip, the only part that moves
 * the toolbar, so its buttons stay still under a finger.
 */
@Composable
internal fun FloatingToolbarDock(
    position: ProxyHomeButtonPosition,
    onPositionChange: (ProxyHomeButtonPosition) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (grip: Modifier) -> Unit,
) {
    val layoutDirection by rememberUpdatedState(LocalLayoutDirection.current)
    val latestOnPositionChange by rememberUpdatedState(onPositionChange)
    val snapRadius by rememberUpdatedState(with(LocalDensity.current) { SnapRadius.toPx() })
    // Held here as well, so the button stays where it was dropped while the host is still saving.
    val resting = remember { mutableStateOf(position) }
    // The gesture handler outlives recompositions, so a position from the host is written into the same state.
    LaunchedEffect(position) { resting.value = position }
    var dragged by remember { mutableStateOf<IntOffset?>(null) }
    // What is left of the way from the drop point to the resting place.
    var settling by remember { mutableStateOf(IntOffset.Zero) }
    val measurements = remember { DockMeasurements() }
    val scope = rememberCoroutineScope()

    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val gripModifier = remember {
        Modifier.pointerInput(Unit) {
            var finger = Offset.Zero
            var start = IntOffset.Zero
            var settleJob: Job? = null
            // The zone the held toolbar is in, so only coming into one vibrates.
            var zoneUnderDrag: ProxyHomeButtonPosition? = null

            fun zoneAt(offset: IntOffset) = floatingToolbarZoneAt(
                offset, measurements.area, measurements.toolbar, layoutDirection, snapRadius,
            )

            fun pickUp() {
                settleJob?.cancel()
                start = floatingToolbarOffset(
                    resting.value, measurements.area, measurements.toolbar, layoutDirection,
                ) + settling
                finger = Offset(start.x.toFloat(), start.y.toFloat())
                settling = IntOffset.Zero
                dragged = start
                zoneUnderDrag = zoneAt(start)
            }

            fun moveBy(amount: Offset) {
                val moved = finger + amount
                // Kept unrounded inside the area, so slow drags are not lost to rounding.
                finger = Offset(
                    moved.x.coerceIn(0f, (measurements.area.width - measurements.toolbar.width).coerceAtLeast(0).toFloat()),
                    moved.y.coerceIn(0f, (measurements.area.height - measurements.toolbar.height).coerceAtLeast(0).toFloat()),
                )
                val next = clampFloatingToolbarOffset(
                    IntOffset(finger.x.roundToInt(), finger.y.roundToInt()), measurements.area, measurements.toolbar,
                )
                dragged = next
                val zone = zoneAt(next)
                if (zone != null && zone != zoneUnderDrag) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                zoneUnderDrag = zone
            }

            fun release() {
                val dropped = dragged ?: return
                // Dragged back to where it was: the toolbar has not gone anywhere.
                val target = if (dropped == start) {
                    resting.value
                } else {
                    settledFloatingToolbarPosition(
                        dropped, measurements.area, measurements.toolbar, layoutDirection, snapRadius,
                    )
                }
                val rest = floatingToolbarOffset(target, measurements.area, measurements.toolbar, layoutDirection)
                val changed = target != resting.value
                settling = dropped - rest
                resting.value = target
                dragged = null
                if (dropped != start && target in ProxyHomeButtonPosition.StickyZones) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                if (changed) latestOnPositionChange(target)
                settleJob = scope.launch {
                    animate(
                        typeConverter = IntOffset.VectorConverter,
                        initialValue = settling,
                        targetValue = IntOffset.Zero,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { value, _ -> settling = value }
                }
            }

            detectDragGestures(
                onDragStart = { pickUp() },
                onDragEnd = { release() },
                onDragCancel = { release() },
            ) { change, amount ->
                change.consume()
                moveBy(amount)
            }
        }
    }

    Layout(content = { Box { content(gripModifier) } }, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        val area = IntSize(constraints.maxWidth, constraints.maxHeight)
        val toolbar = IntSize(placeable.width, placeable.height)
        measurements.area = area
        measurements.toolbar = toolbar
        layout(area.width, area.height) {
            // Read while placing, so a drag only moves the button and recomposes nothing.
            val offset = dragged
                ?: (floatingToolbarOffset(resting.value, area, toolbar, this@Layout.layoutDirection) + settling)
            placeable.place(offset)
        }
    }
}
