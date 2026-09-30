// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skipi.ui.theme.SkipiTheme

/** Dropdown surface and item palette shared by the custom-colored Home controls. */
@Composable
internal fun SkipiHomeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    surface: Color = SkipiTheme.colors.surface,
    text: Color = SkipiTheme.colors.onSurface,
    outline: Color = SkipiTheme.colors.outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = SkipiTheme.shapes.large,
        containerColor = surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, outline.copy(alpha = 0.2f)),
        content = {
            CompositionLocalProvider(LocalContentColor provides text) {
                content()
            }
        },
    )
}

@Composable
internal fun skipiHomeDropdownItemColors(
    text: Color = SkipiTheme.colors.onSurface,
    icon: Color = SkipiTheme.colors.onSurfaceVariant,
): MenuItemColors = MenuDefaults.itemColors(
    textColor = text,
    leadingIconColor = icon,
    trailingIconColor = icon,
)
