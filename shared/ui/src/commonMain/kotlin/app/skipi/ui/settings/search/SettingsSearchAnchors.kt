// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.skipi.ui.theme.SkipiTheme
import kotlinx.coroutines.delay

/** How long a found row stays lit. */
private const val HighlightMillis = 1_800L

/**
 * Marks a row of a settings page, so a search result can scroll to it and light it up. [id] is the row's id in the
 * catalog.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsSearchAnchor(id: String, content: @Composable () -> Unit) {
    val focus = LocalSettingsSearchController.current.focus?.takeIf { id in it.chain }
    val anchor = remember { SettingsSearchAnchorHandle() }
    if (focus != null) {
        DisposableEffect(focus, id) {
            focus.anchors[id] = anchor
            onDispose { if (focus.anchors[id] === anchor) focus.anchors.remove(id) }
        }
    }
    val alpha by animateFloatAsState(if (focus?.highlighted == id) 0.18f else 0f, tween(durationMillis = 350))
    val color = SkipiTheme.colors.accent
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("settings-anchor:$id")
            .bringIntoViewRequester(anchor.requester)
            .onSizeChanged { anchor.height = it.height }
            .drawWithContent {
                drawContent()
                // Drawn over the row: the rows paint their own backgrounds.
                if (alpha > 0f) drawRoundRect(color.copy(alpha = alpha), cornerRadius = CornerRadius(12.dp.toPx()))
            },
    ) { content() }
}

/**
 * Shows the row a search result asked this [page] for: scrolls [listState] until the row is composed, brings it
 * into view and lights it for a moment. A row that is switched off gives way to the setting that shows it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsSearchFocusEffect(page: SettingsSearchPage, listState: LazyListState) {
    val controller = LocalSettingsSearchController.current
    val focus = controller.focus?.takeIf { it.page == page } ?: return
    // Room around the row, so it does not end up under the top bar or the navigation bar.
    val margin = with(LocalDensity.current) { 120.dp.toPx() }
    LaunchedEffect(focus) {
        try {
            val found = focus.chain.firstOrNull { listState.scrollUntilComposed(focus, it) } ?: return@LaunchedEffect
            val anchor = focus.anchors[found] ?: return@LaunchedEffect
            // As much room as the screen has to spare, on both sides of the whole row.
            val spare = (listState.layoutInfo.viewportSize.height - anchor.height) / 2f
            val room = spare.coerceIn(0f, margin)
            anchor.requester.bringIntoView(Rect(0f, -room, 1f, anchor.height + room))
            focus.highlighted = found
            delay(HighlightMillis)
            focus.highlighted = null
            delay(400)
        } finally {
            // Leaving the page early forgets the request too; a later visit starts at the top as usual.
            if (controller.focus === focus) controller.clearFocus()
        }
    }
}

/** Scrolls down from the top, a part of a screen at a time, until the anchor [id] is composed. */
private suspend fun LazyListState.scrollUntilComposed(focus: SettingsSearchFocus, id: String): Boolean {
    // Not before the page has been laid out once: a scroll composes rows and must not run inside a composition.
    withFrameNanos {}
    scrollToItem(0)
    while (true) {
        // A frame for the layout and one for the effects of what it composed.
        withFrameNanos {}
        withFrameNanos {}
        if (id in focus.anchors) return true
        val height = layoutInfo.viewportSize.height
        if (height == 0) continue
        if (!canScrollForward) return false
        scrollBy(height * 0.6f)
    }
}
