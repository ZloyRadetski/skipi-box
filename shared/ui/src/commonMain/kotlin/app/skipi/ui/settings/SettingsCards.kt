// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.ui.theme.SkipiTheme

/** Shared settings surface for existing preference controls. */
@Composable
fun SkipiSettingsSectionCard(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = SkipiTheme.spacing.medium,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SkipiTheme.spacing.medium)
            .padding(bottom = bottomPadding),
        shape = SkipiTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = SkipiTheme.colors.surface,
            contentColor = SkipiTheme.colors.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SkipiTheme.spacing.medium),
        ) { content() }
    }
}

/** Shared grouping surface for navigation entries. */
@Composable
fun SkipiSettingsCategoryGroupCard(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = SkipiTheme.spacing.medium,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SkipiTheme.spacing.medium)
            .padding(bottom = bottomPadding),
        shape = SkipiTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = SkipiTheme.colors.surface,
            contentColor = SkipiTheme.colors.onSurface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** Platform-neutral settings navigation row. All visible text and actions are host supplied. */
@Composable
fun SkipiSettingsCategoryEntry(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    value: String? = null,
    iconColor: Color = SkipiTheme.colors.onAccent,
    iconBackgroundColor: Color = SkipiTheme.colors.accent,
    trailingIcon: ImageVector? = null,
    showDivider: Boolean = false,
    selected: Boolean? = null,
    contentDescription: String? = null,
) {
    val entryDescription = contentDescription
    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    selected?.let { this.selected = it }
                    entryDescription?.let { this.contentDescription = it }
                },
            headlineContent = {
                Text(
                    text = title,
                    style = SkipiTheme.typography.titleMedium,
                    color = SkipiTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = summary?.takeIf(String::isNotBlank)?.let { text ->
                { Text(text, style = SkipiTheme.typography.bodyMedium, color = SkipiTheme.colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            leadingContent = {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(SkipiTheme.shapes.small)
                        .background(iconBackgroundColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    value?.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            style = SkipiTheme.typography.bodyMedium,
                            color = SkipiTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(SkipiTheme.spacing.small))
                    }
                    trailingIcon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = SkipiTheme.colors.onSurfaceVariant.copy(alpha = 0.72f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            tonalElevation = 0.dp,
        )
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp, end = SkipiTheme.spacing.medium),
                color = SkipiTheme.colorScheme.outline.copy(alpha = 0.35f),
                thickness = 0.5.dp,
            )
        }
    }
}
