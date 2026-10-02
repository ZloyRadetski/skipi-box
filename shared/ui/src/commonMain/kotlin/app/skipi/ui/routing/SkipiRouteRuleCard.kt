// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.routing_delete
import app.skipi.ui.resources.routing_domain_label
import app.skipi.ui.resources.routing_domain_strategy
import app.skipi.ui.resources.routing_edit
import app.skipi.ui.resources.routing_ip_label
import app.skipi.ui.resources.routing_network_label
import app.skipi.ui.resources.routing_outbound_tag_label
import app.skipi.ui.resources.routing_port_label
import app.skipi.ui.resources.routing_process_label
import app.skipi.ui.resources.routing_protocol_label
import app.skipi.ui.theme.SkipiTheme
import features.routing.model.RouteRule
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SkipiRouteRuleCard(
    rule: RouteRule,
    outboundLabel: String,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).then(dragModifier),
        colors = CardDefaults.defaultColors(color = if (isDragging) SkipiTheme.colors.surfaceVariant else SkipiTheme.colors.surface),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.remarks, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${stringResource(Res.string.routing_outbound_tag_label)}: $outboundLabel", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(4.dp))
            RuleLine(stringResource(Res.string.routing_domain_label), rule.domain.joinToString())
            RuleLine(stringResource(Res.string.routing_ip_label), rule.ip.joinToString())
            RuleLine(stringResource(Res.string.routing_process_label), rule.process.joinToString())
            RuleLine(stringResource(Res.string.routing_port_label), rule.port)
            RuleLine(stringResource(Res.string.routing_protocol_label), rule.protocol)
            RuleLine(stringResource(Res.string.routing_network_label), rule.network)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(MiuixIcons.Edit, contentDescription = stringResource(Res.string.routing_edit), tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(MiuixIcons.Delete, contentDescription = stringResource(Res.string.routing_delete), tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun RuleLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text("$label: ", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SkipiRoutingPolicyCard(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayDropdownPreference(
        title = stringResource(Res.string.routing_domain_strategy),
        items = options,
        selectedIndex = selectedIndex.coerceIn(options.indices),
        onSelectedIndexChange = onSelectedIndexChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
