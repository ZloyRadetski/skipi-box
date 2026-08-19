// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import app.SubscriptionGroupState
import features.proxy.server.list.CountryFlagBadge
import features.subscription.SubscriptionProviderBody
import features.subscription.subscriptionExpirySummary
import features.subscription.subscriptionTrafficProgress
import features.subscription.subscriptionTrafficSummary
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.text.formatTemplate

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
    val sub = subscriptionGroup
    val traffic = sub?.subscriptionTrafficSummary()
    val expiry = sub?.subscriptionExpirySummary()
    val lastUpdated = sub?.lastUpdatedAtMillis?.takeIf { it > 0L }?.let { millis ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }

    val providerBorderColor = AppTheme.colors.onSurface.copy(alpha = 0.14f)
    val providerColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.65f)
    val providerCornerRadius = 18.dp
    val headerShape = if (expanded && count > 0) {
        RoundedCornerShape(topStart = providerCornerRadius, topEnd = providerCornerRadius)
    } else {
        RoundedCornerShape(providerCornerRadius)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp)
            .clip(headerShape)
            .background(providerColor)
            .border(width = 1.dp, color = providerBorderColor, shape = headerShape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = { onExpandedChange(!expanded) })
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
                        text = stringResource(R.string.subscription_provider_servers, count),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (toggleState != null && onToggleGroup != null) {
                    Checkbox(
                        state = toggleState,
                        onClick = onToggleGroup,
                        enabled = count > 0,
                    )
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
                sub.subscriptionTrafficProgress()?.let { progress ->
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
            if (expanded && sub != null && sub.announce.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = sub.announce,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
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
}

@Composable
fun ServerPickerEmptyGroupRow(
    message: String = stringResource(R.string.proxy_editor_strategy_group_no_servers),
) {
    val providerBorderColor = AppTheme.colors.onSurface.copy(alpha = 0.14f)
    val providerColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.65f)
    val providerCornerRadius = 18.dp

    SubscriptionProviderBody(
        color = providerColor,
        borderColor = providerBorderColor,
        isLastItem = true,
        bottomCornerRadius = providerCornerRadius,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
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
    val providerBorderColor = AppTheme.colors.onSurface.copy(alpha = 0.14f)
    val providerColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.65f)
    val dividerColor = AppTheme.colors.onSurface.copy(alpha = 0.08f)
    val providerCornerRadius = 18.dp

    SubscriptionProviderBody(
        color = providerColor,
        borderColor = providerBorderColor,
        isLastItem = isLast,
        bottomCornerRadius = providerCornerRadius,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CountryFlagBadge(
                    flag = flag,
                    size = 32.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) AppTheme.colors.accent else MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Checkbox(
                    state = ToggleableState(selected),
                    onClick = onClick,
                )
            }
            if (!isLast) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor,
                )
            }
        }
    }
}
