// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyConnectionPhase
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyHomeUiState
import app.skipi.app.home.ProxyServerSummary
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiWindowClass
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.home_action_add_server
import app.skipi.ui.resources.home_action_add_subscription
import app.skipi.ui.resources.home_action_hide_search
import app.skipi.ui.resources.home_action_import
import app.skipi.ui.resources.home_action_refresh_subscription
import app.skipi.ui.resources.home_action_refreshing
import app.skipi.ui.resources.home_action_search
import app.skipi.ui.resources.home_connection_choose_server
import app.skipi.ui.resources.home_connection_connect
import app.skipi.ui.resources.home_connection_connected
import app.skipi.ui.resources.home_connection_connecting
import app.skipi.ui.resources.home_connection_disconnect
import app.skipi.ui.resources.home_connection_disconnected
import app.skipi.ui.resources.home_connection_preparing
import app.skipi.ui.resources.home_connection_profile
import app.skipi.ui.resources.home_connection_tap_to_connect
import app.skipi.ui.resources.home_empty_add_server
import app.skipi.ui.resources.home_empty_hint
import app.skipi.ui.resources.home_empty_search
import app.skipi.ui.resources.home_empty_servers
import app.skipi.ui.resources.home_error
import app.skipi.ui.resources.home_group_disabled
import app.skipi.ui.resources.home_group_empty
import app.skipi.ui.resources.home_group_title
import app.skipi.ui.resources.home_latency_milliseconds
import app.skipi.ui.resources.home_search_servers
import app.skipi.ui.resources.home_server_count
import app.skipi.ui.resources.home_status_error_description
import app.skipi.ui.resources.home_subscription_auto_update
import app.skipi.ui.resources.home_subscription_days_left
import app.skipi.ui.resources.home_subscription_disabled
import app.skipi.ui.resources.home_subscription_enabled
import app.skipi.ui.resources.home_subscription_support
import app.skipi.ui.resources.home_subscription_traffic
import app.skipi.ui.resources.home_subscription_unnamed
import app.skipi.ui.resources.home_subscription_updated
import app.skipi.ui.resources.home_subscription_website
import org.jetbrains.compose.resources.stringResource

data class ProxyHomeCapabilities(
    val onToggleTunnel: () -> Unit = {},
    val onSelectServer: (String) -> Unit = {},
    val onTestServerLatency: (String) -> Unit = {},
    val onTestGroupLatency: (String) -> Unit = {},
    val onTestAllLatency: () -> Unit = {},
    val onAddServer: () -> Unit = {},
    val onAddSubscription: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onEditServer: (String) -> Unit = {},
    val onDeleteServer: (String) -> Unit = {},
    val onCopyServerLink: (String) -> Unit = {},
    val onSelectGroup: (String) -> Unit = {},
    val onRefreshSubscription: (String) -> Unit = {},
    val onEditSubscription: (String) -> Unit = {},
    val onToggleSubscriptionEnabled: (String) -> Unit = {},
    val onOpenAnnouncement: ((String) -> Unit)? = null,
    val onOpenSupport: ((String) -> Unit)? = null,
    val onOpenSite: ((String) -> Unit)? = null,
    val onSearchQueryChange: (String) -> Unit = {},
    val onToggleSearchVisible: () -> Unit = {},
)

/**
 * Unified adaptive Home screen for both Android and Desktop.
 * Adapts between compact phone layout and wider tablet/desktop layouts.
 */
