// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_edit
import app.skipi.ui.resources.common_refresh
import app.skipi.ui.resources.proxy_server_list_latency_test
import org.jetbrains.compose.resources.stringResource

data class SkipiSubscriptionSummaryState(
    val id: String,
    val title: String,
    val serverCountText: String,
    val canPing: Boolean,
    val enabled: Boolean,
    val refreshing: Boolean,
    val statusText: String,
    val trafficText: String?,
    val trafficProgress: Float?,
    val expiryText: String?,
    val description: String?,
    val announcementText: String?,
    val supportLabel: String?,
    val siteLabel: String?,
    val updatedText: String?,
)

data class SkipiSubscriptionSummaryActions(
    val onRefresh: () -> Unit,
    val onPing: () -> Unit,
    val onEdit: () -> Unit,
    val onToggleEnabled: () -> Unit,
    val onAnnouncement: (() -> Unit)? = null,
    val onSupport: (() -> Unit)? = null,
    val onSite: (() -> Unit)? = null,
)

data class SkipiSubscriptionSummaryLabels(
    val refreshContentDescription: String,
    val pingContentDescription: String,
    val editContentDescription: String,
)

@Composable
fun defaultSkipiSubscriptionSummaryLabels(): SkipiSubscriptionSummaryLabels = SkipiSubscriptionSummaryLabels(
    refreshContentDescription = stringResource(Res.string.common_refresh),
    pingContentDescription = stringResource(Res.string.proxy_server_list_latency_test),
    editContentDescription = stringResource(Res.string.common_edit),
)

data class SkipiSubscriptionSummaryColors(
    val surface: Color,
    val raisedSurface: Color,
    val border: Color,
    val text: Color,
    val mutedText: Color,
    val enabled: Color,
)

/**
 * Shared Home card for a subscription/provider summary.
 *
 * Network refresh, persistence and external-link safety stay in platform
 * adapters. This component renders the state and forwards only user intents.
 */
@Composable
fun SkipiSubscriptionSummaryCard(
    state: SkipiSubscriptionSummaryState,
    actions: SkipiSubscriptionSummaryActions,
    colors: SkipiSubscriptionSummaryColors,
    modifier: Modifier = Modifier,
    labels: SkipiSubscriptionSummaryLabels = defaultSkipiSubscriptionSummaryLabels(),
) {
    var expanded by remember(state.id) { mutableStateOf(true) }
    Card(
        modifier = modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.raisedSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⌜⌟", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        color = colors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(state.serverCountText, color = colors.mutedText, fontSize = 14.sp)
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { actions.onToggleEnabled() },
                    enabled = !state.refreshing,
                )
                IconButton(onClick = actions.onRefresh, enabled = !state.refreshing) {
                    Icon(Icons.Outlined.Refresh, labels.refreshContentDescription, tint = colors.text)
                }
                IconButton(
                    onClick = actions.onPing,
                    enabled = !state.refreshing && state.canPing,
                ) {
                    Icon(Icons.Outlined.HourglassEmpty, labels.pingContentDescription, tint = colors.text)
                }
                IconButton(onClick = actions.onEdit) {
                    Icon(Icons.Outlined.MoreVert, labels.editContentDescription, tint = colors.text)
                }
            }
            Text(
                text = state.statusText,
                color = if (state.enabled) colors.enabled else colors.mutedText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (expanded) {
                state.trafficText?.let { trafficText ->
                    Text(trafficText, color = colors.mutedText, fontSize = 14.sp)
                }
                state.trafficProgress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.enabled,
                        trackColor = colors.raisedSurface,
                    )
                }
                state.expiryText?.let { expiryText ->
                    Text(expiryText, color = colors.mutedText, fontSize = 14.sp)
                }
                state.description?.let { description ->
                    Text(description, color = colors.mutedText, fontSize = 14.sp)
                }
                state.announcementText?.let { announcementText ->
                    val onAnnouncement = actions.onAnnouncement
                    if (onAnnouncement == null) {
                        Text(announcementText, color = colors.mutedText, fontSize = 14.sp)
                    } else {
                        TextButton(onClick = onAnnouncement) { Text(announcementText) }
                    }
                }
                val supportAction = actions.onSupport
                val siteAction = actions.onSite
                if ((state.supportLabel != null && supportAction != null) || (state.siteLabel != null && siteAction != null)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.supportLabel != null && supportAction != null) {
                            TextButton(onClick = supportAction) { Text(state.supportLabel) }
                        }
                        if (state.siteLabel != null && siteAction != null) {
                            TextButton(onClick = siteAction) { Text(state.siteLabel) }
                        }
                    }
                }
                state.updatedText?.let { updatedText ->
                    Text(updatedText, color = colors.mutedText, fontSize = 13.sp)
                }
            }
        }
    }
}
