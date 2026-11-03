// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.ui.components.onSecondaryClick
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.home_action_search
import app.skipi.ui.text.themedFontWeight
import org.jetbrains.compose.resources.stringResource
import app.skipi.ui.theme.SkipiTheme

data class SkipiProxyGroupPickerItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val pickerText: String,
)

data class SkipiProxyGroupPickerColors(
    val surface: Color,
    val raisedSurface: Color,
    val border: Color,
    val accent: Color,
    val text: Color,
    val mutedText: Color,
)

data class SkipiProxyGroupPickerAction(
    val id: String,
    val title: String,
)

/**
 * Shared selected-group surface and popup picker. Hosts keep only their data,
 * localized labels, haptics, and optional group-management actions.
 */
@Composable
fun SkipiProxyGroupPicker(
    groups: List<SkipiProxyGroupPickerItem>,
    selectedGroupId: String?,
    colors: SkipiProxyGroupPickerColors,
    onGroupSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPickerToggled: (() -> Unit)? = null,
    onSelectedGroupLongClick: ((String) -> Unit)? = null,
    contextActions: (String) -> List<SkipiProxyGroupPickerAction> = { emptyList() },
    onContextAction: (groupId: String, actionId: String) -> Unit = { _, _ -> },
) {
    if (groups.isEmpty()) return
    val selectedGroup = groups.firstOrNull { group -> group.id == selectedGroupId } ?: groups.first()
    var expanded by remember { mutableStateOf(false) }
    var contextGroupId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipiProxyGroupPickerChevron",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier.fillMaxWidth()) {
        // A section title for the list below, not a card: the picker is secondary to the connection and the servers.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .onSecondaryClick {
                    if (contextActions(selectedGroup.id).isNotEmpty()) {
                        expanded = false
                        onSelectedGroupLongClick?.invoke(selectedGroup.id)
                        contextGroupId = selectedGroup.id
                    }
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        onPickerToggled?.invoke()
                        expanded = !expanded
                    },
                    onLongClick = {
                        if (contextActions(selectedGroup.id).isNotEmpty()) {
                            expanded = false
                            onSelectedGroupLongClick?.invoke(selectedGroup.id)
                            contextGroupId = selectedGroup.id
                        }
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedGroup.title,
                style = SkipiTheme.typography.titleSmall,
                fontSize = 15.sp,
                fontWeight = themedFontWeight(FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = selectedGroup.subtitle,
                style = SkipiTheme.typography.bodySmall,
                fontSize = 13.sp,
                color = colors.mutedText,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation },
                tint = if (expanded) colors.accent else colors.mutedText,
            )
        }
        SkipiHomeDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = ""
            },
            surface = colors.raisedSurface,
            text = colors.text,
            outline = colors.border,
        ) {
            if (groups.size > SearchThreshold) {
                GroupSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    textColor = colors.text,
                    hintColor = colors.mutedText,
                    accent = colors.accent,
                )
            }
            val visibleGroups = if (query.isBlank()) {
                groups
            } else {
                groups.filter { group -> group.title.contains(query.trim(), ignoreCase = true) }
            }
            visibleGroups.forEach { group ->
                val isSelected = group.id == selectedGroup.id
                DropdownMenuItem(
                    modifier = Modifier.semantics {
                        contentDescription = group.pickerText
                        selected = isSelected
                    },
                    text = {
                        Column {
                            Text(
                                text = group.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = group.subtitle,
                                style = SkipiTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.mutedText,
                            )
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    colors = skipiHomeDropdownItemColors(colors.text, colors.mutedText),
                    onClick = {
                        expanded = false
                        query = ""
                        onGroupSelected(group.id)
                    },
                )
            }
        }
        val selectedContextGroupId = contextGroupId
        if (selectedContextGroupId != null) {
            val actions = contextActions(selectedContextGroupId)
            SkipiHomeDropdownMenu(
                expanded = actions.isNotEmpty(),
                onDismissRequest = { contextGroupId = null },
                surface = colors.raisedSurface,
                text = colors.text,
                outline = colors.border,
            ) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.title) },
                        colors = skipiHomeDropdownItemColors(colors.text, colors.mutedText),
                        onClick = {
                            contextGroupId = null
                            onContextAction(selectedContextGroupId, action.id)
                        },
                    )
                }
            }
        }
    }
}

/** More groups than this and the popup gets a search field; a handful fit on screen without one. */
private const val SearchThreshold = 6

@Composable
private fun GroupSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    textColor: Color,
    hintColor: Color,
    accent: Color,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(min = 200.dp)) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = SkipiTheme.typography.bodyMedium.copy(color = textColor),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        if (query.isEmpty()) {
            Text(
                text = stringResource(Res.string.home_action_search),
                color = hintColor,
                style = SkipiTheme.typography.bodyMedium,
            )
        }
    }
}
