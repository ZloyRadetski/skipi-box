// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shows a row or block of a settings page that another setting turns on or off: it fades in while it unfolds and
 * folds up again. A row that simply appears makes the rows below it jump, so every conditional row goes through this.
 */
@Composable
fun SkipiSettingsReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut(),
    ) { content() }
}
