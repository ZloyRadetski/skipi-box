// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.subscription

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
import app.skipi.ui.resources.*
import app.skipi.ui.text.formatTemplate
import app.skipi.ui.text.themedFontWeight
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme


@Composable
fun SubscriptionProviderHeader(
    presentation: SubscriptionProviderPresentation,
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
    val title = presentation.title
    val traffic = presentation.trafficSummary
    val expiry = presentation.expirySummary
    val lastUpdated = presentation.lastUpdatedLabel

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
                    fontWeight = themedFontWeight(FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.subscription_provider_servers, serverCount),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            IconButton(
                onClick = onUpdate,
                enabled = !isUpdating,
            ) {
                if (isUpdating) {
                    val updatingDescription = stringResource(Res.string.subscription_provider_update)
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
                        contentDescription = stringResource(Res.string.subscription_provider_update),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(
                onClick = onPing,
            ) {
                if (isPinging) {
                    val pingingDescription = stringResource(Res.string.proxy_server_list_ping_in_progress)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = pingingDescription },
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
                        imageVector = MiuixIcons.Timer,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = MiuixIcons.More,
                    contentDescription = stringResource(Res.string.subscription_edit),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
        if (traffic != null || expiry != null) {
            Spacer(Modifier.height(10.dp))
            traffic?.let { value ->
                Text(
                    text = stringResource(Res.string.subscription_provider_traffic)
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
            presentation.trafficProgress?.let { progress ->
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.70f)),
                    )
                }
            }
        }
        if (expanded && presentation.announcement.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            val announceModifier = if (presentation.announcementUrl.isNotBlank()) {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { runCatching { uriHandler.openUri(presentation.announcementUrl) } }
                    .padding(vertical = 2.dp)
            } else {
                Modifier
            }
            Text(
                text = presentation.announcement,
                style = MiuixTheme.textStyles.body2,
                color = if (presentation.announcementUrl.isNotBlank()) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = announceModifier,
            )
        }
        if (expanded && (presentation.supportUrl.isNotBlank() || presentation.websiteUrl.isNotBlank())) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val effectiveSupportUrl = when {
                    presentation.supportUrl.isNotBlank() -> presentation.supportUrl
                    else -> null
                }
                if (effectiveSupportUrl != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .clickable { runCatching { uriHandler.openUri(effectiveSupportUrl) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.subscription_provider_support),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = themedFontWeight(FontWeight.Medium),
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (presentation.websiteUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .clickable { runCatching { uriHandler.openUri(presentation.websiteUrl) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.subscription_provider_website),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = themedFontWeight(FontWeight.Medium),
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        lastUpdated?.let { value ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.subscription_provider_updated).formatTemplate("value" to value),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SubscriptionProviderBody(
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

