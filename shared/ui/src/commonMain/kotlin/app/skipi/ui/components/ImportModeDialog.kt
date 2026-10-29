// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import features.clipboard.ClipboardImportMode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ImportModeDialog(show: Boolean, title: String, message: String, mergeText: String, replaceText: String, onDismissRequest: () -> Unit, onModeSelected: (ClipboardImportMode) -> Unit) {
    AppWindowDialog(show = show, title = title, onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = message, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            // Stacked: both labels are long and would wrap inside half-width buttons.
            TextButton(text = mergeText, onClick = { onModeSelected(ClipboardImportMode.Merge) }, modifier = Modifier.fillMaxWidth())
            TextButton(text = replaceText, onClick = { onModeSelected(ClipboardImportMode.Replace) }, modifier = Modifier.fillMaxWidth())
        }
    }
}


