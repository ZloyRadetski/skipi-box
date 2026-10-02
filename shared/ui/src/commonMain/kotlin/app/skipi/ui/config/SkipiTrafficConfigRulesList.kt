// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.configs_edit
import app.skipi.ui.resources.configs_rules_add
import app.skipi.ui.resources.configs_rules_final_missing
import app.skipi.ui.resources.configs_rules_order
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.text.themedFontWeight
import features.config.ShadowrocketRule
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class TrafficConfigRuleListItem(
    val id: Long,
    val rule: ShadowrocketRule,
)

/** Profile list and original long-press reorder UI; the host supplies profile operations and haptics. */
@Composable
fun SkipiTrafficConfigRulesList(
    rules: List<TrafficConfigRuleListItem>,
    finalRule: ShadowrocketRule?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    reorderBottomPadding: Dp,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onEdit: (ShadowrocketRule) -> Unit,
    onDelete: (ShadowrocketRule) -> Unit,
    onEditFinal: () -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onHapticFeedback: (HapticFeedbackType) -> Unit,
) {
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnHapticFeedback = rememberUpdatedState(onHapticFeedback)
    val itemCount = rules.size
    val scrollThresholdPadding = remember(reorderBottomPadding) {
        PaddingValues(top = 0.dp, bottom = reorderBottomPadding)
    }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThresholdPadding = scrollThresholdPadding,
    ) { from, to ->
        val fromIndex = from.index - 2
        val toIndex = to.index - 2
        if (fromIndex == toIndex || fromIndex !in 0 until itemCount || toIndex !in 0 until itemCount) {
            return@rememberReorderableLazyListState
        }
        currentOnMove.value(fromIndex, toIndex)
        currentOnHapticFeedback.value(HapticFeedbackType.SegmentFrequentTick)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().then(modifier),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "order") {
            Text(
                text = stringResource(Res.string.configs_rules_order),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            )
        }
        item(key = "add") {
            TextButton(
                text = stringResource(Res.string.configs_rules_add),
                onClick = onAdd,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        items(items = rules, key = TrafficConfigRuleListItem::id) { item ->
            ReorderableItem(
                state = reorderableState,
                key = item.id,
                enabled = rules.size > 1,
                animateItemModifier = Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = folmeSpring(damping = 0.9f, response = 0.38f),
                ),
            ) { isDragging ->
                TrafficConfigVisualRuleCard(
                    rule = item.rule,
                    isDragging = isDragging,
                    dragModifier = Modifier.longPressDraggableHandle(
                        enabled = rules.size > 1,
                        onDragStarted = {
                            currentOnHapticFeedback.value(HapticFeedbackType.LongPress)
                        },
                        onDragStopped = {
                            currentOnHapticFeedback.value(HapticFeedbackType.GestureEnd)
                        },
                    ),
                    onEdit = { onEdit(item.rule) },
                    onDelete = { onDelete(item.rule) },
                )
            }
        }
        item(key = "fallback") {
            TrafficConfigFinalRuleCard(rule = finalRule, onEdit = onEditFinal)
        }
    }
}

@Composable
private fun TrafficConfigVisualRuleCard(
    rule: ShadowrocketRule,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "visualRuleDragScale",
    )
    val animatedShadowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "visualRuleDragShadowAlpha",
    )
    val shadowColor = SkipiTheme.colors.onSurface.copy(alpha = 0.20f)

    Card(
        modifier = Modifier.fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
            .drawDraggedCardShadow(animatedShadowAlpha, shadowColor)
            .then(dragModifier)
            .clickable(onClick = onEdit),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${rule.type}, ${rule.value}",
                    fontSize = 15.sp,
                    fontWeight = themedFontWeight(FontWeight.Medium),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rule.policy,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(MiuixIcons.Edit, contentDescription = stringResource(Res.string.configs_edit), tint = MiuixTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDelete) {
                Icon(MiuixIcons.Delete, contentDescription = stringResource(Res.string.common_delete), tint = MiuixTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun TrafficConfigFinalRuleCard(rule: ShadowrocketRule?, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "FINAL",
                    fontSize = 15.sp,
                    fontWeight = themedFontWeight(FontWeight.Medium),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rule?.policy ?: stringResource(Res.string.configs_rules_final_missing),
                    fontSize = 12.sp,
                    color = if (rule != null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(MiuixIcons.Edit, contentDescription = stringResource(Res.string.configs_edit), tint = MiuixTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun Modifier.drawDraggedCardShadow(alpha: Float, color: Color): Modifier {
    if (alpha <= 0f) return this
    return drawBehind {
        val cornerRadius = CardDefaults.CornerRadius.toPx()
        val maxSpread = 12.dp.toPx()
        for (step in 12 downTo 1) {
            val spread = maxSpread * (step / 12f)
            val layerAlpha = alpha * 0.035f * (1f - (step - 1f) / 12f)
            drawRoundRect(
                color = color.copy(alpha = layerAlpha),
                topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread),
                size = androidx.compose.ui.geometry.Size(size.width + spread * 2, size.height + spread * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius + spread),
            )
        }
    }
}
