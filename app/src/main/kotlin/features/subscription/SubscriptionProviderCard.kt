// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.R
import app.SubscriptionGroupState
import app.skipi.ui.subscription.SubscriptionProviderBody as SharedSubscriptionProviderBody
import app.skipi.ui.subscription.SubscriptionProviderHeader as SharedSubscriptionProviderHeader
import app.skipi.ui.subscription.SubscriptionProviderPresentation
import features.proxy.server.display.displayName

@Composable
internal fun SubscriptionProviderHeader(
    group: SubscriptionGroupState,
    serverCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onPing: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    isUpdating: Boolean = false,
    isPinging: Boolean = false,
) {
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val updated = group.lastUpdatedAtMillis.takeIf { it > 0L }?.let { millis ->
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT,
        ).format(java.util.Date(millis))
    }
    val supportUrl = group.supportUrl.takeIf(String::isNotBlank)
        ?: group.supportEmail.takeIf(String::isNotBlank)?.let { "mailto:$it" }.orEmpty()
    SharedSubscriptionProviderHeader(
        presentation = SubscriptionProviderPresentation(
            title = group.profileTitle.ifBlank { group.displayName(defaultGroupName) },
            serverCount = serverCount,
            trafficSummary = group.subscriptionTrafficSummary(),
            trafficProgress = group.subscriptionTrafficProgress(),
            expirySummary = group.subscriptionExpirySummary(),
            lastUpdatedLabel = updated,
            announcement = group.announce,
            announcementUrl = group.announceUrl,
            supportUrl = supportUrl,
            websiteUrl = group.profileWebPageUrl,
        ),
        serverCount = serverCount,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onUpdate = onUpdate,
        onPing = onPing,
        onEdit = onEdit,
        modifier = modifier,
        isUpdating = isUpdating,
        isPinging = isPinging,
    )
}

@Composable
internal fun SubscriptionProviderBody(
    color: Color,
    borderColor: Color,
    isLastItem: Boolean,
    bottomCornerRadius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = SharedSubscriptionProviderBody(
    color = color,
    borderColor = borderColor,
    isLastItem = isLastItem,
    bottomCornerRadius = bottomCornerRadius,
    modifier = modifier,
    content = content,
)

internal fun SubscriptionGroupState.subscriptionTrafficSummary(): String? {
    return features.subscription.subscriptionTrafficSummary(
        trafficUploadBytes,
        trafficDownloadBytes,
        trafficTotalBytes,
    )
}

internal fun SubscriptionGroupState.subscriptionTrafficProgress(): Float? {
    return features.subscription.subscriptionTrafficProgress(
        trafficUploadBytes,
        trafficDownloadBytes,
        trafficTotalBytes,
    )
}

@Composable
internal fun SubscriptionGroupState.subscriptionExpirySummary(): String? {
    val days = subscriptionExpiryDays(trafficExpireAtSeconds, System.currentTimeMillis()) ?: return null
    return stringResource(R.string.subscription_provider_days_remaining, days)
}
