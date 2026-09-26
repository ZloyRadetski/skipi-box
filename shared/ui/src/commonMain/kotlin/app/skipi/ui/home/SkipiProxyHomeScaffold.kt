// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.skipi.ui.theme.SkipiTheme
import androidx.compose.ui.unit.dp

/** Describes which Home controls stay fixed above the platform-specific list. */
data class SkipiProxyHomeScaffoldState(
    val pinConnectionPanel: Boolean,
    val showGroupSelector: Boolean,
    val showSearch: Boolean,
)

/**
 * Shared structural shell for the proxy Home screen.
 *
 * The shell owns the common order and spacing of the header, connection panel,
 * group picker, search field and scrolling body. Hosts supply the platform
 * actions and list implementation; Android may put the content header inside
 * its pager while desktop puts it inside its scroll column.
 */
@Composable
fun SkipiProxyHomeScaffold(
    state: SkipiProxyHomeScaffoldState,
    header: @Composable () -> Unit,
    connectionPanel: @Composable (Modifier) -> Unit,
    groupSelector: @Composable (Modifier) -> Unit,
    search: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    pinnedContentModifier: Modifier = Modifier,
    notifications: @Composable () -> Unit = {},
    body: @Composable BoxScope.(
        scrollingContentHeader: (@Composable (Modifier) -> Unit)?,
    ) -> Unit,
) {
    val scrollingContentHeader: (@Composable (Modifier) -> Unit)? = if (state.pinConnectionPanel) {
        null
    } else {
        { contentModifier ->
            SkipiProxyHomeContentHeader(
                state = state,
                scrolling = true,
                connectionPanel = connectionPanel,
                groupSelector = groupSelector,
                search = search,
                modifier = contentModifier,
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        header()
        if (state.pinConnectionPanel) {
            SkipiProxyHomeContentHeader(
                state = state,
                scrolling = false,
                connectionPanel = connectionPanel,
                groupSelector = groupSelector,
                search = search,
                modifier = pinnedContentModifier,
            )
        }
        notifications()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            body(scrollingContentHeader)
        }
    }
}

@Composable
private fun SkipiProxyHomeContentHeader(
    state: SkipiProxyHomeScaffoldState,
    scrolling: Boolean,
    connectionPanel: @Composable (Modifier) -> Unit,
    groupSelector: @Composable (Modifier) -> Unit,
    search: @Composable (Modifier) -> Unit,
    modifier: Modifier,
) {
    val connectionBottomPadding = when {
        scrolling -> SkipiTheme.spacing.sectionGap
        state.showGroupSelector || state.showSearch -> SkipiTheme.spacing.sectionGap
        else -> 0.dp
    }
    val groupBottomPadding = when {
        scrolling -> SkipiTheme.spacing.sectionGap
        state.showSearch -> SkipiTheme.spacing.sectionGap
        else -> 0.dp
    }
    val searchBottomPadding = if (scrolling) SkipiTheme.spacing.sectionGap else 0.dp

    Column(modifier = modifier.fillMaxWidth()) {
        connectionPanel(
            Modifier
                .fillMaxWidth()
                .padding(bottom = connectionBottomPadding),
        )
        AnimatedVisibility(
            visible = state.showGroupSelector,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            groupSelector(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = groupBottomPadding),
            )
        }
        AnimatedVisibility(
            visible = state.showSearch,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            search(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = searchBottomPadding),
            )
        }
    }
}
