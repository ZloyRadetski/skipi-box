// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup

data class IconDropdownMenuEntry<T>(
    val key: Any,
    val title: String,
    val action: T? = null,
    val selected: Boolean = false,
    val children: List<IconDropdownMenuEntry<T>> = emptyList(),
)

@Composable
fun <T> IconDropdownMenu(
    imageVector: ImageVector,
    contentDescription: String,
    entries: List<IconDropdownMenuEntry<T>>,
    onAction: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showPopup = remember { mutableStateOf(false) }
    val holdDown = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    IconButton(
        modifier = modifier,
        onClick = {
            showPopup.value = true
            holdDown.value = true
        },
        holdDownState = holdDown.value,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
    WindowCascadingListPopup(
        show = showPopup.value,
        entries = listOf(
            DropdownEntry(
                items = entries.toDropdownItems(
                    hapticFeedback = hapticFeedback,
                    onAction = onAction,
                ),
            ),
        ),
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = {
            showPopup.value = false
        },
        onDismissFinished = {
            holdDown.value = false
        },
    )
}

private fun <T> List<IconDropdownMenuEntry<T>>.toDropdownItems(
    hapticFeedback: HapticFeedback,
    onAction: (T) -> Unit,
): List<DropdownItem> {
    return map { entry ->
        DropdownItem(
            text = entry.title,
            selected = entry.selected,
            onClick = entry.action?.let { action ->
                {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onAction(action)
                }
            },
            children = entry.children
                .takeIf { children -> children.isNotEmpty() }
                ?.toDropdownItems(
                    hapticFeedback = hapticFeedback,
                    onAction = onAction,
                ),
        )
    }
}