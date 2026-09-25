// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
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

data class SkipiProxyServerCompactListCardState(
    val flag: String?,
    val title: String,
    val summary: String,
    val protocol: String,
    val protocolColor: Color,
    val transport: String?,
    val transportTextColor: Color,
    val transportContainerColor: Color,
    val selected: Boolean,
    val inSubscriptionGroup: Boolean,
    val latencyText: String?,
    val latencyTesting: Boolean,
    val latencyColor: Color,
    val progressColor: Color,
    val isStrategyGroup: Boolean,
    val isDragging: Boolean,
)

data class SkipiProxyServerCompactListCardColors(
    val surface: Color,
    val selectedSurface: Color,
    val selectedBorder: Color,
)

/**
 * Shared compact Home server card.
 *
 * [onLongPress] and [overlay] keep platform-specific haptics and action menus
 * at the host boundary while sharing the compact interaction surface itself.
 */
@Composable
fun SkipiProxyServerCompactListCard(
    state: SkipiProxyServerCompactListCardState,
    colors: SkipiProxyServerCompactListCardColors,
    fallbackBadgePainter: Painter,
    titleFontWeight: FontWeight,
    latencyFontWeight: FontWeight,
    onSelect: () -> Unit,
    onLongPress: (Offset) -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    dragVisualModifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    val selectedShape = SkipiTheme.shapes.small
    val compactCardHeight = if (state.isStrategyGroup) 58.dp else 66.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(compactCardHeight)
            .zIndex(if (state.isDragging) 1f else 0f)
            .then(dragVisualModifier)
            .clip(selectedShape)
            .border(
                width = if (state.selected) 1.dp else 0.dp,
                color = if (state.selected) colors.selectedBorder else Color.Transparent,
                shape = selectedShape,
            )
            .then(dragModifier),
    ) {
        Card(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(onSelect, onLongPress) {
                    detectTapGestures(
                        onTap = { onSelect() },
                        onLongPress = onLongPress,
                    )
                },
            colors = CardDefaults.defaultColors(
                color = when {
                    state.selected -> colors.selectedSurface
                    state.inSubscriptionGroup -> Color.Transparent
                    else -> colors.surface
                },
            ),
                insideMargin = PaddingValues(SkipiTheme.spacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipiProxyServerFlagBadge(
                    flag = state.flag,
                    fallbackPainter = fallbackBadgePainter,
                    containerColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    fallbackTint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.85f),
                    size = 32.dp,
                    shapeRadius = 8.dp,
                )
                Spacer(Modifier.width(SkipiTheme.spacing.small))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = state.title,
                        style = SkipiTheme.typography.titleSmall,
                        fontWeight = titleFontWeight,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SkipiTheme.spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkipiProxyProtocolChip(
                            text = state.protocol,
                            chipColor = state.protocolColor,
                            modifier = Modifier.weight(1f, fill = false),
                            compact = true,
                            selected = state.selected,
                            fontWeight = titleFontWeight,
                        )
                        state.transport?.takeIf(String::isNotBlank)?.let { transport ->
                            Spacer(Modifier.width(SkipiTheme.spacing.extraSmall))
                            SkipiProxyTransportChip(
                                text = transport,
                                textColor = state.transportTextColor,
                                containerColor = state.transportContainerColor,
                                modifier = Modifier.weight(1f, fill = false),
                                compact = true,
                                fontWeight = latencyFontWeight,
                            )
                        }
                        Spacer(Modifier.width(SkipiTheme.spacing.small))
                        Text(
                            text = state.summary,
                            modifier = Modifier.weight(1f),
                            style = SkipiTheme.typography.bodySmall,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        when {
                            state.latencyTesting -> {
                                Spacer(Modifier.width(SkipiTheme.spacing.small))
                                InfiniteProgressIndicator(
                                    color = state.progressColor,
                                    size = 12.dp,
                                    strokeWidth = 1.8.dp,
                                )
                            }

                            !state.latencyText.isNullOrBlank() -> {
                                Spacer(Modifier.width(SkipiTheme.spacing.small))
                                Text(
                                    text = state.latencyText,
                                    style = SkipiTheme.typography.labelSmall,
                                    fontWeight = latencyFontWeight,
                                    color = state.latencyColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        overlay()
    }
}
