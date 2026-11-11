// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.basic.DropdownEntry

/**
 * A [SkipiMenu] for menus that are written as the library's [entries]. It opens next to the layout it is
 * composed in, and choosing a row closes it.
 */
@Composable
fun AppEntriesMenu(
    show: Boolean,
    entries: List<DropdownEntry>,
    onDismissRequest: () -> Unit = {},
) {
    val sections = remember(entries) { entries.toMenuSections() }
    SkipiMenu(
        expanded = show && sections.isNotEmpty(),
        onDismissRequest = onDismissRequest,
        sections = sections,
        closeOnSelection = true,
    )
}
