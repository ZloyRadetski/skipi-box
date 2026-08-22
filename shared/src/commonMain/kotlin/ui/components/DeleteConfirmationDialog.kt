// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import app.shared.res.Res
import app.shared.res.common_cancel
import app.shared.res.common_delete
import app.shared.res.deletion_confirmation_summary

@Composable
fun DeleteConfirmationDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    WarningConfirmDialog(
        show = show,
        title = title,
        summary = stringResource(Res.string.deletion_confirmation_summary),
        dismissText = stringResource(Res.string.common_cancel),
        confirmText = stringResource(Res.string.common_delete),
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}