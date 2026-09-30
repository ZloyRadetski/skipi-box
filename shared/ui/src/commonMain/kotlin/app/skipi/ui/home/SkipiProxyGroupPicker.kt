// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
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
import app.skipi.ui.text.themedFontWeight
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

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "skipiProxyGroupPickerChevron",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (expanded) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "skipiProxyGroupPickerScale",
    )
    val cardBorder by animateColorAsState(
        targetValue = if (expanded) colors.accent.copy(alpha = 0.5f) else colors.border,
        animationSpec = tween(durationMillis = 200),
        label = "skipiProxyGroupPickerBorder",
    )
    val cardSurface by animateColorAsState(
        targetValue = if (expanded) colors.raisedSurface.copy(alpha = 0.5f) else colors.surface,
        animationSpec = tween(durationMillis = 200),
        label = "skipiProxyGroupPickerSurface",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
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
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedGroup.title,
                        style = SkipiTheme.typography.titleSmall,
                        fontSize = 15.sp,
                        fontWeight = themedFontWeight(FontWeight.SemiBold),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = selectedGroup.subtitle,
                        style = SkipiTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation },
                    tint = if (expanded) colors.accent else colors.mutedText,
                )
            }
        }
        SkipiHomeDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            surface = colors.raisedSurface,
            text = colors.text,
            outline = colors.border,
        ) {
            groups.forEach { group ->
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
