// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem

/** One row of a [SkipiMenu]: a command, a choice (when [selected] is meaningful) or the door to a submenu. */
data class SkipiMenuItem(
    val title: String,
    val summary: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val icon: ImageVector? = null,
    /** Items of the submenu this row opens; when not empty the row opens it instead of running [onClick]. */
    val children: List<SkipiMenuItem> = emptyList(),
    val onClick: (() -> Unit)? = null,
    /** Identifies the row's level while a submenu is open; two rows of one menu must not share it. */
    val key: String = title,
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
}

/** A group of rows; the menu draws a divider between its sections. */
data class SkipiMenuSection(val items: List<SkipiMenuItem>)

/** The sections of a menu that were written as the library's entries; empty entries are dropped. */
fun List<DropdownEntry>.toMenuSections(): List<SkipiMenuSection> =
    filter { entry -> entry.items.isNotEmpty() }
        .map { entry -> SkipiMenuSection(entry.items.map { item -> item.toMenuItem(entryEnabled = entry.enabled) }) }

private fun DropdownItem.toMenuItem(entryEnabled: Boolean): SkipiMenuItem = SkipiMenuItem(
    title = text,
    summary = summary,
    enabled = enabled && entryEnabled,
    selected = selected,
    children = children.orEmpty().map { child -> child.toMenuItem(entryEnabled) },
    onClick = onClick,
)

/** One section that lists [this] and marks the one at [selectedIndex]; choosing a row reports its index. */
fun List<String>.toMenuSection(selectedIndex: Int, onSelected: (Int) -> Unit): SkipiMenuSection =
    SkipiMenuSection(
        mapIndexed { index, title ->
            SkipiMenuItem(title = title, selected = index == selectedIndex, onClick = { onSelected(index) })
        },
    )

/** The title of the first selected row, which is what a preference row shows as its current value. */
fun List<SkipiMenuSection>.selectedTitle(): String? =
    firstNotNullOfOrNull { section -> section.items.firstOrNull { item -> item.selected } }?.title

/** A menu of one section is a plain choice and closes on it; with several sections the rows are toggles. */
fun closesOnSelection(sectionCount: Int): Boolean = sectionCount <= 1
