// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.deletion_confirmation_summary
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.stringResource

/**
 * The "Deletion confirmation" setting, offered to every deletion dialog below it. With it off such a dialog does
 * not appear: asking to show it confirms at once, so a caller that forgot to check the setting still obeys it.
 */
val LocalConfirmDeletion = compositionLocalOf { true }

@Composable
fun DeleteConfirmationDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!LocalConfirmDeletion.current) {
        val confirm by rememberUpdatedState(onConfirm)
        LaunchedEffect(show) { if (show) confirm() }
        return
    }
    val haptics = LocalHapticFeedback.current
    WarningConfirmDialog(
        show = show,
        title = title,
        summary = stringResource(Res.string.deletion_confirmation_summary),
        dismissText = stringResource(Res.string.common_cancel),
        confirmText = stringResource(Res.string.common_delete),
        onDismissRequest = onDismissRequest,
        onConfirm = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onConfirm()
        },
    )
}
