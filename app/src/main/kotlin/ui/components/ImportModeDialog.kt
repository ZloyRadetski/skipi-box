// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.R
import app.skipi.ui.components.ImportModeDialog as SharedImportModeDialog
import features.clipboard.ClipboardImportMode

@Composable
internal fun ImportModeDialog(
    show: Boolean,
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    onModeSelected: (ClipboardImportMode) -> Unit,
) = SharedImportModeDialog(
    show = show,
    title = title,
    message = message,
    mergeText = stringResource(R.string.common_merge_import),
    replaceText = stringResource(R.string.common_replace_existing),
    onDismissRequest = onDismissRequest,
    onModeSelected = onModeSelected,
)
