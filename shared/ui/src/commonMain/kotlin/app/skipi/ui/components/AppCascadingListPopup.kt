// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.runtime.Composable
import app.skipi.ui.theme.LocalPopupColors
import app.skipi.ui.theme.LocalPopupTextStyles
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup

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
    val popupColors = LocalPopupColors.current
    if (popupTextStyles != null || popupColors != null) {
        MiuixTheme(
            colors = popupColors ?: MiuixTheme.colorScheme,
            textStyles = popupTextStyles ?: MiuixTheme.textStyles,
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