@Composable
fun ProxyHomeScreen(
    store: ProxyHomeStore,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    capabilities: ProxyHomeCapabilities = ProxyHomeCapabilities(),
) {
    val state by store.uiState.collectAsState()

    ProxyHomeScreenContent(
        state = state,
        onAction = { action -> store.dispatch(action) },
        capabilities = capabilities,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
fun ProxyHomeScreenContent(
    state: ProxyHomeUiState,
    onAction: (ProxyHomeAction) -> Unit,
    capabilities: ProxyHomeCapabilities,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val isCompact = SkipiTheme.windowClass == SkipiWindowClass.Compact
    val maxWidth = if (isCompact) Modifier.fillMaxWidth() else Modifier.widthIn(max = 980.dp)
    val refreshSubscriptionTitle = stringResource(Res.string.home_action_refresh_subscription)
    val refreshingSubscriptionTitle = stringResource(Res.string.home_action_refreshing)
    val searchActionTitle = stringResource(Res.string.home_action_search)
    val hideSearchActionTitle = stringResource(Res.string.home_action_hide_search)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SkipiTheme.colors.background)
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(maxWidth)
                .padding(
                    horizontal = if (isCompact) SkipiTheme.spacing.medium else SkipiTheme.spacing.extraLarge,
                    vertical = if (isCompact) SkipiTheme.spacing.small else SkipiTheme.spacing.medium,
                ),
        ) {
            SkipiProxyHomeHeader(
                state = SkipiProxyHomeHeaderState(
                    title = "SKIPI",
                    latencyTesting = state.isTestingLatency,
                    latencyEnabled = !state.isTestingLatency,
                ),
                colors = SkipiProxyHomeHeaderColors(
                    text = SkipiTheme.colors.onBackground,
                    mutedText = SkipiTheme.colors.onSurfaceVariant,
                    accent = SkipiTheme.colors.accent,
                ),
                addActions = listOf(
                    SkipiProxyHomeHeaderAction(id = "add_server", title = stringResource(Res.string.home_action_add_server)),
                    SkipiProxyHomeHeaderAction(id = "add_subscription", title = stringResource(Res.string.home_action_add_subscription)),
                    SkipiProxyHomeHeaderAction(id = "import", title = stringResource(Res.string.home_action_import)),
                ),
                toolActions = buildList {
                    val currentSub = state.subscription
                    if (currentSub != null) {
                        add(
                            SkipiProxyHomeHeaderAction(
                                id = "refresh_subscription",
                                title = if (currentSub.refreshing) refreshingSubscriptionTitle else refreshSubscriptionTitle,
                                enabled = !currentSub.refreshing,
                            ),
                        )
                    }
                    add(
                        SkipiProxyHomeHeaderAction(
                            id = "search",
                            title = if (state.isSearchVisible) hideSearchActionTitle else searchActionTitle,
                        ),
                    )
                },
                onTestLatency = {
                    onAction(ProxyHomeAction.TestAllVisibleServers)
                    capabilities.onTestAllLatency()
                },
                onAddAction = { id ->
                    when (id) {
                        "add_server" -> capabilities.onAddServer()
                        "add_subscription" -> capabilities.onAddSubscription()
                        "import" -> capabilities.onImport()
                    }
                },
                onToolAction = { id ->
                    when (id) {
                        "refresh_subscription" -> state.subscription?.let {
                            onAction(ProxyHomeAction.RefreshSubscription(it.id))
                            capabilities.onRefreshSubscription(it.id)
                        }
                        "search" -> {
                            val nextVisible = !state.isSearchVisible
                            onAction(ProxyHomeAction.SetSearchVisible(nextVisible))
                            capabilities.onToggleSearchVisible()
                        }
                    }
                },
            )

            Spacer(Modifier.height(SkipiTheme.spacing.small))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.medium),
            ) {
                SkipiConnectionHeroCard(
                    state = SkipiConnectionHeroState(
                        phase = when (state.connectionPhase) {
                            ProxyConnectionPhase.Connected -> SkipiConnectionHeroPhase.Connected
                            ProxyConnectionPhase.Connecting -> SkipiConnectionHeroPhase.Connecting
                            ProxyConnectionPhase.Disconnected -> SkipiConnectionHeroPhase.Disconnected
                        },
                        title = when (state.connectionPhase) {
                            ProxyConnectionPhase.Connected -> stringResource(Res.string.home_connection_connected)
                            ProxyConnectionPhase.Connecting -> stringResource(Res.string.home_connection_connecting)
                            ProxyConnectionPhase.Disconnected -> stringResource(Res.string.home_connection_disconnected)
                        },
                        subtitle = when {
                            state.connectionPhase == ProxyConnectionPhase.Connected -> state.selectedServerTitle.ifBlank { stringResource(Res.string.home_connection_connected) }
                            state.connectionPhase == ProxyConnectionPhase.Connecting -> state.selectedServerTitle.ifBlank { stringResource(Res.string.home_connection_preparing) }
                            state.selectedServerTitle.isNotBlank() -> stringResource(Res.string.home_connection_tap_to_connect)
                            else -> stringResource(Res.string.home_connection_choose_server)
                        },
                        profileText = state.activeProfileName?.let { stringResource(Res.string.home_connection_profile, it) },
                        toggleEnabled = state.canToggleTunnel,
                    ),
                    colors = SkipiConnectionHeroColors(
                        surface = SkipiTheme.colors.surface,
                        raisedSurface = SkipiTheme.colors.surfaceVariant,
                        border = SkipiTheme.colors.surfaceVariant,
                        text = SkipiTheme.colors.onSurface,
                        mutedText = SkipiTheme.colors.onSurfaceVariant,
                        connected = SkipiTheme.colorScheme.success,
                        connecting = SkipiTheme.colorScheme.warning,
                        disconnected = SkipiTheme.colors.surfaceVariant,
                    ),
                    compact = isCompact,
                    connectContentDescription = stringResource(Res.string.home_connection_connect),
                    disconnectContentDescription = stringResource(Res.string.home_connection_disconnect),
                    onToggle = {
                        onAction(ProxyHomeAction.ToggleTunnel)
                        capabilities.onToggleTunnel()
                    },
                )

                if (state.groups.size > 1) {
                    SkipiProxyGroupSelector(
                        groups = state.groups.map { group ->
                            SkipiProxyGroupItem(
                                id = group.id,
                                title = group.title,
                                serverCount = group.serverCount,
                                enabled = group.enabled,
                            )
                        },
                        selectedGroupId = state.selectedGroupId,
                        title = stringResource(Res.string.home_group_title),
                        emptyText = stringResource(Res.string.home_group_empty),
                        disabledTitle = { groupTitle -> stringResource(Res.string.home_group_disabled, groupTitle) },
                        serverCountText = { count -> stringResource(Res.string.home_server_count, count) },
                        colors = SkipiProxyGroupSelectorColors(
                            surface = SkipiTheme.colors.surface,
                            raisedSurface = SkipiTheme.colors.surfaceVariant,
                            selectedSurface = SkipiTheme.colorScheme.primaryContainer,
                            border = SkipiTheme.colors.surfaceVariant,
                            selectedBorder = SkipiTheme.colorScheme.outline,
                            text = SkipiTheme.colors.onSurface,
                            mutedText = SkipiTheme.colors.onSurfaceVariant,
                            selectedText = SkipiTheme.colorScheme.onPrimaryContainer,
                            selectedMutedText = SkipiTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        ),
                        onSelect = { groupId ->
                            onAction(ProxyHomeAction.SelectGroup(groupId))
                            capabilities.onSelectGroup(groupId)
                        },
                    )
                }

                if (state.isSearchVisible) {
                    SkipiProxyHomeSearchField(
                        value = state.searchQuery,
                        onValueChange = { query ->
                            onAction(ProxyHomeAction.SetSearchQuery(query))
                            capabilities.onSearchQueryChange(query)
                        },
                        label = stringResource(Res.string.home_search_servers),
                    )
                }

                state.subscription?.let { subscription ->
                    val usedBytes = subscription.usedBytes
                    val totalTraffic = subscription.totalBytes
                    val progress = totalTraffic?.takeIf { it > 0 }?.let { total ->
                        (usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }

                    SkipiSubscriptionSummaryCard(
                        state = SkipiSubscriptionSummaryState(
                            id = subscription.id,
                            title = subscription.title.ifBlank { stringResource(Res.string.home_subscription_unnamed) },
                            serverCountText = stringResource(Res.string.home_server_count, subscription.serverCount),
                            canPing = subscription.serverCount > 0,
                            enabled = subscription.enabled,
                            refreshing = subscription.refreshing,
                            statusText = buildString {
                                append(stringResource(if (subscription.enabled) Res.string.home_subscription_enabled else Res.string.home_subscription_disabled))
                                subscription.updateIntervalHours?.trim()?.takeIf(String::isNotBlank)?.let { interval ->
                                    append(stringResource(Res.string.home_subscription_auto_update, interval))
                                }
                            },
                            trafficText = stringResource(
                                Res.string.home_subscription_traffic,
                                formatSummaryBytes(usedBytes),
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
                            updatedText = subscription.lastUpdatedAtMillis?.takeIf { it > 0 }?.let { stringResource(Res.string.home_subscription_updated) },
                        ),
                        actions = SkipiSubscriptionSummaryActions(
                            onRefresh = {
                                onAction(ProxyHomeAction.RefreshSubscription(subscription.id))
                                capabilities.onRefreshSubscription(subscription.id)
                            },
                            onPing = { capabilities.onTestGroupLatency(subscription.id) },
                            onEdit = { capabilities.onEditSubscription(subscription.id) },
                            onToggleEnabled = {
                                onAction(ProxyHomeAction.ToggleSubscriptionEnabled(subscription.id))
                                capabilities.onToggleSubscriptionEnabled(subscription.id)
                            },
                            onAnnouncement = subscription.announcementUrl?.let { url -> { capabilities.onOpenAnnouncement?.invoke(url) } },
                            onSupport = subscription.supportUrl?.let { url -> { capabilities.onOpenSupport?.invoke(url) } },
                            onSite = subscription.siteUrl?.let { url -> { capabilities.onOpenSite?.invoke(url) } },
                        ),
                        colors = SkipiSubscriptionSummaryColors(
                            surface = SkipiTheme.colors.surface,
                            raisedSurface = SkipiTheme.colors.surfaceVariant,
                            border = SkipiTheme.colors.surfaceVariant,
                            text = SkipiTheme.colors.onSurface,
                            mutedText = SkipiTheme.colors.onSurfaceVariant,
                            enabled = SkipiTheme.colorScheme.success,
                        ),
                    )
                }

                if (state.servers.isEmpty()) {
                    EmptyServerListCard(
                        hasSearch = state.searchQuery.isNotBlank(),
                        onAdd = capabilities.onAddServer,
                    )
                } else {
                    state.servers.forEach { server ->
                        ServerListItem(
                            server = server,
                            onSelect = {
                                onAction(ProxyHomeAction.SelectServer(server.id))
                                capabilities.onSelectServer(server.id)
                            },
                            onTest = {
                                onAction(ProxyHomeAction.TestServer(server.id))
                                capabilities.onTestServerLatency(server.id)
                            },
                            onCopy = { capabilities.onCopyServerLink(server.id) },
                            onEdit = { capabilities.onEditServer(server.id) },
                            onDelete = { capabilities.onDeleteServer(server.id) },
                        )
                    }
                }

                state.statusMessage?.takeIf(String::isNotBlank)?.let { message ->
                    StatusBanner(message = message, isError = state.isStatusError)
                }

                Spacer(Modifier.height(SkipiTheme.spacing.small))
            }
        }
    }
}

