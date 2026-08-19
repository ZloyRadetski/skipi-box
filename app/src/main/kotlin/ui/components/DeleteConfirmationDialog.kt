// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.R

@Composable
internal fun DeleteConfirmationDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    WarningConfirmDialog(
        show = show,
        title = title,
        summary = stringResource(R.string.deletion_confirmation_summary),
        dismissText = stringResource(R.string.common_cancel),
        confirmText = stringResource(R.string.common_delete),
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}
