// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.layout

import androidx.compose.runtime.Composable
import app.skipi.ui.layout.shouldShowSplitPane as sharedShouldShowSplitPane

@Composable
fun shouldShowSplitPane(): Boolean = sharedShouldShowSplitPane()
