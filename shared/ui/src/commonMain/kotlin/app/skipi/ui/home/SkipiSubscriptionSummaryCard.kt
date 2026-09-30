// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_edit
import app.skipi.ui.resources.common_refresh
import app.skipi.ui.resources.home_details_collapsed
import app.skipi.ui.resources.home_details_expanded
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
    val canRefresh: Boolean = true,
    val canPingAction: Boolean = true,
    val canEdit: Boolean = true,
    val canToggleEnabled: Boolean = false,
    val pinging: Boolean = false,
)

data class SkipiSubscriptionSummaryActions(
    val onRefresh: () -> Unit,
    val onPing: () -> Unit,
    val onEdit: () -> Unit,
    val onToggleEnabled: (() -> Unit)? = null,
    val onAnnouncement: (() -> Unit)? = null,
    val onSupport: (() -> Unit)? = null,
    val onSite: (() -> Unit)? = null,
)

data class SkipiSubscriptionSummaryLabels(
    val refreshContentDescription: String,
    val pingContentDescription: String,
    val editContentDescription: String,
    val detailsExpandedDescription: String,
    val detailsCollapsedDescription: String,
)

@Composable
fun defaultSkipiSubscriptionSummaryLabels(): SkipiSubscriptionSummaryLabels = SkipiSubscriptionSummaryLabels(
    refreshContentDescription = stringResource(Res.string.common_refresh),
    pingContentDescription = stringResource(Res.string.proxy_server_list_latency_test),
    editContentDescription = stringResource(Res.string.common_edit),
    detailsExpandedDescription = stringResource(Res.string.home_details_expanded),
    detailsCollapsedDescription = stringResource(Res.string.home_details_collapsed),
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
 * Shared Home header for a subscription/provider summary.
 *
 * Network refresh, persistence and external-link safety stay in platform
 * adapters. This component renders state and forwards user intents. Set
 * [embeddedInPanel] to true when a caller wraps this header and server rows in
 * one continuous panel; that mode omits this card's standalone surface and
 * border.
 */
@Composable
fun SkipiSubscriptionSummaryCard(
    state: SkipiSubscriptionSummaryState,
    actions: SkipiSubscriptionSummaryActions,
    colors: SkipiSubscriptionSummaryColors,
    modifier: Modifier = Modifier,
    labels: SkipiSubscriptionSummaryLabels = defaultSkipiSubscriptionSummaryLabels(),
    expanded: Boolean? = null,
    onToggleExpanded: (() -> Unit)? = null,
    embeddedInPanel: Boolean = false,
) {
    var internalExpanded by remember(state.id) { mutableStateOf(true) }
    val isExpanded = expanded ?: internalExpanded
    val toggleExpanded = onToggleExpanded ?: { internalExpanded = !internalExpanded }
    val contentModifier = modifier.fillMaxWidth()

    if (embeddedInPanel) {
        Column(modifier = contentModifier) {
            SubscriptionSummaryCardContent(
                state = state,
                actions = actions,
                colors = colors,
                labels = labels,
                isExpanded = isExpanded,
                onToggleExpanded = toggleExpanded,
            )
        }
    } else {
        Card(
            modifier = contentModifier,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.text.copy(alpha = 0.14f)),
        ) {
            SubscriptionSummaryCardContent(
                state = state,
                actions = actions,
                colors = colors,
                labels = labels,
                isExpanded = isExpanded,
                onToggleExpanded = toggleExpanded,
            )
        }
    }
}
