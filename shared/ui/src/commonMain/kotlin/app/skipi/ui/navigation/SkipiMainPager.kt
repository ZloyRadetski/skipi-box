// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Common top-level pager shell.
 *
 * Hosts own screen-specific state and services; this component owns only the
 * shared conversion from pager position to SKIPI's top-level destination.
 */
@Composable
fun SkipiMainPager(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = false,
    content: @Composable (SkipiMainDestination) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled,
        verticalAlignment = Alignment.Top,
    ) { pageIndex ->
        content(SkipiMainDestination.fromPageIndex(pageIndex))
    }
}
