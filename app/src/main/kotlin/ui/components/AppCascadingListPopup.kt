// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import app.skipi.ui.components.AppCascadingListPopup as SharedAppCascadingListPopup
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider

@Composable
fun AppCascadingListPopup(
    show: Boolean,
    entries: List<DropdownEntry>,
    popupPositionProvider: PopupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.TopEnd,
    onDismissRequest: () -> Unit = {},
    onDismissFinished: () -> Unit = {},
) = SharedAppCascadingListPopup(show, entries, popupPositionProvider, alignment, onDismissRequest, onDismissFinished)
