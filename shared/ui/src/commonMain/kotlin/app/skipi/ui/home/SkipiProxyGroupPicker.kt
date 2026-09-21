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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedGroup.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = selectedGroup.subtitle,
                        fontSize = 12.sp,
                        color = colors.mutedText,
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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = group.pickerText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
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
            DropdownMenu(
                expanded = actions.isNotEmpty(),
                onDismissRequest = { contextGroupId = null },
            ) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.title) },
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
