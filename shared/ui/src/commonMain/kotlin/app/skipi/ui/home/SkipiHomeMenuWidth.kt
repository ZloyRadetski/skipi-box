// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Natural widths of the levels of a nested dropdown menu.
 *
 * A Material3 menu sizes itself to the widest content it holds, so while one level slides out and
 * the next slides in the menu would snap to the wider of the two. Each level reports its own
 * width here, and [animatedMenuWidth] then glides the menu surface between the levels.
 */
internal class MenuLevelWidths {
    private val measured = mutableStateMapOf<String, Int>()

    fun widthOf(levelKey: String): Int? = measured[levelKey]

    fun report(levelKey: String, widthPx: Int) {
        if (measured[levelKey] != widthPx) measured[levelKey] = widthPx
    }
}

/**
 * What a menu level is measured with: its own [naturalWidth], exactly, whatever width the menu surface is
 * pinned to at that moment. The surface glides between the widths of two levels; if the level followed it,
 * every frame of that glide would wrap and ellipsize its text again to the width of the frame. Measured at its
 * own width the text is laid out once, and the surface only reveals or hides it.
 */
internal fun menuLevelConstraints(incoming: Constraints, naturalWidth: Int): Constraints =
    if (naturalWidth > 0) incoming.copy(minWidth = naturalWidth, maxWidth = naturalWidth) else incoming

/**
 * Reports the natural (intrinsic) width of this menu level and lays the level out at it, so its text does not
 * reflow while the menu surface animates between levels; place it on the level's root.
 */
internal fun Modifier.reportMenuLevelWidth(widths: MenuLevelWidths, levelKey: String): Modifier =
    layout { measurable, constraints ->
        val natural = measurable.maxIntrinsicWidth(Constraints.Infinity)
        widths.report(levelKey, natural)
        val placeable = measurable.measure(menuLevelConstraints(constraints, natural))
        // The size reported to the parent still follows the surface; whatever is wider is clipped by it.
        val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * Pins the menu surface to the animated width of [currentLevelKey]. The first known width is
 * applied without animation, so opening the menu never grows from zero.
 */
@Composable
internal fun animatedMenuWidth(widths: MenuLevelWidths, currentLevelKey: String): Modifier {
    val target = widths.widthOf(currentLevelKey)
    val width = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        if (width.value == 0f) {
            width.snapTo(target.toFloat())
        } else {
            width.animateTo(target.toFloat(), tween(durationMillis = 220))
        }
    }
    // Read in the layout phase so the per-frame animation never recomposes the menu.
    return Modifier.layout { measurable, constraints ->
        val pinned = width.value.roundToInt()
        val fixed = if (pinned > 0) pinned.coerceIn(constraints.minWidth, constraints.maxWidth) else null
        val placeable = measurable.measure(
            if (fixed != null) constraints.copy(minWidth = fixed, maxWidth = fixed) else constraints,
        )
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
