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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.app.home.ProxyConnectionPhase
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyHomeUiState
import app.skipi.app.home.ProxyServerSummary
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiWindowClass

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
                    SkipiProxyHomeHeaderAction(id = "add_server", title = "Добавить сервер"),
                    SkipiProxyHomeHeaderAction(id = "add_subscription", title = "Добавить подписку"),
                    SkipiProxyHomeHeaderAction(id = "import", title = "Импортировать"),
                ),
                toolActions = buildList {
                    val currentSub = state.subscription
                    if (currentSub != null) {
                        add(
                            SkipiProxyHomeHeaderAction(
                                id = "refresh_subscription",
                                title = if (currentSub.refreshing) "Обновление…" else "Обновить подписку",
                                enabled = !currentSub.refreshing,
                            ),
                        )
                    }
                    add(
                        SkipiProxyHomeHeaderAction(
                            id = "search",
                            title = if (state.isSearchVisible) "Скрыть поиск" else "Поиск",
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
                            ProxyConnectionPhase.Connected -> "Подключено"
                            ProxyConnectionPhase.Connecting -> "Подключение…"
                            ProxyConnectionPhase.Disconnected -> "Отключено"
                        },
                        subtitle = when {
                            state.connectionPhase == ProxyConnectionPhase.Connected -> state.selectedServerTitle.ifBlank { "Подключено" }
                            state.connectionPhase == ProxyConnectionPhase.Connecting -> state.selectedServerTitle.ifBlank { "Подготовка туннеля…" }
                            state.selectedServerTitle.isNotBlank() -> "Нажмите для подключения"
                            else -> "Сначала выберите сервер"
                        },
                        profileText = state.activeProfileName?.let { "Профиль: $it" },
                        toggleEnabled = state.canToggleTunnel,
                    ),
                    colors = SkipiConnectionHeroColors(
                        surface = SkipiTheme.colors.surface,
                        raisedSurface = SkipiTheme.colors.surfaceVariant,
                        border = SkipiTheme.colors.surfaceVariant,
                        text = SkipiTheme.colors.onSurface,
                        mutedText = SkipiTheme.colors.onSurfaceVariant,
                        connected = Color(0xFF58D27A),
                        connecting = Color(0xFFE5A93C),
                        disconnected = SkipiTheme.colors.surfaceVariant,
                    ),
                    compact = isCompact,
                    connectContentDescription = "Подключить",
                    disconnectContentDescription = "Отключить",
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
                        title = "Группы прокси",
                        emptyText = "Добавьте сервер или подписку",
                        disabledTitle = { "$it (откл.)" },
                        serverCountText = { "Серверов: $it" },
                        colors = SkipiProxyGroupSelectorColors(
                            surface = SkipiTheme.colors.surface,
                            raisedSurface = SkipiTheme.colors.surfaceVariant,
                            selectedSurface = Color(0xFF737373),
                            border = SkipiTheme.colors.surfaceVariant,
                            selectedBorder = Color(0xFFA4A4A4),
                            text = SkipiTheme.colors.onSurface,
                            mutedText = SkipiTheme.colors.onSurfaceVariant,
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
                        label = "Поиск серверов",
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
                            title = subscription.title.ifBlank { "Подписка" },
                            serverCountText = "Серверов: ${subscription.serverCount}",
                            canPing = subscription.serverCount > 0,
                            enabled = subscription.enabled,
                            refreshing = subscription.refreshing,
                            statusText = buildString {
                                append(if (subscription.enabled) "Включена" else "Отключена")
                                subscription.updateIntervalHours?.trim()?.takeIf(String::isNotBlank)?.let { interval ->
                                    append(" · Автообновление: $interval ч.")
                                }
                            },
                            trafficText = "Трафик: ${formatSummaryBytes(usedBytes)} / ${totalTraffic?.let(::formatSummaryBytes) ?: "∞"}",
                            trafficProgress = progress,
                            expiryText = subscription.expireAtSeconds?.takeIf { it > 0 }?.let { expireAt ->
                                val days = ((expireAt * 1_000L - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                                "Осталось $days дн."
                            },
                            description = subscription.description,
                            announcementText = subscription.announcement,
                            supportLabel = if (subscription.supportUrl != null) "Поддержка" else null,
                            siteLabel = if (subscription.siteUrl != null) "Сайт" else null,
                            updatedText = subscription.lastUpdatedAtMillis?.takeIf { it > 0 }?.let { "Обновлено" },
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
                            enabled = Color(0xFF58D27A),
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
    SkipiProxyServerCard(
        state = SkipiProxyServerCardState(
            iconText = server.flag ?: "⚡",
            title = server.title,
            address = server.address,
            protocol = server.protocol,
            transport = server.transport,
            selected = server.selected,
            latency = when {
                server.latencyMs != null -> SkipiProxyServerLatency(
                    text = "${server.latencyMs} ms",
                    kind = SkipiProxyServerLatencyKind.Success,
                )
                server.latencyError -> SkipiProxyServerLatency(
                    text = "Ошибка",
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
            selectedSurface = Color(0xFF737373),
            selectedBorder = Color(0xFF9A9A9A),
            text = SkipiTheme.colors.onSurface,
            mutedText = SkipiTheme.colors.onSurfaceVariant,
            success = Color(0xFF58D27A),
            error = Color(0xFFFF5252),
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
                text = if (hasSearch) "Ничего не найдено" else "Пока нет серверов",
                color = SkipiTheme.colors.onSurface,
                style = SkipiTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!hasSearch) {
                Text(
                    text = "Добавьте ссылку сервера или подписку",
                    color = SkipiTheme.colors.onSurfaceVariant,
                    style = SkipiTheme.typography.bodyMedium,
                )
                Button(onClick = onAdd) {
                    Text("Добавить сервер")
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
            color = Color(0xFF331515),
            border = BorderStroke(1.dp, Color(0xFF8B2626)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Ошибка",
                    tint = Color(0xFFFF8B8B),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = message,
                    color = Color(0xFFFF8B8B),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    } else {
        Text(
            text = message,
            color = SkipiTheme.colors.onSurfaceVariant,
            fontSize = 13.sp,
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
