// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiWindowClass
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_more
import org.jetbrains.compose.resources.stringResource

data class SkipiProxyGroupItem(
    val id: String,
    val title: String,
    val serverCount: Int,
    val enabled: Boolean,
    val actions: List<SkipiProxyGroupAction> = emptyList(),
)

data class SkipiProxyGroupAction(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
)

data class SkipiProxyGroupSelectorColors(
    val surface: Color,
    val raisedSurface: Color,
    val selectedSurface: Color,
    val border: Color,
    val selectedBorder: Color,
    val text: Color,
    val mutedText: Color,
    val selectedText: Color = text,
    val selectedMutedText: Color = mutedText,
)

/** Common horizontally-scrollable proxy-group selector for the shared Home UI. */
@Composable
fun SkipiProxyGroupSelector(
    groups: List<SkipiProxyGroupItem>,
    selectedGroupId: String?,
    title: String,
    emptyText: String,
    disabledTitle: @Composable (String) -> String,
    serverCountText: @Composable (Int) -> String,
    colors: SkipiProxyGroupSelectorColors,
    onSelect: (String) -> Unit,
    onGroupAction: (String, String) -> Unit = { _, _ -> },
    flowLayout: Boolean = SkipiTheme.windowClass == SkipiWindowClass.Expanded,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = SkipiTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = SkipiTheme.spacing.large,
                vertical = SkipiTheme.spacing.medium,
            ),
            verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
        ) {
            Text(title, color = colors.text, style = SkipiTheme.typography.titleLarge, fontWeight = themedFontWeight(FontWeight.Bold))
            if (groups.isEmpty()) {
                Text(emptyText, color = colors.mutedText, style = SkipiTheme.typography.bodyMedium)
            } else {
                if (flowLayout) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
                    ) {
                        groups.forEach { group ->
                            ProxyGroupChip(
                                group = group,
                                selected = group.id == selectedGroupId,
                                colors = colors,
                                disabledTitle = disabledTitle,
                                serverCountText = serverCountText,
                                onSelect = onSelect,
                                onGroupAction = onGroupAction,
                                modifier = Modifier.weight(1f).widthIn(min = 160.dp),
                                fillWidth = true,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
                    ) {
                        groups.forEach { group ->
                            ProxyGroupChip(
                                group = group,
                                selected = group.id == selectedGroupId,
                                colors = colors,
                                disabledTitle = disabledTitle,
                                serverCountText = serverCountText,
                                onSelect = onSelect,
                                onGroupAction = onGroupAction,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyGroupChip(
    group: SkipiProxyGroupItem,
    selected: Boolean,
    colors: SkipiProxyGroupSelectorColors,
    disabledTitle: @Composable (String) -> String,
    serverCountText: @Composable (Int) -> String,
    onSelect: (String) -> Unit,
    onGroupAction: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
) {
    val chipBackgroundColor by animateColorAsState(
        targetValue = if (selected) colors.selectedSurface else colors.raisedSurface,
        animationSpec = tween(200),
        label = "skipiProxyGroupChipBackground",
    )
    val chipBorderColor by animateColorAsState(
        targetValue = if (selected) colors.selectedBorder else colors.selectedBorder.copy(alpha = 0f),
        animationSpec = tween(200),
        label = "skipiProxyGroupChipBorder",
    )

    Surface(
        modifier = modifier
            .clip(SkipiTheme.shapes.small)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(group.id) },
            ),
        color = chipBackgroundColor,
        shape = SkipiTheme.shapes.small,
        border = BorderStroke(1.dp, chipBorderColor),
    ) {
        Row(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .padding(
                    start = SkipiTheme.spacing.medium,
                    top = SkipiTheme.spacing.small,
                    bottom = SkipiTheme.spacing.small,
                    end = if (group.actions.isEmpty()) SkipiTheme.spacing.medium else SkipiTheme.spacing.extraSmall,
                ),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            val textModifier = if (fillWidth) Modifier.weight(1f) else Modifier.widthIn(min = 96.dp, max = 220.dp)
            Column(modifier = textModifier) {
                Text(
                    text = if (group.enabled) group.title else disabledTitle(group.title),
                    color = if (!group.enabled) colors.mutedText else if (selected) colors.selectedText else colors.text,
                    style = SkipiTheme.typography.bodyMedium,
                    fontWeight = themedFontWeight(FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = serverCountText(group.serverCount),
                    color = if (selected) colors.selectedMutedText else colors.mutedText,
                    style = SkipiTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (group.actions.isNotEmpty()) {
                var expanded by remember(group.id) { mutableStateOf(false) }
                val groupMenuDescription = stringResource(Res.string.common_more) + ": " + group.title
                androidx.compose.foundation.layout.Box {
                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.semantics { contentDescription = groupMenuDescription },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = if (selected) colors.selectedText else colors.text,
                        )
                    }
                    SkipiHomeDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        surface = colors.raisedSurface,
                        text = colors.text,
                        outline = colors.border,
                    ) {
                        group.actions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.title) },
                                enabled = action.enabled,
                                colors = skipiHomeDropdownItemColors(colors.text, colors.mutedText),
                                onClick = {
                                    expanded = false
                                    onGroupAction(group.id, action.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
