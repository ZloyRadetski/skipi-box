// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.resources.common_copy
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_edit
import app.skipi.ui.resources.proxy_server_list_latency_test
import org.jetbrains.compose.resources.stringResource

enum class SkipiProxyServerLatencyKind {
    Success,
    Error,
}

data class SkipiProxyServerLatency(
    val text: String,
    val kind: SkipiProxyServerLatencyKind,
)

data class SkipiProxyServerCardState(
    val iconText: String,
    val title: String,
    val address: String,
    val protocol: String,
    val transport: String?,
    val selected: Boolean,
    val latency: SkipiProxyServerLatency?,
    val testingLatency: Boolean,
    val canTest: Boolean,
    val canCopy: Boolean,
    val canEdit: Boolean,
)

data class SkipiProxyServerCardActions(
    val onSelect: () -> Unit,
    val onTest: () -> Unit,
    val onCopy: () -> Unit,
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
)

data class SkipiProxyServerCardLabels(
    val testContentDescription: String,
    val copyContentDescription: String,
    val editContentDescription: String,
    val deleteContentDescription: String,
)

@Composable
fun defaultSkipiProxyServerCardLabels(): SkipiProxyServerCardLabels = SkipiProxyServerCardLabels(
    testContentDescription = stringResource(Res.string.proxy_server_list_latency_test),
    copyContentDescription = stringResource(Res.string.common_copy),
    editContentDescription = stringResource(Res.string.common_edit),
    deleteContentDescription = stringResource(Res.string.common_delete),
)

data class SkipiProxyServerCardColors(
    val surface: Color,
    val raisedSurface: Color,
    val selectedSurface: Color,
    val selectedBorder: Color,
    val text: Color,
    val mutedText: Color,
    val success: Color,
    val error: Color,
    val selectedText: Color = text,
    val selectedMutedText: Color = mutedText,
)

/**
 * Shared Home card for a single proxy server.
 *
 * Hosts map their Core model and platform capabilities into [state]. Connection
 * testing, clipboard access, editing and deletion remain injected user intents.
 */
@Composable
fun SkipiProxyServerCard(
    state: SkipiProxyServerCardState,
    actions: SkipiProxyServerCardActions,
    colors: SkipiProxyServerCardColors,
    modifier: Modifier = Modifier,
    labels: SkipiProxyServerCardLabels = defaultSkipiProxyServerCardLabels(),
) {
    Card(
        onClick = actions.onSelect,
        modifier = modifier.fillMaxWidth().semantics { selected = state.selected },
        shape = SkipiTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (state.selected) colors.selectedSurface else colors.surface,
        ),
        border = BorderStroke(1.dp, if (state.selected) colors.selectedBorder else Color.Transparent),
    ) {
        Column(
            modifier = Modifier.padding(SkipiTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(SkipiTheme.shapes.small)
                        .background(colors.raisedSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.iconText, style = SkipiTheme.typography.headlineSmall)
                }
                Spacer(Modifier.width(SkipiTheme.spacing.small))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        color = if (state.selected) colors.selectedText else colors.text,
                        style = SkipiTheme.typography.titleLarge,
                        fontWeight = themedFontWeight(FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.address,
                        color = if (state.selected) colors.selectedMutedText else colors.mutedText,
                        style = SkipiTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SkipiProtocolChip(text = state.protocol, selected = state.selected, colors = colors)
                state.transport?.takeIf(String::isNotBlank)?.let { transport ->
                    Spacer(Modifier.width(SkipiTheme.spacing.extraSmall))
                    SkipiTransportChip(text = transport, selected = state.selected, colors = colors)
                }
                state.latency?.let { latency ->
                    Spacer(Modifier.width(SkipiTheme.spacing.small))
                    Text(
                        text = latency.text,
                        color = if (latency.kind == SkipiProxyServerLatencyKind.Success) colors.success else colors.error,
                        style = if (latency.kind == SkipiProxyServerLatencyKind.Success) {
                            SkipiTheme.typography.labelLarge
                        } else {
                            SkipiTheme.typography.labelMedium
                        },
                        fontWeight = themedFontWeight(FontWeight.Bold),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = actions.onTest, enabled = !state.testingLatency && state.canTest) {
                    Icon(Icons.Outlined.HourglassEmpty, labels.testContentDescription, tint = colors.text)
                }
                IconButton(onClick = actions.onCopy, enabled = state.canCopy) {
                    Icon(Icons.Outlined.ContentCopy, labels.copyContentDescription, tint = colors.text)
                }
                IconButton(onClick = actions.onEdit, enabled = state.canEdit) {
                    Icon(Icons.Outlined.Edit, labels.editContentDescription, tint = colors.text)
                }
                IconButton(onClick = actions.onDelete) {
                    Icon(Icons.Outlined.Delete, labels.deleteContentDescription, tint = colors.text)
                }
            }
        }
    }
}

@Composable
private fun SkipiProtocolChip(
    text: String,
    selected: Boolean,
    colors: SkipiProxyServerCardColors,
) {
    val chipColor = when (text.lowercase()) {
        "vless", "vmess" -> Color(0xFF5C6DB0)
        "hysteria2", "hy2" -> Color(0xFFB65A3D)
        "shadowsocks", "ss" -> Color(0xFF387D58)
        else -> Color(0xFF426A4B)
    }
    Surface(
        color = chipColor.copy(alpha = if (selected) 0.85f else 0.45f),
        shape = SkipiTheme.shapes.extraSmall,
    ) {
        Text(
            text = text.uppercase(),
            color = colors.text,
            style = SkipiTheme.typography.labelMedium,
            fontWeight = themedFontWeight(FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SkipiTransportChip(
    text: String,
    selected: Boolean,
    colors: SkipiProxyServerCardColors,
) {
    Surface(
        color = Color(0xFF40505A).copy(alpha = if (selected) 0.88f else 0.56f),
        shape = SkipiTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            color = colors.text,
            style = SkipiTheme.typography.labelMedium,
            fontWeight = themedFontWeight(FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
