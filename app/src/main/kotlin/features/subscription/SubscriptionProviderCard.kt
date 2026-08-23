// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import app.R
import app.SubscriptionGroupState
import features.proxy.server.display.displayName
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.text.formatTemplate

import ui.icons.AnimatedHourglassIcon
import ui.icons.StaticHourglass

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
    val uriHandler = LocalUriHandler.current
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val title = group.profileTitle.ifBlank { group.displayName(defaultGroupName) }
    val traffic = group.subscriptionTrafficSummary()
    val expiry = group.subscriptionExpirySummary()
    val lastUpdated = group.lastUpdatedAtMillis.takeIf { value -> value > 0L }?.let { millis ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { onExpandedChange(!expanded) },
                    onLongClick = onEdit,
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    imageVector = if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.subscription_provider_servers, serverCount),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            IconButton(
                onClick = onUpdate,
                enabled = !isUpdating,
            ) {
                if (isUpdating) {
                    val updatingDescription = stringResource(R.string.subscription_provider_update)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = updatingDescription },
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator(
                            color = MiuixTheme.colorScheme.primary,
                            size = 20.dp,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.subscription_provider_update),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(
                onClick = onPing,
            ) {
                if (isPinging) {
                    val pingingDescription = stringResource(R.string.proxy_server_list_ping_in_progress)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = pingingDescription },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedHourglassIcon(
                            color = MiuixTheme.colorScheme.primary,
                            isPinging = true,
                            size = 20.dp,
                        )
                    }
                } else {
                    StaticHourglass(
                        modifier = Modifier.size(24.dp),
                        color = MiuixTheme.colorScheme.onSurface,
                        size = 18.dp,
                    )
                }
            }
        }
        if (traffic != null || expiry != null) {
            Spacer(Modifier.height(10.dp))
            traffic?.let { value ->
                Text(
                    text = stringResource(R.string.subscription_provider_traffic)
                        .formatTemplate("value" to value),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            expiry?.let { value ->
                if (traffic != null) Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            group.subscriptionTrafficProgress()?.let { progress ->
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(AppTheme.colors.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .background(AppTheme.colors.onSurface.copy(alpha = 0.70f)),
                    )
                }
            }
        }
        if (expanded && group.announce.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            val announceModifier = if (group.announceUrl.isNotBlank()) {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { runCatching { uriHandler.openUri(group.announceUrl) } }
                    .padding(vertical = 2.dp)
            } else {
                Modifier
            }
            Text(
                text = group.announce,
                style = MiuixTheme.textStyles.body2,
                color = if (group.announceUrl.isNotBlank()) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = announceModifier,
            )
        }
        if (expanded && (group.supportUrl.isNotBlank() || group.supportEmail.isNotBlank() || group.profileWebPageUrl.isNotBlank())) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val effectiveSupportUrl = when {
                    group.supportUrl.isNotBlank() -> group.supportUrl
                    group.supportEmail.isNotBlank() -> "mailto:${group.supportEmail}"
                    else -> null
                }
                if (effectiveSupportUrl != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { runCatching { uriHandler.openUri(effectiveSupportUrl) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.subscription_provider_support),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
                if (group.profileWebPageUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .clickable { runCatching { uriHandler.openUri(group.profileWebPageUrl) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.subscription_provider_website),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        lastUpdated?.let { value ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.subscription_provider_updated).formatTemplate("value" to value),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SubscriptionProviderBody(
    color: Color,
    borderColor: Color,
    isLastItem: Boolean,
    bottomCornerRadius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = if (isLastItem) {
        RoundedCornerShape(
            bottomStart = bottomCornerRadius,
            bottomEnd = bottomCornerRadius,
        )
    } else {
        RectangleShape
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .drawBehind {
                val stroke = 1.dp.toPx()
                val halfStroke = stroke / 2f
                if (isLastItem && bottomCornerRadius > 0.dp) {
                    val radius = bottomCornerRadius.toPx()
                    val diameter = radius * 2
                    // Left border line
                    drawLine(
                        color = borderColor,
                        start = Offset(halfStroke, 0f),
                        end = Offset(halfStroke, size.height - radius),
                        strokeWidth = stroke,
                    )
                    // Right border line
                    drawLine(
                        color = borderColor,
                        start = Offset(size.width - halfStroke, 0f),
                        end = Offset(size.width - halfStroke, size.height - radius),
                        strokeWidth = stroke,
                    )
                    // Bottom border line
                    drawLine(
                        color = borderColor,
                        start = Offset(radius, size.height - halfStroke),
                        end = Offset(size.width - radius, size.height - halfStroke),
                        strokeWidth = stroke,
                    )
                    // Bottom-left arc
                    drawArc(
                        color = borderColor,
                        startAngle = 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(halfStroke, size.height - diameter + halfStroke),
                        size = Size(diameter - stroke, diameter - stroke),
                        style = Stroke(width = stroke),
                    )
                    // Bottom-right arc
                    drawArc(
                        color = borderColor,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(size.width - diameter + halfStroke, size.height - diameter + halfStroke),
                        size = Size(diameter - stroke, diameter - stroke),
                        style = Stroke(width = stroke),
                    )
                } else {
                    // Intermediate item: ONLY left and right borders!
                    drawLine(
                        color = borderColor,
                        start = Offset(halfStroke, 0f),
                        end = Offset(halfStroke, size.height),
                        strokeWidth = stroke,
                    )
                    drawLine(
                        color = borderColor,
                        start = Offset(size.width - halfStroke, 0f),
                        end = Offset(size.width - halfStroke, size.height),
                        strokeWidth = stroke,
                    )
                }
            },
    ) {
        content()
    }
}

internal fun SubscriptionGroupState.subscriptionTrafficSummary(): String? {
    if (trafficUploadBytes < 0L && trafficDownloadBytes < 0L && trafficTotalBytes < 0L) return null
    val used = subscriptionUsedTrafficBytes()
    return if (!trafficTotalBytes.isUnlimitedSubscriptionTraffic()) {
        "${used.formatSubscriptionBytes()} / ${trafficTotalBytes.formatSubscriptionBytes()}"
    } else {
        "${used.formatSubscriptionBytes()} / \u221E"
    }
}

internal fun SubscriptionGroupState.subscriptionTrafficProgress(): Float? {
    if (trafficTotalBytes.isUnlimitedSubscriptionTraffic()) return null
    val used = subscriptionUsedTrafficBytes()
    return (used.toDouble() / trafficTotalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
}

@Composable
internal fun SubscriptionGroupState.subscriptionExpirySummary(): String? {
    if (trafficExpireAtSeconds <= 0L) return null
    val expiresAtMillis = trafficExpireAtSeconds * 1_000L
    val days = ((expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L) / MillisPerDay)
    return stringResource(R.string.subscription_provider_days_remaining, days)
}

private fun SubscriptionGroupState.subscriptionUsedTrafficBytes(): Long {
    val upload = trafficUploadBytes.coerceAtLeast(0L)
    val download = trafficDownloadBytes.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
}

private fun Long.isUnlimitedSubscriptionTraffic(): Boolean {
    return this <= 0L || this >= UnlimitedSubscriptionTrafficThreshold
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
