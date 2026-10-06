// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.home.SkipiProxyServerFlagBadge
import app.skipi.ui.subscription.SubscriptionProviderBody
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.text.themedFontWeight
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SkipiRoutingOutboundGroupDetails(
    val traffic: String? = null,
    val expiry: String? = null,
    val trafficProgress: Float? = null,
    val announcement: String = "",
    val updatedLabel: String? = null,
)

data class SkipiRoutingOutboundOption(
    val key: String,
    val tag: String,
    val title: String,
    val subtitle: String? = null,
    val flag: String? = null,
)

data class SkipiRoutingOutboundGroup(
    val key: String,
    val title: String,
    val countLabel: String,
    val items: List<SkipiRoutingOutboundOption>,
    val details: SkipiRoutingOutboundGroupDetails? = null,
)

@Composable
fun SkipiRoutingOutboundGroupHeader(
    title: String,
    countLabel: String,
    itemCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    details: SkipiRoutingOutboundGroupDetails? = null,
) {
    val borderColor = SkipiTheme.colors.onSurface.copy(alpha = 0.14f)
    val surface = SkipiTheme.colors.surface
    val radius = 18.dp
    val headerShape = if (expanded && itemCount > 0) RoundedCornerShape(topStart = radius, topEnd = radius) else RoundedCornerShape(radius)
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp)
            .clip(headerShape).background(surface).border(1.dp, borderColor, headerShape),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = { onExpandedChange(!expanded) }).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = themedFontWeight(FontWeight.SemiBold), color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(countLabel, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            if (details?.traffic != null || details?.expiry != null) {
                Spacer(Modifier.height(10.dp))
                details.traffic?.let { Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                details.expiry?.let {
                    if (details.traffic != null) Spacer(Modifier.height(4.dp))
                    Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                details.trafficProgress?.let { progress ->
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(SkipiTheme.colors.onSurface.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(progress).height(6.dp).background(SkipiTheme.colors.onSurface.copy(alpha = 0.70f)))
                    }
                }
            }
            if (expanded && !details?.announcement.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(details!!.announcement, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            details?.updatedLabel?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SkipiRoutingOutboundOptionRow(
    flag: String?,
    fallbackPainter: Painter? = null,
    displayTitle: String,
    subtitle: String? = null,
    selected: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val radius = 18.dp
    SubscriptionProviderBody(
        color = SkipiTheme.colors.surface,
        borderColor = SkipiTheme.colors.onSurface.copy(alpha = 0.14f),
        isLastItem = isLast,
        bottomCornerRadius = radius,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipiProxyServerFlagBadge(
                    flag = flag,
                    fallbackPainter = fallbackPainter,
                    containerColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    fallbackTint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.85f),
                    size = 32.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
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

@Composable
fun SkipiRoutingOutboundEmptyGroupRow(message: String) {
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
