// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import app.skipi.ui.components.DeleteConfirmationDialog as SharedDeleteConfirmationDialog

@Composable
internal fun DeleteConfirmationDialog(show: Boolean, title: String, onDismissRequest: () -> Unit, onConfirm: () -> Unit) =
    SharedDeleteConfirmationDialog(show, title, onDismissRequest, onConfirm)
