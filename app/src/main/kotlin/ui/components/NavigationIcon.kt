// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.skipi.ui.components.BackNavigationIcon as SharedBackNavigationIcon
import app.skipi.ui.components.NavigationIcon as SharedNavigationIcon

@Composable
internal fun NavigationIcon(onClick: () -> Unit, imageVector: ImageVector, modifier: Modifier = Modifier, contentDescription: String? = null) =
    SharedNavigationIcon(onClick, imageVector, modifier, contentDescription)

@Composable
internal fun BackNavigationIcon(onClick: () -> Unit, modifier: Modifier = Modifier) =
    SharedBackNavigationIcon(onClick, modifier)
