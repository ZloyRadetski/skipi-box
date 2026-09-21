// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.runtime.Composable
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.deletion_confirmation_summary
import org.jetbrains.compose.resources.stringResource

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
