// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_merge_import
import app.skipi.ui.resources.proxy_server_list_import_clipboard
import app.skipi.ui.resources.proxy_server_list_import_file
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SkipiImportDialog(
    show: Boolean,
    importText: String,
    replaceExisting: Boolean,
    isSubmitting: Boolean = false,
    onImportTextChange: (String) -> Unit,
    onReplaceExistingChange: (Boolean) -> Unit,
    onClipboardImport: (() -> Unit)? = null,
    onFileImport: (() -> Unit)? = null,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    AppWindowDialog(
        show = show,
        title = stringResource(Res.string.common_merge_import),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Paste YAML (Clash), JSON (Xray), Base64, or URI list below:",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )

            TextField(
                value = importText,
                onValueChange = onImportTextChange,
                label = "proxies: [...] / vless://...",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 280.dp),
                enabled = !isSubmitting,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Replace existing configuration",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = replaceExisting,
                    onCheckedChange = onReplaceExistingChange,
                    enabled = !isSubmitting,
                )
            }

            if (onClipboardImport != null || onFileImport != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onClipboardImport != null) {
                        TextButton(
                            text = stringResource(Res.string.proxy_server_list_import_clipboard),
                            onClick = onClipboardImport,
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting,
                        )
                    }
                    if (onFileImport != null) {
                        TextButton(
                            text = stringResource(Res.string.proxy_server_list_import_file),
                            onClick = onFileImport,
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(Res.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onConfirmImport,
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && importText.isNotBlank(),
                ) {
                    Text(text = stringResource(Res.string.common_merge_import))
                }
            }
        }
    }
}
