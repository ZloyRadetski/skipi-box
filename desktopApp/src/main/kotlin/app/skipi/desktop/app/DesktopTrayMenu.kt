// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text

/** What the tray menu lists, in order. */
internal sealed interface DesktopTrayMenuEntry {
    class Action(val label: String, val onClick: () -> Unit) : DesktopTrayMenuEntry

    /** A line of text that is not clickable, e.g. the active server. */
    class Info(val label: String) : DesktopTrayMenuEntry

    data object Separator : DesktopTrayMenuEntry
}

/** A size in dp, which on desktop is the same unit AWT positions windows in. */
internal data class DesktopTrayMenuSize(val width: Int, val height: Int)

/** The part of a screen a window may use, without the taskbar. */
internal data class DesktopTrayScreenArea(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right get() = x + width
    val bottom get() = y + height
}

internal data class DesktopTrayMenuOrigin(val x: Int, val y: Int)

private const val MenuWidth = 220
private const val MenuVerticalPadding = 6
private const val RowHeight = 36
private const val SeparatorHeight = 9

/** Room around the menu card for its shadow inside the transparent window. */
private const val ShadowMargin = 12

internal fun desktopTrayMenuSize(entries: List<DesktopTrayMenuEntry>): DesktopTrayMenuSize =
    DesktopTrayMenuSize(
        width = MenuWidth,
        height = 2 * MenuVerticalPadding + entries.sumOf { entry ->
            when (entry) {
                is DesktopTrayMenuEntry.Action, is DesktopTrayMenuEntry.Info -> RowHeight
                DesktopTrayMenuEntry.Separator -> SeparatorHeight
            }
        },
    )

internal fun desktopTrayMenuWindowSize(entries: List<DesktopTrayMenuEntry>): DesktopTrayMenuSize {
    val menu = desktopTrayMenuSize(entries)
    return DesktopTrayMenuSize(menu.width + 2 * ShadowMargin, menu.height + 2 * ShadowMargin)
}

/** The screen that holds the point, or null when none does. */
internal fun desktopTrayScreenAt(screens: List<DesktopTrayScreenArea>, x: Int, y: Int): DesktopTrayScreenArea? =
    screens.firstOrNull { x >= it.x && x < it.right && y >= it.y && y < it.bottom }

/**
 * Where the menu window goes for a click on the tray icon: it opens towards the middle of the
 * screen, so a tray at the bottom right gets a menu above and to the left of the click, and it is
 * always kept inside the usable area.
 */
internal fun desktopTrayMenuOrigin(
    clickX: Int,
    clickY: Int,
    window: DesktopTrayMenuSize,
    screen: DesktopTrayScreenArea,
): DesktopTrayMenuOrigin {
    val opensLeft = clickX + window.width > screen.right
    val opensUp = clickY + window.height > screen.bottom
    val x = if (opensLeft) clickX - window.width else clickX
    val y = if (opensUp) clickY - window.height else clickY
    return DesktopTrayMenuOrigin(
        x = x.coerceIn(screen.x, maxOf(screen.x, screen.right - window.width)),
        y = y.coerceIn(screen.y, maxOf(screen.y, screen.bottom - window.height)),
    )
}

/** The menu card, drawn by Compose in the app's own theme so it looks the same on every platform. */
@Composable
internal fun DesktopTrayMenuContent(entries: List<DesktopTrayMenuEntry>, onDismiss: () -> Unit) {
    val size = desktopTrayMenuSize(entries)
    val shape = RoundedCornerShape(12.dp)
    val colors = SkipiTheme.colors
    Box(Modifier.padding(ShadowMargin.dp)) {
        Column(
            modifier = Modifier
                .size(width = size.width.dp, height = size.height.dp)
                .shadow(elevation = 8.dp, shape = shape)
                .clip(shape)
                .background(colors.surface)
                .padding(vertical = MenuVerticalPadding.dp),
        ) {
            entries.forEach { entry ->
                when (entry) {
                    is DesktopTrayMenuEntry.Action -> DesktopTrayMenuRow(entry.label) {
                        onDismiss()
                        entry.onClick()
                    }

                    is DesktopTrayMenuEntry.Info -> Box(
                        Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(RowHeight.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = entry.label,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            fontSize = 13.sp,
                            color = colors.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    DesktopTrayMenuEntry.Separator -> Box(
                        Modifier.height(SeparatorHeight.dp).fillMaxWidth().padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        HorizontalDivider(color = colors.onSurface.copy(alpha = 0.12f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopTrayMenuRow(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = SkipiTheme.colors
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .height(RowHeight.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) colors.onSurface.copy(alpha = 0.09f) else colors.surface)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp),
            fontSize = 14.sp,
            color = colors.onSurface,
            maxLines = 1,
        )
    }
}