@Composable
private fun ServerListItem(
    server: ProxyServerSummary,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val latencyMs = server.latencyMs
    SkipiProxyServerCard(
        state = SkipiProxyServerCardState(
            iconText = server.flag ?: "⚡",
            title = server.title,
            address = server.address,
            protocol = server.protocol,
            transport = server.transport,
            selected = server.selected,
            latency = when {
                latencyMs != null -> SkipiProxyServerLatency(
                    text = stringResource(Res.string.home_latency_milliseconds, latencyMs),
                    kind = SkipiProxyServerLatencyKind.Success,
                )
                server.latencyError -> SkipiProxyServerLatency(
                    text = stringResource(Res.string.home_error),
                    kind = SkipiProxyServerLatencyKind.Error,
                )
                else -> null
            },
            testingLatency = server.latencyTesting,
            canTest = server.canTest,
            canCopy = true,
            canEdit = true,
        ),
        actions = SkipiProxyServerCardActions(
            onSelect = onSelect,
            onTest = onTest,
            onCopy = onCopy,
            onEdit = onEdit,
            onDelete = onDelete,
        ),
        colors = SkipiProxyServerCardColors(
            surface = SkipiTheme.colors.surface,
            raisedSurface = SkipiTheme.colors.surfaceVariant,
            selectedSurface = SkipiTheme.colorScheme.primaryContainer,
            selectedBorder = SkipiTheme.colorScheme.outline,
            text = SkipiTheme.colors.onSurface,
            mutedText = SkipiTheme.colors.onSurfaceVariant,
            success = SkipiTheme.colorScheme.success,
            error = SkipiTheme.colorScheme.error,
            selectedText = SkipiTheme.colorScheme.onPrimaryContainer,
            selectedMutedText = SkipiTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
        ),
    )
}

