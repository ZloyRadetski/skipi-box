// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.proxy_server_list_ping_in_progress
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh

@Composable
internal fun SubscriptionSummaryCardContent(
    state: SkipiSubscriptionSummaryState,
    actions: SkipiSubscriptionSummaryActions,
    colors: SkipiSubscriptionSummaryColors,
    labels: SkipiSubscriptionSummaryLabels,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    role = Role.Button,
                    onClick = onToggleExpanded,
                    onLongClick = if (state.canEdit) actions.onEdit else null,
                )
                .semantics {
                    stateDescription = if (isExpanded) {
                        labels.detailsExpandedDescription
                    } else {
                        labels.detailsCollapsedDescription
                    }
                }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onToggleExpanded,
                modifier = Modifier.semantics {
                    contentDescription = if (isExpanded) {
                        labels.detailsExpandedDescription
                    } else {
                        labels.detailsCollapsedDescription
                    }
                },
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = colors.text,
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = state.title,
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = themedFontWeight(FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.serverCountText,
                    color = colors.mutedText,
                    style = SkipiTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SubscriptionHeaderActions(state = state, actions = actions, labels = labels, colors = colors)
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(260)) + expandVertically(animationSpec = tween(260)),
            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)),
        ) {
            SubscriptionSummaryDetails(state = state, actions = actions, colors = colors)
        }
    }
}

@Composable
private fun SubscriptionHeaderActions(
    state: SkipiSubscriptionSummaryState,
    actions: SkipiSubscriptionSummaryActions,
    labels: SkipiSubscriptionSummaryLabels,
    colors: SkipiSubscriptionSummaryColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.canRefresh || state.refreshing) {
            IconButton(
                onClick = actions.onRefresh,
                enabled = state.canRefresh && !state.refreshing,
                modifier = Modifier.semantics {
                    contentDescription = labels.refreshContentDescription
                },
            ) {
                if (state.refreshing) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = labels.refreshContentDescription },
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator(
                            color = colors.enabled,
                            size = 20.dp,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = null,
                        tint = colors.text,
                    )
                }
            }
        }
        if (state.canPingAction || state.pinging) {
            val pingDescription = if (state.pinging) {
                stringResource(Res.string.proxy_server_list_ping_in_progress)
            } else {
                labels.pingContentDescription
            }
            IconButton(
                onClick = actions.onPing,
                // Android reports an active run only where this same action cancels it. Let that
                // cancellation through even when starting a new ping is currently unavailable.
                enabled = state.canPing && (
                    state.pinging || (state.canPingAction && !state.refreshing)
                ),
                modifier = Modifier.semantics { contentDescription = pingDescription },
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.pinging) {
                        SkipiProxyHeroAnimatedHourglassIcon(color = colors.enabled, size = 20.dp)
                    } else {
                        SkipiProxyHeroStaticHourglassIcon(color = colors.text, size = 20.dp)
                    }
                }
            }
        }
        if (state.canEdit) {
            IconButton(
                onClick = actions.onEdit,
                modifier = Modifier.semantics {
                    contentDescription = labels.editContentDescription
                },
            ) {
                Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = colors.text)
            }
        }
    }
}
