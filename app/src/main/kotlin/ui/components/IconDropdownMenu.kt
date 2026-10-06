// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import app.skipi.ui.components.IconDropdownMenu as SharedIconDropdownMenu
import app.skipi.ui.components.IconDropdownMenuEntry as SharedIconDropdownMenuEntry

typealias IconDropdownMenuEntry<T> = SharedIconDropdownMenuEntry<T>

@Composable
internal fun <T> IconDropdownMenu(imageVector: ImageVector, contentDescription: String, entries: List<IconDropdownMenuEntry<T>>, onAction: (T) -> Unit, modifier: Modifier = Modifier) =
    SharedIconDropdownMenu(imageVector, contentDescription, entries, onAction, LocalHapticFeedback.current, modifier)
