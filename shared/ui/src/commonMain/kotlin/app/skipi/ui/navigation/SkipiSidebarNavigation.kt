// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Width of the open desktop sidebar: wide enough for an icon and the longest label. */
private val SidebarWidth = 196.dp

/** Width of the collapsed sidebar: the icons only. */
private val CollapsedSidebarWidth = 68.dp

/** The width from which the labels start to fade in while the sidebar opens: past the icon and its padding. */
private val LabelFadeStart = 110.dp

/**
 * The desktop navigation: a full-height column at the window's left edge with one row per destination
 * (icon and label), the selected one on an accent pill and the one under the pointer lightly tinted.
 * It takes the same items and colors as [SkipiExpressiveNavigationBar], so a host switches between the two.
 */
@Composable
fun SkipiSidebarNavigation(
    selectedDestination: SkipiMainDestination,
    onSelect: (SkipiMainDestination) -> Unit,
    colors: SkipiExpressiveNavigationColors,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    items: List<SkipiNavigationItem> = defaultSkipiNavigationItems(),
    /** Pinned shortcuts listed under the destinations, each running its action instead of selecting a section. */
    actions: List<SkipiNavigationAction> = emptyList(),
) {
    val width by animateDpAsState(if (expanded) SidebarWidth else CollapsedSidebarWidth)
    val borderColor = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    // The labels follow the animated width, not the target: they fade in as the sidebar opens and are gone
    // as soon as it starts to close, so they are never squeezed into a half-open column.
    val labelAlpha = if (expanded) {
        ((width - LabelFadeStart) / (SidebarWidth - LabelFadeStart)).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .clipToBounds()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                SidebarItem(
                    label = item.label,
                    icon = item.icon,
                    selected = item.destination == selectedDestination,
                    labelAlpha = labelAlpha,
                    colors = colors,
                    onClick = { onSelect(item.destination) },
                )
            }
            if (actions.isNotEmpty()) {
                Box(Modifier.padding(vertical = 8.dp, horizontal = 4.dp).fillMaxWidth().height(1.dp).background(borderColor))
                actions.forEach { action ->
                    SidebarItem(
                        label = action.label,
                        icon = action.icon,
                        selected = false,
                        labelAlpha = labelAlpha,
                        colors = colors,
                        onClick = action.onClick,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (onExpandedChange != null) {
                SidebarToggle(expanded = expanded, colors = colors, onClick = { onExpandedChange(!expanded) })
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(borderColor))
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    labelAlpha: Float,
    colors: SkipiExpressiveNavigationColors,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> colors.accent.copy(alpha = if (colors.isDark) 0.30f else 0.16f)
            hovered -> colors.onSurfaceVariant.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
    )
    val content by animateColorAsState(if (selected) colors.accent else colors.onSurfaceVariant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .hoverable(interaction)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = content, modifier = Modifier.size(22.dp))
        if (labelAlpha > 0f) {
            Text(
                text = label,
                color = content.copy(alpha = content.alpha * labelAlpha),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The fold button at the foot of the sidebar: a chevron that points where the sidebar will move. */
@Composable
private fun SidebarToggle(
    expanded: Boolean,
    colors: SkipiExpressiveNavigationColors,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered) colors.onSurfaceVariant.copy(alpha = 0.10f) else Color.Transparent,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (expanded) "\u2039" else "\u203A",
            color = colors.onSurfaceVariant,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
