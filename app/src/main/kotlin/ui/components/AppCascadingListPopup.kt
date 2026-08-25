// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup
import ui.LocalPopupTextStyles

@Composable
fun AppCascadingListPopup(
    show: Boolean,
    entries: List<DropdownEntry>,
    popupPositionProvider: PopupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.TopEnd,
    onDismissRequest: () -> Unit = {},
    onDismissFinished: () -> Unit = {},
) {
    val popupTextStyles = LocalPopupTextStyles.current
    if (popupTextStyles != null) {
        MiuixTheme(
            textStyles = popupTextStyles,
        ) {
            WindowCascadingListPopup(
                show = show,
                entries = entries,
                popupPositionProvider = popupPositionProvider,
                alignment = alignment,
                onDismissRequest = onDismissRequest,
                onDismissFinished = onDismissFinished,
            )
        }
    } else {
        WindowCascadingListPopup(
            show = show,
            entries = entries,
            popupPositionProvider = popupPositionProvider,
            alignment = alignment,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
        )
    }
}
