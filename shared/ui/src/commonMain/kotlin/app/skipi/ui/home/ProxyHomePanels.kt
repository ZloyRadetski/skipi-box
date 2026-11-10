// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import app.skipi.app.home.ProxyConnectionPhase
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeGroupHeader
import app.skipi.app.home.ProxyHomeGroupKind
import app.skipi.app.home.ProxyGroupSummary
import app.skipi.app.home.ProxyHomeUiState
import app.skipi.app.home.ProxySubscriptionSummary
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.home_connection_choose_server
import app.skipi.ui.resources.home_connection_connect
import app.skipi.ui.resources.home_connection_connected
import app.skipi.ui.resources.home_connection_connecting
import app.skipi.ui.resources.home_connection_disconnect
import app.skipi.ui.resources.home_connection_disconnected
import app.skipi.ui.resources.home_connection_preparing
import app.skipi.ui.resources.home_connection_tap_to_connect
import app.skipi.ui.resources.home_group_disabled
import app.skipi.ui.resources.home_latency_milliseconds
import app.skipi.ui.resources.home_move_down
import app.skipi.ui.resources.home_move_up
import app.skipi.ui.resources.proxy_server_list_latency_failed
import app.skipi.ui.resources.home_server_count
import app.skipi.ui.resources.home_subscription_days_left
import app.skipi.ui.resources.home_subscription_support
import app.skipi.ui.resources.home_subscription_traffic
import app.skipi.ui.resources.home_subscription_unnamed
import app.skipi.ui.resources.home_subscription_updated
import app.skipi.ui.resources.home_subscription_website
import app.skipi.ui.resources.proxy_server_list_latency_test
import app.skipi.ui.resources.home_delete_group
import app.skipi.ui.resources.home_edit_group
import app.skipi.ui.resources.subscription_delete_group
import app.skipi.ui.resources.subscription_edit_group
import app.skipi.ui.server.display.CountryFlagUtils
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import features.subscription.formatSubscriptionUpdateTimestamp
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HomeConnectionPanel(
    state: ProxyHomeUiState,
    compact: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showCardPowerControl: Boolean = true,
) {
    val rawTitle = state.selectedServerTitle.ifBlank { state.selectedServer?.title.orEmpty() }
    val flag = state.selectedServer?.flag
        ?: CountryFlagUtils.extractLeadingCountryFlag(rawTitle)
        ?: if (state.selectedServer?.isStrategyGroup == true) "⚡" else null
    val cleanTitle = if (flag != null) CountryFlagUtils.stripLeadingCountryFlag(rawTitle).trim() else rawTitle.trim()
    val hasServer = state.selectedServer != null || rawTitle.isNotBlank()
    val chooseServerText = stringResource(Res.string.home_connection_choose_server)

    val isBusy = state.tunnelBusy || state.isConnecting || ProxyHomeActionId.ToggleTunnel in state.busyActions
    val isConnectingPhase = isBusy || state.connectionPhase == ProxyConnectionPhase.Connecting

    val heroPhase = when {
        isConnectingPhase -> SkipiConnectionHeroPhase.Connecting
        state.connectionPhase == ProxyConnectionPhase.Connected -> SkipiConnectionHeroPhase.Connected
        else -> SkipiConnectionHeroPhase.Disconnected
    }

    val title = when {
        isConnectingPhase -> stringResource(Res.string.home_connection_connecting)
        state.connectionPhase == ProxyConnectionPhase.Connected -> stringResource(Res.string.home_connection_connected)
        else -> stringResource(Res.string.home_connection_disconnected)
    }

    val subtitle = when {
        isConnectingPhase -> {
            cleanTitle.ifBlank { stringResource(Res.string.home_connection_preparing) }
        }
        !hasServer -> {
            chooseServerText
        }
        else -> {
            when (state.connectionPhase) {
                ProxyConnectionPhase.Connected -> cleanTitle.ifBlank { stringResource(Res.string.home_connection_connected) }
                ProxyConnectionPhase.Connecting -> cleanTitle.ifBlank { stringResource(Res.string.home_connection_preparing) }
                ProxyConnectionPhase.Disconnected -> cleanTitle.ifBlank { stringResource(Res.string.home_connection_tap_to_connect) }
            }
        }
    }

    val sessionDuration = rememberSessionDuration(state.connectionPhase == ProxyConnectionPhase.Connected, state.connectedSince)

    val currentServer = state.selectedServer
    val currentServerLatencyMs = currentServer?.latencyMs
    val latency = when (
        heroLatencyDisplay(
            latencyMs = currentServerLatencyMs,
            testing = currentServer?.latencyTesting == true,
            failed = currentServer?.latencyError == true,
        )
    ) {
        HeroLatencyDisplay.Value -> SkipiProxyHeroLatency(
            text = stringResource(Res.string.home_latency_milliseconds, checkNotNull(currentServerLatencyMs)),
            color = serverLatencyColor(checkNotNull(currentServer), state.displayOptions),
            testing = false,
        )
        HeroLatencyDisplay.Failure -> SkipiProxyHeroLatency(
            text = checkNotNull(currentServer).latencyErrorText?.takeIf(String::isNotBlank)
                ?: stringResource(Res.string.proxy_server_list_latency_failed),
            color = serverLatencyColor(checkNotNull(currentServer), state.displayOptions),
            testing = false,
        )
        HeroLatencyDisplay.Hidden -> null
    }

    val memoryText = if (state.displayOptions.showTunnelMemory) {
        state.runtimeOutboundMetric?.takeIf(String::isNotBlank)
    } else null

    val toggleEnabled = canToggleHomePower(
        canToggleTunnel = state.canToggleTunnel,
        tunnelBusy = state.tunnelBusy,
        isConnecting = state.isConnecting,
        availableActions = state.availableActions,
        busyActions = state.busyActions,
    )

    val heroState = SkipiProxyHeroState(
        phase = heroPhase,
        title = title,
        subtitle = subtitle,
        flag = flag,
        sessionDuration = sessionDuration,
        latency = latency,
        memoryText = memoryText,
        ip = state.tunnelIp?.let { SkipiHeroIp(value = it.value, flag = it.flag) },
        showIp = state.displayOptions.showTunnelIp,
        maskIp = state.displayOptions.maskTunnelIp,
        toggleEnabled = toggleEnabled,
    )

    val colors = SkipiProxyHeroColors(
        surface = SkipiTheme.colors.surface,
        raisedSurface = SkipiTheme.colors.surfaceVariant,
        border = SkipiTheme.colors.surfaceVariant,
        accent = SkipiTheme.colors.accent,
        text = SkipiTheme.colors.onSurface,
        mutedText = SkipiTheme.colors.onSurfaceVariant,
        connectedStatus = SkipiTheme.colorScheme.success,
        onAccent = SkipiTheme.colors.onAccent,
    )

    val typography = SkipiProxyHeroTypography(
        title = themedFontWeight(FontWeight.Bold),
        emphasis = themedFontWeight(FontWeight.SemiBold),
        body = themedFontWeight(FontWeight.Medium),
    )

    if (state.displayOptions.connectionMode == ProxyHomeConnectionMode.Compact) {
        SkipiProxyHeroCompactCard(
            state = heroState,
            colors = colors,
            typography = typography,
            fallbackBadgePainter = null,
            modifier = modifier,
            onToggle = onToggle.takeIf { showCardPowerControl },
            connectContentDescription = stringResource(Res.string.home_connection_connect),
            disconnectContentDescription = stringResource(Res.string.home_connection_disconnect),
        )
    } else if (state.displayOptions.connectionMode == ProxyHomeConnectionMode.Legacy) {
        SkipiProxyHeroLegacyCard(
            state = heroState,
            colors = colors,
            typography = typography,
            compact = compact,
            onToggle = onToggle,
            modifier = modifier,
            connectContentDescription = stringResource(Res.string.home_connection_connect),
            disconnectContentDescription = stringResource(Res.string.home_connection_disconnect),
        )
    } else {
        SkipiProxyHeroClassicCard(
            state = heroState,
            colors = colors,
            typography = typography,
            compact = compact,
            onToggle = onToggle,
            modifier = modifier,
            buttonSize = state.displayOptions.classicButtonSize,
            connectContentDescription = stringResource(Res.string.home_connection_connect),
            disconnectContentDescription = stringResource(Res.string.home_connection_disconnect),
        )
    }
}

