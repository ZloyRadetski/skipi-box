// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.WarningConfirmDialog as SharedWarningConfirmDialog

@Composable
internal fun WarningConfirmDialog(show: Boolean, title: String, summary: String, dismissText: String, confirmText: String, onDismissRequest: () -> Unit, onConfirm: () -> Unit, detailsMaxHeight: Dp = 240.dp, details: (@Composable ColumnScope.() -> Unit)? = null) =
    SharedWarningConfirmDialog(show, title, summary, dismissText, confirmText, onDismissRequest, onConfirm, detailsMaxHeight, details)
