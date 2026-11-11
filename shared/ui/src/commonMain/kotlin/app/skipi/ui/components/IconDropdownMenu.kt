// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class IconDropdownMenuEntry<T>(
    val key: Any,
    val title: String,
    val action: T? = null,
    val selected: Boolean = false,
    val children: List<IconDropdownMenuEntry<T>> = emptyList(),
)

/** An icon button that opens a [SkipiMenu] of [entries] under it; choosing one reports its action. */
@Composable
fun <T> IconDropdownMenu(
    imageVector: ImageVector,
    contentDescription: String,
    entries: List<IconDropdownMenuEntry<T>>,
    onAction: (T) -> Unit,
    hapticFeedback: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // The menu opens next to the layout it is composed in, so the button and the menu share a Box.
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            holdDownState = expanded,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MiuixTheme.colorScheme.onBackground,
            )
        }
        SkipiMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            sections = listOf(SkipiMenuSection(entries.toMenuItems(hapticFeedback, onAction))),
            closeOnSelection = true,
        )
    }
}

private fun <T> List<IconDropdownMenuEntry<T>>.toMenuItems(
    hapticFeedback: HapticFeedback,
    onAction: (T) -> Unit,
): List<SkipiMenuItem> = map { entry ->
    SkipiMenuItem(
        title = entry.title,
        selected = entry.selected,
        children = entry.children.toMenuItems(hapticFeedback, onAction),
        onClick = entry.action?.let { action ->
            {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onAction(action)
            }
        },
        key = entry.key.toString(),
    )
}