@Composable
internal fun HomeGroupSelector(
    state: ProxyHomeUiState,
    onAction: (ProxyHomeAction) -> Unit,
    onSelectGroup: (String) -> Unit = { groupId -> onAction(ProxyHomeAction.SelectGroup(groupId)) },
    modifier: Modifier = Modifier,
    /** Group to show as selected while a swipe is under way; null means the selected group. */
    displayedGroupId: String? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val groupItems = state.groups.map { group ->
        val serverCountText = stringResource(Res.string.home_server_count, group.serverCount)
        val groupTitle = if (group.enabled) {
            group.title
        } else {
            stringResource(Res.string.home_group_disabled, group.title)
        }
        SkipiProxyGroupPickerItem(
            id = group.id,
            title = groupTitle,
            subtitle = serverCountText,
            pickerText = "$groupTitle · $serverCountText",
        )
    }
    val groupActions = state.groups.mapIndexed { groupIndex, group ->
        group.id to buildList {
            if (canTestHomeGroup(
                    groupEnabled = group.enabled,
                    serverCount = group.serverCount,
                    availableActions = state.availableActions,
                    busyActions = state.busyActions,
                    isTestingLatency = state.isTestingLatency,
                )
            ) {
                add(SkipiProxyGroupPickerAction("test", stringResource(Res.string.proxy_server_list_latency_test)))
            }
            if (group.canEdit && canUseHomeAction(state, ProxyHomeActionId.EditGroup)) {
                val editLabel = if (group.kind == ProxyHomeGroupKind.Subscription) {
                    stringResource(Res.string.subscription_edit_group)
                } else {
                    stringResource(Res.string.home_edit_group)
                }
                add(SkipiProxyGroupPickerAction("edit", editLabel))
            }
            if (group.canDelete && canUseHomeAction(state, ProxyHomeActionId.DeleteGroup)) {
                val deleteLabel = if (group.kind == ProxyHomeGroupKind.Subscription) {
                    stringResource(Res.string.subscription_delete_group)
                } else {
                    stringResource(Res.string.home_delete_group)
                }
                add(SkipiProxyGroupPickerAction("delete", deleteLabel))
            }
            if (group.canMove && canUseHomeAction(state, ProxyHomeActionId.MoveGroup)) {
                // Proxy groups and subscriptions are listed apart, so a group only trades places with its own kind.
                val movesWith = { other: ProxyGroupSummary? -> other?.canMove == true && other.kind == group.kind }
                if (movesWith(state.groups.getOrNull(groupIndex - 1))) {
                    add(SkipiProxyGroupPickerAction("move-up", stringResource(Res.string.home_move_up)))
                }
                if (movesWith(state.groups.getOrNull(groupIndex + 1))) {
                    add(SkipiProxyGroupPickerAction("move-down", stringResource(Res.string.home_move_down)))
                }
            }
        }
    }.toMap()
    SkipiProxyGroupPicker(
        groups = groupItems,
        selectedGroupId = displayedGroupId ?: state.selectedGroupId,
        colors = SkipiProxyGroupPickerColors(
            surface = SkipiTheme.colors.surfaceVariant,
            raisedSurface = SkipiTheme.colors.surfaceVariant,
            border = Color.Transparent,
            accent = SkipiTheme.colors.accent,
            text = SkipiTheme.colors.onSurface,
            mutedText = SkipiTheme.colors.onSurfaceVariant,
        ),
        onGroupSelected = { groupId ->
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            onSelectGroup(groupId)
        },
        onPickerToggled = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        },
        onSelectedGroupLongClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        contextActions = { groupId -> groupActions[groupId].orEmpty() },
        onContextAction = { groupId, actionId ->
            when (actionId) {
                "test" -> onAction(ProxyHomeAction.TestGroup(groupId))
                "edit" -> onAction(ProxyHomeAction.EditGroup(groupId))
                "delete" -> onAction(ProxyHomeAction.DeleteGroup(groupId))
                "move-up" -> onAction(ProxyHomeAction.MoveGroup(groupId, -1))
                "move-down" -> onAction(ProxyHomeAction.MoveGroup(groupId, 1))
            }
        },
        modifier = modifier,
    )
}

