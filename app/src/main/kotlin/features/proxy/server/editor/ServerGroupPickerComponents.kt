// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import app.R
import app.SubscriptionGroupState
import app.skipi.ui.server.editor.ServerPickerSubscriptionSummary
import app.skipi.ui.server.editor.SkipiServerPickerEmptyGroupRow
import app.skipi.ui.server.editor.SkipiServerPickerGroupHeader
import app.skipi.ui.server.editor.SkipiServerPickerItemRow
import features.subscription.subscriptionExpirySummary
import features.subscription.subscriptionTrafficProgress
import features.subscription.subscriptionTrafficSummary
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.res.stringResource
import ui.text.formatTemplate
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Android adapter for subscription metadata and legacy resource strings. */
@Composable
fun ServerPickerGroupHeader(
    title: String,
    count: Int,
    subscriptionGroup: SubscriptionGroupState? = null,
    expanded: Boolean,
    toggleState: ToggleableState? = null,
    onToggleGroup: (() -> Unit)? = null,
    onExpandedChange: (Boolean) -> Unit,
) {
    val traffic = subscriptionGroup?.subscriptionTrafficSummary()
    val expiry = subscriptionGroup?.subscriptionExpirySummary()
    val lastUpdated = subscriptionGroup?.lastUpdatedAtMillis?.takeIf { it > 0L }?.let { millis ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }
    val summary = if (subscriptionGroup == null) null else ServerPickerSubscriptionSummary(
        traffic = traffic?.let { stringResource(R.string.subscription_provider_traffic).formatTemplate("value" to it) },
        expiry = expiry,
        trafficProgress = subscriptionGroup.subscriptionTrafficProgress(),
        announce = subscriptionGroup.announce,
        lastUpdated = lastUpdated?.let { stringResource(R.string.subscription_provider_updated).formatTemplate("value" to it) },
    )
    SkipiServerPickerGroupHeader(title, count, summary, expanded, toggleState, onToggleGroup, onExpandedChange)
}

@Composable
fun ServerPickerEmptyGroupRow(message: String = stringResource(R.string.proxy_editor_strategy_group_no_servers)) {
    SkipiServerPickerEmptyGroupRow(message)
}

@Composable
fun ServerPickerItemRow(
    flag: String? = null,
    displayTitle: String,
    subtitle: String? = null,
    selected: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    SkipiServerPickerItemRow(
        flag = flag,
        displayTitle = displayTitle,
        subtitle = subtitle,
        selected = selected,
        isLast = isLast,
        fallbackPainter = painterResource(R.drawable.ic_globe),
        flagContainerColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        flagFallbackTint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.85f),
        onClick = onClick,
    )
}
