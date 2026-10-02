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
import java.util.Locale

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
    if (trafficUploadBytes < 0L && trafficDownloadBytes < 0L && trafficTotalBytes < 0L) return null
    val used = subscriptionUsedTrafficBytes()
    return if (trafficTotalBytes > 0L && trafficTotalBytes < UnlimitedSubscriptionTrafficThreshold) {
        "${used.formatSubscriptionBytes()} / ${trafficTotalBytes.formatSubscriptionBytes()}"
    } else {
        "${used.formatSubscriptionBytes()} / ∞"
    }
}

internal fun SubscriptionGroupState.subscriptionTrafficProgress(): Float? {
    if (trafficTotalBytes <= 0L || trafficTotalBytes >= UnlimitedSubscriptionTrafficThreshold) return null
    return (subscriptionUsedTrafficBytes().toDouble() / trafficTotalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
}

@Composable
internal fun SubscriptionGroupState.subscriptionExpirySummary(): String? {
    if (trafficExpireAtSeconds <= 0L) return null
    val days = ((trafficExpireAtSeconds * 1_000L - System.currentTimeMillis()).coerceAtLeast(0L) / MillisPerDay)
    return stringResource(R.string.subscription_provider_days_remaining, days)
}

private fun SubscriptionGroupState.subscriptionUsedTrafficBytes(): Long {
    val upload = trafficUploadBytes.coerceAtLeast(0L)
    val download = trafficDownloadBytes.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
}

private fun Long.formatSubscriptionBytes(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) "$value ${units[unitIndex]}" else {
        String.format(Locale.getDefault(), "%.2f %s", value, units[unitIndex])
    }
}

private const val MillisPerDay = 24L * 60L * 60L * 1_000L
private const val UnlimitedSubscriptionTrafficThreshold = Long.MAX_VALUE / 2L