/** A subscription's header for the groups the user keeps by hand: title, count and the group's ping. */
@Composable
internal fun HomeProxyGroupCard(
    groupId: String,
    header: ProxyHomeGroupHeader,
    onAction: (ProxyHomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canPing = header.pingAction != null
    SkipiSubscriptionSummaryCard(
        state = SkipiSubscriptionSummaryState(
            id = groupId,
            title = header.title,
            serverCountText = stringResource(Res.string.home_server_count, header.serverCount),
            canPing = canPing,
            enabled = true,
            refreshing = false,
            pinging = header.pinging,
            statusText = "",
            trafficText = null,
            trafficProgress = null,
            expiryText = null,
            description = null,
            announcementText = null,
            supportLabel = null,
            siteLabel = null,
            updatedText = null,
            canRefresh = false,
            canPingAction = canPing,
            canEdit = false,
            expandable = false,
        ),
        actions = SkipiSubscriptionSummaryActions(
            onRefresh = {},
            onPing = { header.pingAction?.let(onAction) },
            onEdit = {},
        ),
        colors = SkipiSubscriptionSummaryColors(
            surface = SkipiTheme.colors.surface,
            raisedSurface = SkipiTheme.colors.surfaceVariant,
            border = SkipiTheme.colors.surfaceVariant,
            text = SkipiTheme.colors.onSurface,
            mutedText = SkipiTheme.colors.onSurfaceVariant,
            enabled = SkipiTheme.colorScheme.success,
        ),
        expanded = false,
        modifier = modifier,
        embeddedInPanel = true,
    )
}

@Composable
internal fun HomeSubscriptionCard(
    subscription: ProxySubscriptionSummary,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    context: ServerRowContext,
    onAction: (ProxyHomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalTraffic = subscription.totalBytes?.takeIf { it > 0 }
    val progress = totalTraffic?.takeIf { it > 0 }?.let { total ->
        (subscription.usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
    SkipiSubscriptionSummaryCard(
        state = SkipiSubscriptionSummaryState(
            id = subscription.id,
            title = subscription.title.ifBlank { stringResource(Res.string.home_subscription_unnamed) },
            serverCountText = stringResource(Res.string.home_server_count, subscription.serverCount),
            canPing = subscription.serverCount > 0,
            enabled = subscription.enabled,
            refreshing = subscription.refreshing,
            pinging = subscription.pinging,
            statusText = "",
            trafficText = stringResource(
                Res.string.home_subscription_traffic,
                formatSummaryBytes(subscription.usedBytes),
                totalTraffic?.let(::formatSummaryBytes) ?: "∞",
            ),
            trafficProgress = progress,
            expiryText = subscription.expireAtSeconds?.takeIf { it > 0 }?.let { expireAt ->
                val days = ((expireAt * 1_000L - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                stringResource(Res.string.home_subscription_days_left, days)
            },
            description = subscription.description,
            announcementText = subscription.announcement,
            supportLabel = if (subscription.supportUrl != null) stringResource(Res.string.home_subscription_support) else null,
            siteLabel = if (subscription.siteUrl != null) stringResource(Res.string.home_subscription_website) else null,
            updatedText = subscription.lastUpdatedAtMillis?.takeIf { it > 0 }?.let { timestamp ->
                stringResource(
                    Res.string.home_subscription_updated,
                    formatSubscriptionUpdateTimestamp(timestamp),
                )
            },
            canRefresh = context.canUse(ProxyHomeActionId.RefreshSubscription),
        canPingAction = context.canUse(ProxyHomeActionId.PingSubscription) &&
                (!subscription.pinging || subscription.canCancelPing),
            canEdit = context.canUse(ProxyHomeActionId.EditSubscription),
        ),
        actions = SkipiSubscriptionSummaryActions(
            onRefresh = { onAction(ProxyHomeAction.RefreshSubscription(subscription.id)) },
            onPing = { onAction(ProxyHomeAction.PingSubscription(subscription.id)) },
            onEdit = { onAction(ProxyHomeAction.EditSubscription(subscription.id)) },
            onAnnouncement = subscription.announcementUrl
                ?.takeIf { context.canUse(ProxyHomeActionId.OpenExternalLink) }
                ?.let { url -> { onAction(ProxyHomeAction.OpenExternalLink(url)) } },
            onSupport = subscription.supportUrl
                ?.takeIf { context.canUse(ProxyHomeActionId.OpenExternalLink) }
                ?.let { url -> { onAction(ProxyHomeAction.OpenExternalLink(url)) } },
            onSite = subscription.siteUrl
                ?.takeIf { context.canUse(ProxyHomeActionId.OpenExternalLink) }
                ?.let { url -> { onAction(ProxyHomeAction.OpenExternalLink(url)) } },
        ),
        colors = SkipiSubscriptionSummaryColors(
            surface = SkipiTheme.colors.surface,
            raisedSurface = SkipiTheme.colors.surfaceVariant,
            border = SkipiTheme.colors.surfaceVariant,
            text = SkipiTheme.colors.onSurface,
            mutedText = SkipiTheme.colors.onSurfaceVariant,
            enabled = SkipiTheme.colorScheme.success,
        ),
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
        modifier = modifier,
        embeddedInPanel = true,
    )
}
