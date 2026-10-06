// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.AppCascadingListPopup
import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_edit
import app.skipi.ui.resources.common_refresh
import app.skipi.ui.resources.configs_activate
import app.skipi.ui.resources.configs_duplicate
import app.skipi.ui.resources.configs_export
import app.skipi.ui.resources.configs_export_base64
import app.skipi.ui.resources.configs_import_url
import app.skipi.ui.resources.configs_import_url_dialog_summary
import app.skipi.ui.resources.configs_import_url_dialog_title
import app.skipi.ui.resources.configs_raw_edit
import app.skipi.ui.resources.configs_source_url
import app.skipi.ui.resources.configs_ui_edit
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Exact shared presentation for importing a config by URL. Clipboard and fetching stay with the caller. */
@Composable
fun SkipiTrafficConfigUrlImportDialog(
    show: Boolean,
    url: String,
    onUrlChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onImport: (String) -> Unit,
) {
    if (!show) return
    AppWindowDialog(
        show = true,
        title = stringResource(Res.string.configs_import_url_dialog_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.configs_import_url_dialog_summary),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            TextField(
                state = rememberTextFieldState(initialText = url),
                inputTransformation = { onUrlChange(asCharSequence().toString()) },
                label = stringResource(Res.string.configs_source_url),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(text = stringResource(Res.string.common_cancel), onClick = onDismissRequest)
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(Res.string.configs_import_url),
                    enabled = url.isNotBlank(),
                    onClick = { onImport(url.trim()) },
                )
            }
        }
    }
}

/** Shared profile action menu. Actions are injected so storage, navigation, and import policy stay platform-owned. */
@Composable
fun SkipiTrafficConfigContextMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onRawEdit: () -> Unit,
    onUiEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onExportBase64: () -> Unit,
    onDelete: () -> Unit,
    onEnable: () -> Unit,
    onUpdate: (() -> Unit)? = null,
    showRawEdit: Boolean = true,
    showUiEdit: Boolean = true,
    showDuplicate: Boolean = true,
    showExport: Boolean = true,
    showExportBase64: Boolean = true,
    showDelete: Boolean = true,
    showEnable: Boolean = true,
) {
    val items = buildList {
        if (onUpdate != null) add(DropdownItem(text = stringResource(Res.string.common_refresh), onClick = onUpdate))
        if (showRawEdit) add(DropdownItem(text = stringResource(Res.string.configs_raw_edit), onClick = onRawEdit))
        if (showUiEdit) add(DropdownItem(text = stringResource(Res.string.configs_ui_edit), onClick = onUiEdit))
        if (showDuplicate) add(DropdownItem(text = stringResource(Res.string.configs_duplicate), onClick = onDuplicate))
        if (showExport) add(DropdownItem(text = stringResource(Res.string.configs_export), onClick = onExport))
        if (showExportBase64) add(DropdownItem(text = stringResource(Res.string.configs_export_base64), onClick = onExportBase64))
        if (showDelete) add(DropdownItem(text = stringResource(Res.string.common_delete), onClick = onDelete))
        if (showEnable) add(DropdownItem(text = stringResource(Res.string.configs_activate), onClick = onEnable))
    }
    AppCascadingListPopup(
        show = show && items.isNotEmpty(),
        entries = if (items.isEmpty()) emptyList() else listOf(DropdownEntry(items = items)),
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun SkipiTrafficConfigProxyGroupContextMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCascadingListPopup(
        show = show,
        entries = listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(text = stringResource(Res.string.common_edit), onClick = onEdit),
                    DropdownItem(text = stringResource(Res.string.configs_duplicate), onClick = onDuplicate),
                    DropdownItem(text = stringResource(Res.string.common_delete), onClick = onDelete),
                ),
            ),
        ),
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest,
    )
}
