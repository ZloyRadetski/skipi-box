// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.skipi.ui.theme.SkipiTheme

data class SkipiProxyServerExpandedListCardState(
    val flag: String?,
    val title: String,
    val summary: String,
    val protocol: String,
    val protocolColor: Color,
    val transport: String?,
    val transportTextColor: Color,
    val transportContainerColor: Color,
    val selected: Boolean,
    val groupName: String?,
    val latencyText: String?,
    val latencyTesting: Boolean,
    val latencyColor: Color,
    val progressColor: Color,
    val isStrategyGroup: Boolean,
    val isDragging: Boolean,
)

data class SkipiProxyServerExpandedListCardColors(
    val surface: Color,
    val selectedSurface: Color,
    val selectedBorder: Color,
)

/**
 * Shared expanded server card used by the Proxy Home list.
 *
 * Platform adapters supply theme colors, fallback assets and the action-menu
 * slot. This keeps storage, clipboard, QR, drag-and-drop and haptic behavior
 * outside shared UI while sharing all visual layout and state rendering.
 */
@Composable
fun SkipiProxyServerExpandedListCard(
    state: SkipiProxyServerExpandedListCardState,
    colors: SkipiProxyServerExpandedListCardColors,
    fallbackBadgePainter: Painter,
    titleFontWeight: FontWeight,
    latencyFontWeight: FontWeight,
    onSelect: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    dragVisualModifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    val selectedShape = if (state.isStrategyGroup) SkipiTheme.shapes.small else SkipiTheme.shapes.medium
    val cardInsideMargin = if (state.isStrategyGroup) {
        PaddingValues(horizontal = SkipiTheme.spacing.medium, vertical = SkipiTheme.spacing.small)
    } else {
        PaddingValues(SkipiTheme.spacing.medium)
    }
    val cardBottomPadding = if (state.isStrategyGroup) SkipiTheme.spacing.small else SkipiTheme.spacing.medium
    val badgeSize = if (state.isStrategyGroup) 28.dp else 34.dp
    val badgeRadius = if (state.isStrategyGroup) 7.dp else 8.dp
    val middleSpacerHeight = if (state.isStrategyGroup) SkipiTheme.spacing.small else SkipiTheme.spacing.medium

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SkipiTheme.spacing.medium)
            .padding(bottom = cardBottomPadding)
            .zIndex(if (state.isDragging) 1f else 0f)
            .then(dragVisualModifier)
            .clip(selectedShape)
            .border(
                width = if (state.selected) 1.dp else 0.dp,
                color = if (state.selected) colors.selectedBorder else Color.Transparent,
                shape = selectedShape,
            )
            .then(dragModifier),
        colors = CardDefaults.defaultColors(
            color = if (state.selected) colors.selectedSurface else colors.surface,
        ),
        insideMargin = cardInsideMargin,
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipiProxyServerFlagBadge(
                    flag = state.flag,
                    fallbackPainter = fallbackBadgePainter,
                    containerColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    fallbackTint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.85f),
                    size = badgeSize,
                    shapeRadius = badgeRadius,
                )
                Spacer(Modifier.width(if (state.isStrategyGroup) SkipiTheme.spacing.small else SkipiTheme.spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = if (state.isStrategyGroup) SkipiTheme.typography.titleSmall else SkipiTheme.typography.titleMedium,
                        fontWeight = titleFontWeight,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.summary,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            state.groupName?.let { groupName ->
                Spacer(Modifier.width(SkipiTheme.spacing.medium))
                Text(
                    text = groupName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(middleSpacerHeight))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkipiProxyProtocolChip(
                    text = state.protocol,
                    chipColor = state.protocolColor,
                    compact = state.isStrategyGroup,
                    selected = state.selected,
                    fontWeight = titleFontWeight,
                )
                state.transport?.takeIf(String::isNotBlank)?.let { transport ->
                    Spacer(Modifier.width(SkipiTheme.spacing.extraSmall))
                    SkipiProxyTransportChip(
                        text = transport,
                        textColor = state.transportTextColor,
                        containerColor = state.transportContainerColor,
                        compact = state.isStrategyGroup,
                        fontWeight = latencyFontWeight,
                    )
                }
                when {
                    state.latencyTesting -> {
                        Spacer(Modifier.width(SkipiTheme.spacing.small))
                        InfiniteProgressIndicator(
                            color = state.progressColor,
                            size = if (state.isStrategyGroup) 12.dp else 14.dp,
                            strokeWidth = 2.dp,
                        )
                    }

                    !state.latencyText.isNullOrBlank() -> {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = state.latencyText,
                            fontSize = if (state.isStrategyGroup) 13.sp else 14.sp,
                            fontWeight = latencyFontWeight,
                            color = state.latencyColor,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