@Composable
private fun EmptyServerListCard(
    hasSearch: Boolean,
    onAdd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SkipiTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = SkipiTheme.colors.surface),
        border = BorderStroke(1.dp, SkipiTheme.colors.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SkipiTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(if (hasSearch) Res.string.home_empty_search else Res.string.home_empty_servers),
                color = SkipiTheme.colors.onSurface,
                style = SkipiTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!hasSearch) {
                Text(
                    text = stringResource(Res.string.home_empty_hint),
                    color = SkipiTheme.colors.onSurfaceVariant,
                    style = SkipiTheme.typography.bodyMedium,
                )
                Button(onClick = onAdd) {
                    Text(stringResource(Res.string.home_empty_add_server))
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    isError: Boolean,
) {
    if (isError) {
        Surface(
            shape = SkipiTheme.shapes.medium,
            color = SkipiTheme.colorScheme.errorContainer,
            border = BorderStroke(1.dp, SkipiTheme.colorScheme.error.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SkipiTheme.spacing.extraSmall, vertical = SkipiTheme.spacing.extraSmall),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = SkipiTheme.spacing.medium, vertical = SkipiTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = stringResource(Res.string.home_status_error_description),
                    tint = SkipiTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = message,
                    color = SkipiTheme.colorScheme.onErrorContainer,
                    style = SkipiTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        Text(
            text = message,
            color = SkipiTheme.colors.onSurfaceVariant,
            style = SkipiTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun formatSummaryBytes(value: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var amount = value.coerceAtLeast(0).toDouble()
    var unit = 0
    while (amount >= 1024 && unit < units.lastIndex) {
        amount /= 1024
        unit++
    }
    return if (unit == 0) "${amount.toLong()} ${units[unit]}" else "%.2f %s".format(amount, units[unit])
}
