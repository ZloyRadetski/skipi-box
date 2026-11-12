// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipi.ui.home.MenuLevelWidths
import app.skipi.ui.home.SkipiHomeDropdownMenu
import app.skipi.ui.home.animatedMenuWidth
import app.skipi.ui.home.reportMenuLevelWidth
import app.skipi.ui.home.skipiHomeDropdownItemColors
import app.skipi.ui.theme.SkipiTheme

/** The key of the level that holds the menu's own rows; the levels of submenus are keyed by their door row. */
private const val RootLevelKey = ""

/**
 * The menu of the whole app. It is a Material3 popup styled with the app's own colors, which opens next to
 * the layout it is composed in, so compose it right after the control that opens it, inside a `Box`.
 * [surface], [text], [mutedText] and [outline] recolor the surface and its rows for a screen with its own palette;
 * [header] and [footer] add free content above and below the rows of the top level (a search field, a row of
 * buttons).
 *
 * A row with children opens a submenu in place: the two levels slide past each other while the surface
 * glides to the width of the new one, and the text of a level is laid out once, so it never reflows
 * during the glide. [closeOnSelection] decides whether choosing a row closes the menu.
 */
@Composable
fun SkipiMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    sections: List<SkipiMenuSection>,
    modifier: Modifier = Modifier,
    closeOnSelection: Boolean = closesOnSelection(sections.size),
    accent: Color = SkipiTheme.colors.accent,
    surface: Color = SkipiTheme.colors.surface,
    text: Color = SkipiTheme.colors.onSurface,
    mutedText: Color = SkipiTheme.colors.onSurfaceVariant,
    outline: Color = SkipiTheme.colors.outline,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    // The path to the open submenu, as the keys of the rows that were opened. Keys, not rows: the rows are
    // rebuilt on every recomposition, and the open level must show their latest state.
    var openedKeys by remember { mutableStateOf(emptyList<String>()) }
    val levelWidths = remember { MenuLevelWidths() }
    val opened = openedKeys.openedRows(sections)

    SkipiHomeDropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            openedKeys = emptyList()
            onDismissRequest()
        },
        modifier = modifier.then(animatedMenuWidth(levelWidths, openedKeys.lastOrNull() ?: RootLevelKey)),
        surface = surface,
        text = text,
        outline = outline,
    ) {
        AnimatedContent(
            targetState = openedKeys,
            transitionSpec = {
                val direction = if (targetState.size > initialState.size) 1 else -1
                (fadeIn(tween(durationMillis = 180)) + slideInHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetX = { width -> direction * width / 7 },
                )).togetherWith(
                    fadeOut(tween(durationMillis = 120)) + slideOutHorizontally(
                        animationSpec = tween(durationMillis = 180),
                        targetOffsetX = { width -> -direction * width / 7 },
                    ),
                ).using(
                    SizeTransform(clip = false) { _, _ -> tween(durationMillis = 220) },
                )
            },
            label = "skipi_menu_levels",
        ) { levelKeys ->
            // The level on screen while it slides out is the one of its own keys, not of the current ones.
            val rows = levelKeys.openedRows(sections)
            Column(Modifier.reportMenuLevelWidth(levelWidths, levelKeys.lastOrNull() ?: RootLevelKey)) {
                if (rows.isEmpty()) header?.invoke()
                if (rows.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(text = rows.last().title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = skipiHomeDropdownItemColors(text, mutedText),
                        onClick = { openedKeys = openedKeys.dropLast(1) },
                    )
                }
                val levelSections = rows.lastOrNull()?.let { door -> listOf(SkipiMenuSection(door.children)) } ?: sections
                levelSections.forEachIndexed { sectionIndex, section ->
                    if (sectionIndex > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = outline.copy(alpha = 0.2f),
                        )
                    }
                    section.items.forEach { item ->
                        SkipiMenuRow(
                            item = item,
                            accent = accent,
                            text = text,
                            mutedText = mutedText,
                            onClick = {
                                if (item.hasChildren) {
                                    openedKeys = openedKeys + item.key
                                } else {
                                    item.onClick?.invoke()
                                    if (closeOnSelection) {
                                        openedKeys = emptyList()
                                        onDismissRequest()
                                    }
                                }
                            },
                        )
                    }
                }
                if (rows.isEmpty()) footer?.invoke()
            }
        }
    }
    // Keeps a stale path from outliving the rows it pointed at (the menu was rebuilt without them).
    LaunchedEffect(opened.size, openedKeys.size) {
        if (opened.size != openedKeys.size) openedKeys = openedKeys.take(opened.size)
    }
}

@Composable
private fun SkipiMenuRow(
    item: SkipiMenuItem,
    accent: Color,
    text: Color,
    mutedText: Color,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        modifier = if (item.contentDescription == null) {
            Modifier
        } else {
            Modifier.semantics {
                contentDescription = item.contentDescription
                selected = item.selected
            }
        },
        text = {
            Column {
                Text(text = item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.summary?.let { summary ->
                    Text(
                        text = summary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = SkipiTheme.typography.bodySmall,
                        color = mutedText,
                    )
                }
            }
        },
        enabled = item.enabled,
        leadingIcon = item.icon?.let { icon ->
            { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
        },
        trailingIcon = {
            when {
                item.hasChildren -> Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )

                item.selected -> Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )

                else -> Unit
            }
        },
        colors = skipiHomeDropdownItemColors(text, mutedText),
        onClick = onClick,
    )
}

/** The rows that were opened along [this] path of keys, outermost first; the path stops at the first key that is gone. */
internal fun List<String>.openedRows(sections: List<SkipiMenuSection>): List<SkipiMenuItem> {
    val rows = ArrayList<SkipiMenuItem>(size)
    var level = sections.flatMap { section -> section.items }
    for (key in this) {
        val door = level.firstOrNull { item -> item.key == key && item.hasChildren } ?: break
        rows += door
        level = door.children
    }
    return rows
}
