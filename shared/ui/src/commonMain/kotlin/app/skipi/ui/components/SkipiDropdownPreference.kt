// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A settings row that shows its current value and opens a [SkipiMenu] to change it: the row of the library's
 * dropdown preference with the app's own menu under it. The menu opens next to the value, at the end of the row.
 */
@Composable
fun SkipiMenuPreference(
    title: String,
    sections: List<SkipiMenuSection>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    enabled: Boolean = true,
    showValue: Boolean = true,
    closeOnSelection: Boolean = closesOnSelection(sections.count { it.items.isNotEmpty() }),
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val hasItems = sections.any { section -> section.items.isNotEmpty() }
    val actionsEnabled = enabled && hasItems
    val actionColor = if (actionsEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        startAction = startAction,
        insideMargin = insideMargin,
        enabled = actionsEnabled,
        role = Role.DropdownList,
        holdDownState = expanded,
        onClick = {
            expanded = !expanded
            if (expanded) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
        endActions = { PreferenceValue(sections, showValue, actionColor, expanded, { expanded = false }, closeOnSelection) },
    )
}

/** The value, the arrow and the menu they open, composed together so the menu is anchored to the value. */
@Composable
private fun RowScope.PreferenceValue(
    sections: List<SkipiMenuSection>,
    showValue: Boolean,
    actionColor: androidx.compose.ui.graphics.Color,
    expanded: Boolean,
    onDismiss: () -> Unit,
    closeOnSelection: Boolean,
) {
    Box(Modifier.weight(1f, fill = false).align(Alignment.CenterVertically)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val value = sections.selectedTitle()?.takeIf { showValue && it.isNotEmpty() }
            if (value != null) {
                Text(
                    text = value,
                    modifier = Modifier.padding(end = 8.dp).weight(1f, fill = false),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = actionColor,
                    textAlign = TextAlign.End,
                )
            }
            DropdownArrowEndAction(actionColor = actionColor)
        }
        SkipiMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            sections = sections,
            closeOnSelection = closeOnSelection,
        )
    }
}

/** A preference that chooses one of [items]; the one at [selectedIndex] is current. */
@Composable
fun SkipiDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    enabled: Boolean = true,
    showValue: Boolean = true,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    SkipiMenuPreference(
        title = title,
        sections = listOf(items.toMenuSection(selectedIndex) { index -> onSelectedIndexChange?.invoke(index) }),
        modifier = modifier,
        summary = summary,
        startAction = startAction,
        insideMargin = insideMargin,
        enabled = enabled,
        showValue = showValue,
    )
}

/** A preference whose menu is written as the library's entries, for menus that have several sections. */
@Composable
fun SkipiDropdownPreference(
    title: String,
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    enabled: Boolean = true,
    showValue: Boolean = true,
    closeOnSelection: Boolean = entries.size <= 1,
) {
    SkipiMenuPreference(
        title = title,
        sections = entries.toMenuSections(),
        modifier = modifier,
        summary = summary,
        startAction = startAction,
        insideMargin = insideMargin,
        enabled = enabled,
        showValue = showValue,
        closeOnSelection = closeOnSelection,
    )
}

/** A preference that chooses one of [items], each of which may carry a summary of its own. */
@Composable
fun SkipiSpinnerPreference(
    items: List<DropdownItem>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    enabled: Boolean = true,
    showValue: Boolean = true,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    val entries = remember(items, selectedIndex, onSelectedIndexChange) {
        listOf(
            DropdownEntry(
                items = items.mapIndexed { index, item ->
                    item.copy(selected = index == selectedIndex, onClick = { onSelectedIndexChange?.invoke(index) })
                },
            ),
        )
    }
    SkipiDropdownPreference(
        title = title,
        entries = entries,
        modifier = modifier,
        summary = summary,
        startAction = startAction,
        insideMargin = insideMargin,
        enabled = enabled,
        showValue = showValue,
    )
}
