// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.home.SkipiProxyServerFlagBadge
import app.skipi.ui.subscription.SubscriptionProviderBody
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.subscription_provider_servers
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.text.themedFontWeight
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Platform-formatted subscription details displayed in a source-group heading. */
data class ServerPickerSubscriptionSummary(
    val traffic: String? = null,
    val expiry: String? = null,
    val trafficProgress: Float? = null,
    val announce: String? = null,
    val lastUpdated: String? = null,
)

@Composable
fun SkipiServerPickerGroupHeader(
    title: String,
    count: Int,
    summary: ServerPickerSubscriptionSummary? = null,
    expanded: Boolean,
    toggleState: ToggleableState? = null,
    onToggleGroup: (() -> Unit)? = null,
    onExpandedChange: (Boolean) -> Unit,
) {
    val borderColor = SkipiTheme.colors.onSurface.copy(alpha = 0.14f)
    val cornerRadius = 18.dp
    val shape = if (expanded && count > 0) RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius) else RoundedCornerShape(cornerRadius)
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp)
            .clip(shape).background(SkipiTheme.colors.surface).border(1.dp, borderColor, shape),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = { onExpandedChange(!expanded) }).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore, null, tint = MiuixTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = themedFontWeight(FontWeight.SemiBold), color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(Res.string.subscription_provider_servers, count), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (toggleState != null && onToggleGroup != null) Checkbox(state = toggleState, onClick = onToggleGroup, enabled = count > 0)
            }
            if (summary?.traffic != null || summary?.expiry != null) {
                Spacer(Modifier.height(10.dp))
                summary.traffic?.let { Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                summary.expiry?.let { if (summary.traffic != null) Spacer(Modifier.height(4.dp)); Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                summary.trafficProgress?.let { progress ->
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(SkipiTheme.colors.onSurface.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).background(SkipiTheme.colors.onSurface.copy(alpha = 0.70f)))
                    }
                }
            }
            if (expanded && !summary?.announce.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(summary!!.announce!!, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            summary?.lastUpdated?.let { Spacer(Modifier.height(10.dp)); Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun SkipiServerPickerEmptyGroupRow(message: String) {
    SubscriptionProviderBody(
        color = SkipiTheme.colors.surface,
        borderColor = SkipiTheme.colors.onSurface.copy(alpha = 0.14f),
        isLastItem = true,
        bottomCornerRadius = 18.dp,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Text(message, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

@Composable
fun SkipiServerPickerItemRow(
    flag: String? = null,
    displayTitle: String,
    subtitle: String? = null,
    selected: Boolean,
    isLast: Boolean,
    fallbackPainter: Painter? = null,
    flagContainerColor: Color = Color.White.copy(alpha = 0.07f),
    flagFallbackTint: Color = Color.White.copy(alpha = 0.65f),
    onClick: () -> Unit,
) {
    SubscriptionProviderBody(
        color = SkipiTheme.colors.surface,
        borderColor = SkipiTheme.colors.onSurface.copy(alpha = 0.14f),
        isLastItem = isLast,
        bottomCornerRadius = 18.dp,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SkipiProxyServerFlagBadge(flag = flag, fallbackPainter = fallbackPainter, containerColor = flagContainerColor, fallbackTint = flagFallbackTint, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayTitle, fontSize = 15.sp, fontWeight = themedFontWeight(FontWeight.Medium), color = if (selected) SkipiTheme.colors.accent else MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Checkbox(state = ToggleableState(selected), onClick = onClick)
            }
            if (!isLast) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = SkipiTheme.colors.onSurface.copy(alpha = 0.08f))
        }
    }
}
