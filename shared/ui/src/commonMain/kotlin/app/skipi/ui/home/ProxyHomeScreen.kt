// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyConnectionPhase
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeGroupKind
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomePageUiState
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyHomeUiState
import app.skipi.app.home.ProxyServerSummary
import app.skipi.app.home.ProxySubscriptionSummary
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_close
import app.skipi.ui.resources.common_copy
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_edit
import app.skipi.ui.resources.common_more
import app.skipi.ui.resources.home_action_add_server
import app.skipi.ui.resources.home_action_add_subscription
import app.skipi.ui.resources.home_action_hide_search
import app.skipi.ui.resources.home_action_import
import app.skipi.ui.resources.home_action_refresh_subscription
import app.skipi.ui.resources.home_action_refreshing
import app.skipi.ui.resources.home_action_search
import app.skipi.ui.resources.home_action_unavailable
import app.skipi.ui.resources.home_action_variant_unavailable
import app.skipi.ui.resources.home_add_group
import app.skipi.ui.resources.home_cancel_latency_tests
import app.skipi.ui.resources.home_connection_choose_server
import app.skipi.ui.resources.home_connection_connect
import app.skipi.ui.resources.home_connection_connected
import app.skipi.ui.resources.home_connection_connecting
import app.skipi.ui.resources.home_connection_disconnect
import app.skipi.ui.resources.home_connection_disconnected
import app.skipi.ui.resources.home_connection_preparing
import app.skipi.ui.resources.home_connection_tap_to_connect
import app.skipi.ui.resources.home_empty_hint
import app.skipi.ui.resources.home_empty_search
import app.skipi.ui.resources.home_empty_servers
import app.skipi.ui.resources.home_error
import app.skipi.ui.resources.home_group_disabled
import app.skipi.ui.resources.home_group_empty
import app.skipi.ui.resources.home_group_title
import app.skipi.ui.resources.home_latency_milliseconds
import app.skipi.ui.resources.home_move_down
import app.skipi.ui.resources.home_move_up
import app.skipi.ui.resources.home_operation_failed
import app.skipi.ui.resources.home_runtime_outbound_metric
import app.skipi.ui.resources.proxy_server_list_latency_failed
import app.skipi.ui.resources.home_search_servers
import app.skipi.ui.resources.home_server_address
import app.skipi.ui.resources.home_server_count
import app.skipi.ui.resources.home_server_detail_hint
import app.skipi.ui.resources.home_server_detail_title
import app.skipi.ui.resources.home_server_latency
import app.skipi.ui.resources.home_server_protocol
import app.skipi.ui.resources.home_server_test_latency
import app.skipi.ui.resources.home_server_testing_latency
import app.skipi.ui.resources.home_server_transport
import app.skipi.ui.resources.home_show_qr_code
import app.skipi.ui.resources.home_status_error_description
import app.skipi.ui.resources.home_subscription_days_left
import app.skipi.ui.resources.home_subscription_support
import app.skipi.ui.resources.home_subscription_traffic
import app.skipi.ui.resources.home_subscription_unnamed
import app.skipi.ui.resources.home_subscription_updated
import app.skipi.ui.resources.home_subscription_website
import app.skipi.ui.resources.proxy_editor_strategy_group_select_servers
import app.skipi.ui.resources.proxy_server_copy_full_json
import app.skipi.ui.resources.proxy_server_copy_qr_code
import app.skipi.ui.resources.proxy_server_copy_url
import app.skipi.ui.resources.proxy_server_list_add_amnezia_wg
import app.skipi.ui.resources.proxy_server_list_add_chain_proxy
import app.skipi.ui.resources.proxy_server_list_add_custom
import app.skipi.ui.resources.proxy_server_list_add_hysteria2
import app.skipi.ui.resources.proxy_server_list_add_http
import app.skipi.ui.resources.proxy_server_list_add_olcrtc
import app.skipi.ui.resources.proxy_server_list_add_shadowsocks
import app.skipi.ui.resources.proxy_server_list_add_socks
import app.skipi.ui.resources.proxy_server_list_add_strategy_group
import app.skipi.ui.resources.proxy_server_list_add_trojan
import app.skipi.ui.resources.proxy_server_list_add_vless
import app.skipi.ui.resources.proxy_server_list_add_vmess
import app.skipi.ui.resources.proxy_server_list_add_wireguard
import app.skipi.ui.resources.proxy_server_list_delete_all
import app.skipi.ui.resources.proxy_server_list_delete_duplicates
import app.skipi.ui.resources.proxy_server_list_delete_invalid
import app.skipi.ui.resources.proxy_server_list_import_clipboard
import app.skipi.ui.resources.proxy_server_list_import_file
import app.skipi.ui.resources.proxy_server_list_latency_test
import app.skipi.ui.resources.proxy_server_list_manual_input
import app.skipi.ui.resources.proxy_server_list_option_sort
import app.skipi.ui.resources.proxy_server_list_option_sort_default
import app.skipi.ui.resources.proxy_server_list_option_sort_latency
import app.skipi.ui.resources.proxy_server_list_option_sort_name
import app.skipi.ui.resources.proxy_server_list_restart_service
import app.skipi.ui.resources.proxy_server_list_scan_qr_code
import app.skipi.ui.resources.proxy_server_list_update_subscriptions
import app.skipi.ui.resources.subscriptions_pull_to_refresh
import app.skipi.ui.resources.subscriptions_release_to_refresh
import app.skipi.ui.resources.subscriptions_refreshing
import app.skipi.ui.resources.subscriptions_refreshed
import app.skipi.ui.resources.home_delete_group
import app.skipi.ui.resources.home_edit_group
import app.skipi.ui.resources.subscription_delete_group
import app.skipi.ui.resources.subscription_edit_group
import app.skipi.ui.server.display.CountryFlagUtils
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiWindowClass
import features.subscription.formatSubscriptionUpdateTimestamp
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Unified adaptive Home screen for both Android and Desktop.
 * Adapts between compact phone layout with real horizontal pager motion and wider tablet/desktop layouts.
 * [floatingNavigationBottomInset] moves an overlaid parent navigation bar's clearance from the Home
 * viewport into list scroll space and raises the Home toolbar above that bar. Pass zero for a
 * conventional bottom bar so the supplied [contentPadding] continues to inset the viewport.
 */
@Composable
fun ProxyHomeScreen(
    store: ProxyHomeStore,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    floatingNavigationBottomInset: Dp = 0.dp,
    topContentPadding: Dp? = 40.dp,
    containerColor: Color = SkipiTheme.colors.background,
) {
    val state by store.uiState.collectAsState()

    ProxyHomeScreenContent(
        state = state,
        onAction = { action -> store.dispatch(action) },
        modifier = modifier,
        contentPadding = contentPadding,
        floatingNavigationBottomInset = floatingNavigationBottomInset,
        topContentPadding = topContentPadding,
        containerColor = containerColor,
    )
}

@Composable
fun ProxyHomeScreenContent(
    state: ProxyHomeUiState,
    onAction: (ProxyHomeAction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    floatingNavigationBottomInset: Dp = 0.dp,
    topContentPadding: Dp? = 40.dp,
    containerColor: Color = SkipiTheme.colors.background,
) {
    val windowClass = SkipiTheme.windowClass
    val isWide = proxyHomeLayoutMode(windowClass) == ProxyHomeLayoutMode.WideListAndDetails
    val isCompact = windowClass == SkipiWindowClass.Compact
    val columns = proxyHomeResolveColumns(windowClass, state.displayOptions.requestedColumns)
    val send: (ProxyHomeAction) -> Unit = { action -> dispatchHomeIntent(state, action, onAction) }
    val scope = rememberCoroutineScope()
    val canRefreshAllSubscriptions = canUseHomeAction(
        state,
        ProxyHomeActionId.RefreshAllSubscriptions,
        allowBusy = true,
    )
    val refreshingAllSubscriptions = ProxyHomeActionId.RefreshAllSubscriptions in state.busyActions
    val pullRefreshPresentation = SkipiPullRefreshPresentation(
        pullText = stringResource(Res.string.subscriptions_pull_to_refresh),
        releaseText = stringResource(Res.string.subscriptions_release_to_refresh),
        refreshingText = stringResource(Res.string.subscriptions_refreshing),
        refreshedText = stringResource(Res.string.subscriptions_refreshed),
        color = SkipiTheme.colors.onSurfaceVariant,
    )
    val rememberedPullRefreshController = rememberSkipiPullToRefreshController(
        isRefreshing = refreshingAllSubscriptions,
        onRefresh = { send(ProxyHomeAction.RefreshAllSubscriptions) },
    )
    val pullRefreshController = rememberedPullRefreshController.takeIf { canRefreshAllSubscriptions }

    val maxWidth = when (windowClass) {
        SkipiWindowClass.Compact -> Modifier.fillMaxWidth()
        SkipiWindowClass.Medium,
        SkipiWindowClass.Expanded -> Modifier.widthIn(max = SkipiTheme.spacing.contentMaxWidth)
    }

    val selectedServer = state.selectedServer
    val hasTestableVisibleServers = state.servers.any { it.canTest }
    val refreshSubscriptionTitle = stringResource(Res.string.home_action_refresh_subscription)
    val refreshingSubscriptionTitle = stringResource(Res.string.home_action_refreshing)
    val searchActionTitle = stringResource(Res.string.home_action_search)
    val hideSearchActionTitle = stringResource(Res.string.home_action_hide_search)

    val addActions = buildList {
        if (canUseHomeAction(state, ProxyHomeActionId.AddServer) && state.availableServerKinds.isNotEmpty()) {
            add(SkipiProxyHomeHeaderAction(
                id = "add-server",
                title = stringResource(Res.string.home_action_add_server),
                children = state.availableServerKinds.map { kind ->
                    SkipiProxyHomeHeaderAction("server:" + kind.name, serverKindTitle(kind))
                },
            ))
        }
        if (canUseHomeAction(state, ProxyHomeActionId.AddSubscription)) {
            add(SkipiProxyHomeHeaderAction("add-subscription", stringResource(Res.string.home_action_add_subscription)))
        }
        if (canUseHomeAction(state, ProxyHomeActionId.ImportServers) && state.availableImportSources.isNotEmpty()) {
            add(SkipiProxyHomeHeaderAction(
                id = "import",
                title = stringResource(Res.string.home_action_import),
                children = state.availableImportSources.map { source ->
                    SkipiProxyHomeHeaderAction("import:" + source.name, importSourceTitle(source))
                },
            ))
        }
        if (canUseHomeAction(state, ProxyHomeActionId.EditGroup)) {
            add(SkipiProxyHomeHeaderAction("add-group", stringResource(Res.string.home_add_group)))
        }
    }

    val toolActions = buildList {
        state.subscription?.let { subscription ->
            if (canUseHomeAction(state, ProxyHomeActionId.RefreshSubscription)) {
                add(SkipiProxyHomeHeaderAction(
                    id = "refresh-subscription",
                    title = if (subscription.refreshing) refreshingSubscriptionTitle else refreshSubscriptionTitle,
                    enabled = !subscription.refreshing && ProxyHomeActionId.RefreshSubscription !in state.busyActions,
                ))
            }
        }
        if (canUseHomeAction(state, ProxyHomeActionId.RefreshAllSubscriptions)) {
            add(SkipiProxyHomeHeaderAction("refresh-all-subscriptions", stringResource(Res.string.proxy_server_list_update_subscriptions)))
        }
        if (state.searchEnabled) {
            add(SkipiProxyHomeHeaderAction(
                id = "search",
                title = if (state.isSearchVisible) hideSearchActionTitle else searchActionTitle,
                selected = state.isSearchVisible,
            ))
        }
        if (canUseHomeAction(state, ProxyHomeActionId.SetSort)) {
            add(SkipiProxyHomeHeaderAction(
                id = "sort",
                title = stringResource(Res.string.proxy_server_list_option_sort),
                children = ProxyHomeSortMode.entries.map { mode ->
                    SkipiProxyHomeHeaderAction("sort:" + mode.name, sortModeTitle(mode), selected = state.sortMode == mode)
                },
            ))
        }
        if (canUseHomeAction(state, ProxyHomeActionId.RunServerTool) && state.availableServerTools.isNotEmpty()) {
            add(SkipiProxyHomeHeaderAction(
                id = "server-tools",
                title = stringResource(Res.string.common_more),
                children = state.availableServerTools.map { tool ->
                    SkipiProxyHomeHeaderAction("tool:" + tool.name, serverToolTitle(tool))
                },
            ))
        }
    }

    val pinConnection = state.displayOptions.pinConnectionPanel
    val showFloatingPower = state.displayOptions.connectionMode == ProxyHomeConnectionMode.Compact ||
        state.displayOptions.classicFloatingPowerButton
    val showFloatingToolbar = showFloatingPower ||
        (state.isRunning && state.displayOptions.connectionMode == ProxyHomeConnectionMode.Classic)
    val bottomInsetTreatment = proxyHomeBottomInsetTreatment(
        contentPadding = contentPadding,
        bottomOverlayInset = floatingNavigationBottomInset,
        listReservedBottom = if (showFloatingToolbar) 88.dp else SkipiTheme.spacing.large,
        layoutDirection = LocalLayoutDirection.current,
    )
    val listBottomPadding = bottomInsetTreatment.listBottomPadding

    val pageCount = state.pages.size.coerceAtLeast(1)
    val selectedPageIndex = remember(state.pages, state.selectedGroupId) {
        val idx = state.pages.indexOfFirst { it.group.id == state.selectedGroupId }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = selectedPageIndex,
        pageCount = { pageCount },
    )

    LaunchedEffect(selectedPageIndex, pageCount) {
        if (selectedPageIndex in 0 until pageCount && pagerState.currentPage != selectedPageIndex && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(selectedPageIndex)
        }
    }

    LaunchedEffect(pagerState, state.pages) {
        snapshotFlow { pagerState.settledPage }
            .collect { settledIndex ->
                val page = state.pages.getOrNull(settledIndex)
                if (page != null && page.group.id != state.selectedGroupId) {
                    send(ProxyHomeAction.SelectGroup(page.group.id))
                }
            }
    }

    val onSelectGroupTab: (String) -> Unit = { groupId ->
        val targetIndex = state.pages.indexOfFirst { it.group.id == groupId }
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            scope.launch {
                pagerState.animateScrollToPage(targetIndex)
            }
        }
        if (groupId != state.selectedGroupId) {
            send(ProxyHomeAction.SelectGroup(groupId))
        }
    }
    val groupSelectorContent: (@Composable () -> Unit)? = if (state.groups.size > 1) {
        {
            HomeGroupSelector(
                state = state,
                onAction = send,
                onSelectGroup = onSelectGroupTab,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerColor)
            .padding(bottomInsetTreatment.viewportPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(maxWidth)
                .padding(horizontal = SkipiTheme.spacing.screenHorizontal)
                .padding(top = topContentPadding ?: 40.dp)
                .padding(bottom = SkipiTheme.spacing.screenVertical),
        ) {
            SkipiProxyHomeHeader(
                state = SkipiProxyHomeHeaderState(
                    title = "SKIPI",
                    latencyTesting = state.isTestingLatency,
                    latencyEnabled = if (state.isTestingLatency) {
                        canUseHomeAction(state, ProxyHomeActionId.CancelLatencyTests, allowBusy = true)
                    } else {
                        hasTestableVisibleServers && canUseHomeAction(state, ProxyHomeActionId.TestVisibleServers)
                    },
                    latencyActionDescription = stringResource(
                        if (state.isTestingLatency) Res.string.home_cancel_latency_tests else Res.string.proxy_server_list_latency_test,
                    ),
                ),
                colors = SkipiProxyHomeHeaderColors(
                    text = SkipiTheme.colors.onBackground,
                    mutedText = SkipiTheme.colors.onSurfaceVariant,
                    accent = SkipiTheme.colors.accent,
                ),
                addActions = addActions,
                toolActions = toolActions,
                onTestLatency = {
                    if (state.isTestingLatency) send(ProxyHomeAction.CancelLatencyTests)
                    else send(ProxyHomeAction.TestAllVisibleServers)
                },
                onAddAction = { id ->
                    when {
                        id == "add-subscription" -> send(ProxyHomeAction.AddSubscription)
                        id == "add-group" -> send(ProxyHomeAction.EditGroup(null))
                        id.startsWith("server:") -> state.availableServerKinds
                            .firstOrNull { it.name == id.substringAfter("server:") }
                            ?.let { kind -> send(ProxyHomeAction.AddServer(kind)) }
                        id.startsWith("import:") -> state.availableImportSources
                            .firstOrNull { it.name == id.substringAfter("import:") }
                            ?.let { source -> send(ProxyHomeAction.ImportServers(source)) }
                    }
                },
                onToolAction = { id ->
                    when {
                        id == "refresh-subscription" -> state.subscription?.let { send(ProxyHomeAction.RefreshSubscription(it.id)) }
                        id == "refresh-all-subscriptions" -> send(ProxyHomeAction.RefreshAllSubscriptions)
                        id == "search" -> send(ProxyHomeAction.SetSearchVisible(!state.isSearchVisible))
                        id.startsWith("sort:") -> ProxyHomeSortMode.entries
                            .firstOrNull { it.name == id.substringAfter("sort:") }
                            ?.let { mode -> send(ProxyHomeAction.SetSort(mode)) }
                        id.startsWith("tool:") -> state.availableServerTools
                            .firstOrNull { it.name == id.substringAfter("tool:") }
                            ?.let { tool -> send(ProxyHomeAction.RunServerTool(tool)) }
                    }
                },
            )

            Spacer(Modifier.height(SkipiTheme.spacing.small))

            if (isWide) {
                HomeConnectionPanel(
                    state = state,
                    compact = false,
                    onToggle = { send(ProxyHomeAction.ToggleTunnel) },
                )
                Spacer(Modifier.height(SkipiTheme.spacing.medium))
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.medium),
                ) {
                    val wideSelectedPage = state.pages.firstOrNull { it.group.id == state.selectedGroupId }
                        ?: state.pages.firstOrNull()
                        ?: ProxyHomePageUiState(
                            group = state.groups.firstOrNull() ?: app.skipi.app.home.ProxyGroupSummary(
                                id = "default",
                                title = "",
                                serverCount = state.servers.size,
                                enabled = true,
                            ),
                            servers = state.servers,
                            subscription = state.subscription,
                            subscriptionExpanded = true,
                        )
                    Box(modifier = Modifier.weight(1.1f).fillMaxSize()) {
                        PageServerGrid(
                            page = wideSelectedPage,
                            state = state,
                            columns = columns,
                            contentPadding = PaddingValues(bottom = listBottomPadding),
                            pullRefreshController = pullRefreshController,
                            pullRefreshPresentation = pullRefreshPresentation,
                            groupSelector = groupSelectorContent,
                            scrollingHeader = if (state.searchEnabled && state.isSearchVisible) {
                                {
                                    SkipiProxyHomeSearchField(
                                        value = state.searchQuery,
                                        onValueChange = { query -> send(ProxyHomeAction.SetSearchQuery(query)) },
                                        label = stringResource(Res.string.home_search_servers),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else {
                                null
                            },
                            onAction = send,
                        )
                    }
                    SelectedServerDetailsPane(
                        state = state,
                        server = selectedServer,
                        onTest = { s -> send(ProxyHomeAction.TestServer(s.id)) },
                        modifier = Modifier.weight(0.9f).fillMaxSize(),
                    )
                }
            } else {
                if (pinConnection) {
                    val showPinnedSearch = state.searchEnabled && state.isSearchVisible
                    HomeConnectionPanel(
                        state = state,
                        compact = isCompact,
                        onToggle = { send(ProxyHomeAction.ToggleTunnel) },
                        modifier = Modifier.fillMaxWidth().padding(
                            bottom = if (showPinnedSearch) {
                                SkipiTheme.spacing.small
                            } else {
                                0.dp
                            },
                        ),
                        showCardPowerControl = !showFloatingPower,
                    )
                    AnimatedVisibility(
                        visible = showPinnedSearch,
                        enter = fadeIn() + expandVertically(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        SkipiProxyHomeSearchField(
                            value = state.searchQuery,
                            onValueChange = { query -> send(ProxyHomeAction.SetSearchQuery(query)) },
                            label = stringResource(Res.string.home_search_servers),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(SkipiTheme.spacing.small))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (state.pages.isEmpty()) {
                        val fallbackPage = ProxyHomePageUiState(
                            group = state.groups.firstOrNull() ?: app.skipi.app.home.ProxyGroupSummary(
                                id = "default",
                                title = "",
                                serverCount = state.servers.size,
                                enabled = true,
                            ),
                            servers = state.servers,
                            subscription = state.subscription,
                            subscriptionExpanded = true,
                        )
                        PageServerGrid(
                            page = fallbackPage,
                            state = state,
                            columns = columns,
                            contentPadding = PaddingValues(bottom = listBottomPadding),
                            pullRefreshController = pullRefreshController,
                            pullRefreshPresentation = pullRefreshPresentation,
                            groupSelector = groupSelectorContent,
                            scrollingHeader = if (pinConnection) null else {
                                {
                                    val showSearch = state.searchEnabled && state.isSearchVisible
                                    Column {
                                        HomeConnectionPanel(
                                            state = state,
                                            compact = isCompact,
                                            onToggle = { send(ProxyHomeAction.ToggleTunnel) },
                                            modifier = Modifier.fillMaxWidth().padding(
                                                bottom = if (showSearch) {
                                                    SkipiTheme.spacing.small
                                                } else {
                                                    0.dp
                                                },
                                            ),
                                            showCardPowerControl = !showFloatingPower,
                                        )
                                        AnimatedVisibility(
                                            visible = showSearch,
                                            enter = fadeIn() + expandVertically(),
                                            exit = shrinkVertically() + fadeOut(),
                                        ) {
                                            SkipiProxyHomeSearchField(
                                                value = state.searchQuery,
                                                onValueChange = { query -> send(ProxyHomeAction.SetSearchQuery(query)) },
                                                label = stringResource(Res.string.home_search_servers),
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            },
                            onAction = send,
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = state.subscriptionSwipeEnabled,
                            verticalAlignment = Alignment.Top,
                            pageSpacing = SkipiTheme.spacing.medium,
                            beyondViewportPageCount = 0,
                        ) { pageIndex ->
                            val page = state.pages.getOrNull(pageIndex) ?: state.pages[0]
                            PageServerGrid(
                                page = page,
                                state = state,
                                columns = columns,
                                contentPadding = PaddingValues(bottom = listBottomPadding),
                                pullRefreshController = pullRefreshController,
                                pullRefreshPresentation = pullRefreshPresentation,
                                groupSelector = groupSelectorContent,
                                scrollingHeader = if (pinConnection) null else {
                                    {
                                        val showSearch = state.searchEnabled && state.isSearchVisible
                                        Column {
                                            HomeConnectionPanel(
                                                state = state,
                                                compact = isCompact,
                                                onToggle = { send(ProxyHomeAction.ToggleTunnel) },
                                                modifier = Modifier.fillMaxWidth().padding(
                                                    bottom = if (showSearch) {
                                                        SkipiTheme.spacing.small
                                                    } else {
                                                        0.dp
                                                    },
                                                ),
                                                showCardPowerControl = !showFloatingPower,
                                            )
                                            AnimatedVisibility(
                                                visible = showSearch,
                                                enter = fadeIn() + expandVertically(),
                                                exit = shrinkVertically() + fadeOut(),
                                            ) {
                                                SkipiProxyHomeSearchField(
                                                    value = state.searchQuery,
                                                    onValueChange = { query -> send(ProxyHomeAction.SetSearchQuery(query)) },
                                                    label = stringResource(Res.string.home_search_servers),
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                    }
                                },
                                onAction = send,
                            )
                        }
                    }

                    SkipiProxyHomeFloatingToolbar(
                        running = state.isRunning,
                        isConnecting = state.isConnecting,
                        isTestingLatency = state.isTestingLatency,
                        canToggleTunnel = state.canToggleTunnel && canUseHomeAction(state, ProxyHomeActionId.ToggleTunnel, allowBusy = true),
                        canTestLatency = if (state.isTestingLatency) {
                            canUseHomeAction(state, ProxyHomeActionId.CancelLatencyTests, allowBusy = true)
                        } else {
                            hasTestableVisibleServers && canUseHomeAction(state, ProxyHomeActionId.TestVisibleServers)
                        },
                        showToggleAction = showFloatingPower,
                        showPingAction = true,
                        onToggleRunning = { send(ProxyHomeAction.ToggleTunnel) },
                        onTestLatency = {
                            if (state.isTestingLatency) send(ProxyHomeAction.CancelLatencyTests)
                            else send(ProxyHomeAction.TestAllVisibleServers)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = bottomInsetTreatment.floatingToolbarBottomPadding),
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.proxyHomeGroupSection(
    groupSelector: (@Composable () -> Unit)?,
) {
    if (groupSelector == null) return
    item(
        key = "proxy-groups-heading",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "proxy-groups-heading",
    ) {
        Text(
            text = stringResource(Res.string.home_group_title),
            color = SkipiTheme.colors.onSurface,
            style = SkipiTheme.typography.titleMedium,
            fontWeight = themedFontWeight(FontWeight.Bold),
            modifier = Modifier
                .padding(horizontal = SkipiTheme.spacing.extraSmall)
                .padding(top = SkipiTheme.spacing.medium, bottom = SkipiTheme.spacing.small),
        )
    }
    item(
        key = "proxy-groups-selector",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "proxy-groups-selector",
    ) {
        Box(Modifier.fillMaxWidth().padding(bottom = SkipiTheme.spacing.small)) {
            groupSelector()
        }
    }
}

@Composable
private fun PageServerGrid(
    page: ProxyHomePageUiState,
    state: ProxyHomeUiState,
    columns: Int,
    contentPadding: PaddingValues,
    pullRefreshController: SkipiPullToRefreshController?,
    pullRefreshPresentation: SkipiPullRefreshPresentation,
    groupSelector: (@Composable () -> Unit)?,
    scrollingHeader: (@Composable () -> Unit)?,
    onAction: (ProxyHomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageServers = page.servers
    val subscription = page.subscription
    val subscriptionGroup = page.group.kind == ProxyHomeGroupKind.Subscription
    val providerSurface = SkipiTheme.colors.surface
    val providerBorder = SkipiTheme.colors.onSurface.copy(alpha = 0.14f)
    val providerDivider = SkipiTheme.colors.onSurface.copy(alpha = 0.08f)
    val pullRefreshBaseHeightPx = with(LocalDensity.current) {
        (pullRefreshPresentation.circleSize + 32.dp).toPx()
    }
    Box(modifier = modifier.fillMaxSize()) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize()
            .then(pullRefreshController?.let { controller ->
                Modifier
                    .skipiPullToRefresh(controller)
                    .graphicsLayer {
                        translationY = skipiPullRefreshHeaderHeightPx(
                            refreshState = controller.refreshState,
                            dragOffsetPx = controller.dragOffset,
                            thresholdPx = controller.thresholdPx,
                            baseHeightPx = pullRefreshBaseHeightPx,
                            completionProgress = controller.refreshCompleteProgress,
                        )
                    }
            } ?: Modifier),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.small),
      ) {
        if (scrollingHeader != null) {
            item(key = "scrolling-header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Box(Modifier.padding(bottom = SkipiTheme.spacing.small)) {
                    scrollingHeader()
                }
            }
        }
        item(key = "home-status-notice", span = { GridItemSpan(maxLineSpan) }, contentType = "status") {
            val currentPresentation = state.localError?.let { localError ->
                ProxyHomeStatusPresentation(
                    message = proxyHomeLocalErrorMessage(
                        error = localError,
                        unavailable = stringResource(Res.string.home_action_unavailable),
                        variantUnavailable = stringResource(Res.string.home_action_variant_unavailable),
                        operationFailed = stringResource(Res.string.home_operation_failed),
                    ),
                    isError = true,
                )
            } ?: state.statusMessage
                ?.takeIf(String::isNotBlank)
                ?.let { message -> ProxyHomeStatusPresentation(message, state.isStatusError) }

            ProxyHomeStatusNotice(
                currentPresentation = currentPresentation,
                onDismiss = { onAction(ProxyHomeAction.DismissMessage) },
            )
        }
        if (subscriptionGroup && subscription != null) {
            proxyHomeGroupSection(groupSelector)
            item(key = "subscription-${subscription.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "subscription") {
                SkipiSubscriptionServerPanelSegment(
                    segment = if (page.subscriptionExpanded) {
                        SkipiSubscriptionPanelSegment.ExpandedHeader
                    } else {
                        SkipiSubscriptionPanelSegment.CollapsedHeader
                    },
                    surfaceColor = providerSurface,
                    borderColor = providerBorder,
                    dividerColor = providerDivider,
                ) {
                    HomeSubscriptionCard(
                        subscription = subscription,
                        expanded = page.subscriptionExpanded,
                        onToggleExpanded = { onAction(ProxyHomeAction.ToggleSubscriptionExpanded(page.group.id)) },
                        state = state,
                        onAction = onAction,
                    )
                }
            }
            if (page.subscriptionExpanded) {
                if (pageServers.isEmpty()) {
                    item(key = "subscription-empty-${subscription.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "subscription_empty") {
                        SkipiSubscriptionServerPanelSegment(
                            segment = SkipiSubscriptionPanelSegment.LastBody,
                            surfaceColor = providerSurface,
                            borderColor = providerBorder,
                            dividerColor = providerDivider,
                        ) {
                            Text(
                                text = stringResource(if (state.searchQuery.isNotBlank()) Res.string.home_empty_search else Res.string.home_empty_servers),
                                color = SkipiTheme.colors.onSurfaceVariant,
                                style = SkipiTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SkipiTheme.spacing.medium, vertical = SkipiTheme.spacing.large),
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = pageServers,
                        key = { _, server -> "server-${server.id}" },
                        span = { _, _ -> GridItemSpan(maxLineSpan) },
                        contentType = { _, _ -> "subscription_server" },
                    ) { serverIndex, server ->
                        val segment = if (serverIndex == pageServers.lastIndex) {
                            SkipiSubscriptionPanelSegment.LastBody
                        } else {
                            SkipiSubscriptionPanelSegment.Body
                        }
                        SkipiSubscriptionServerPanelSegment(
                            segment = segment,
                            surfaceColor = providerSurface,
                            borderColor = providerBorder,
                            dividerColor = providerDivider,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(durationMillis = 180),
                                fadeOutSpec = tween(durationMillis = 120),
                                placementSpec = null,
                            ),
                        ) {
                            ServerListItem(
                                server = server,
                                state = state,
                                serverIndex = serverIndex,
                                totalServers = pageServers.size,
                                columns = columns,
                                inSubscriptionGroup = true,
                                onAction = onAction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp),
                            )
                        }
                    }
                }
            }
        } else {
            if (!subscriptionGroup) {
                proxyHomeGroupSection(groupSelector)
            }
            if (pageServers.isEmpty()) {
                item(key = "empty-server-list", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    Box(Modifier.padding(bottom = SkipiTheme.spacing.small)) {
                        EmptyServerListCard(
                            hasSearch = state.searchQuery.isNotBlank(),
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = pageServers,
                    key = { _, server -> "server-${server.id}" },
                    contentType = { _, _ -> "server" },
                ) { serverIndex, server ->
                    ServerListItem(
                        server = server,
                        state = state,
                        serverIndex = serverIndex,
                        totalServers = pageServers.size,
                        columns = columns,
                        onAction = onAction,
                        inSubscriptionGroup = subscriptionGroup,
                        modifier = Modifier
                            .padding(bottom = SkipiTheme.spacing.extraSmall)
                            .animateItem(placementSpec = null),
                    )
                }
            }
        }
      }
      pullRefreshController?.let { controller ->
          SkipiPullRefreshHeader(
              controller = controller,
              presentation = pullRefreshPresentation,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = contentPadding.calculateTopPadding()),
          )
      }
    }
}

@Composable
private fun rememberSessionDurationText(connected: Boolean): String? {
    if (!connected) return null
    val sessionDuration by produceState(initialValue = "00:00:00", connected) {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            val totalSeconds = mark.elapsedNow().inWholeSeconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val h = if (hours < 10) "0$hours" else "$hours"
            val m = if (minutes < 10) "0$minutes" else "$minutes"
            val s = if (seconds < 10) "0$seconds" else "$seconds"
            value = "$h:$m:$s"
            delay(1000)
        }
    }
    return sessionDuration
}

@Composable
private fun HomeConnectionPanel(
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

    val sessionDuration = rememberSessionDurationText(state.connectionPhase == ProxyConnectionPhase.Connected)

    val currentServer = state.selectedServer
    val currentServerLatencyMs = currentServer?.latencyMs
    val latency = when {
        currentServer?.latencyTesting == true -> SkipiProxyHeroLatency(
            text = stringResource(Res.string.home_server_testing_latency),
            color = SkipiTheme.colors.accent,
            testing = true,
        )
        currentServerLatencyMs != null -> SkipiProxyHeroLatency(
            text = stringResource(Res.string.home_latency_milliseconds, currentServerLatencyMs),
            color = serverLatencyColor(currentServer, state.displayOptions),
            testing = false,
        )
        currentServer?.latencyError == true -> SkipiProxyHeroLatency(
            text = currentServer.latencyErrorText?.takeIf(String::isNotBlank)
                ?: stringResource(Res.string.proxy_server_list_latency_failed),
            color = serverLatencyColor(currentServer, state.displayOptions),
            testing = false,
        )
        else -> null
    }

    val memoryText = if (state.displayOptions.showTunnelMemory) {
        state.runtimeOutboundMetric?.takeIf(String::isNotBlank)?.let { metric ->
            stringResource(Res.string.home_runtime_outbound_metric, metric)
        }
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
        sessionDurationText = sessionDuration,
        latency = latency,
        memoryText = memoryText,
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
    } else {
        SkipiProxyHeroClassicCard(
            state = heroState,
            colors = colors,
            typography = typography,
            compact = compact,
            onToggle = onToggle,
            modifier = modifier,
            connectContentDescription = stringResource(Res.string.home_connection_connect),
            disconnectContentDescription = stringResource(Res.string.home_connection_disconnect),
        )
    }
}

@Composable
private fun HomeGroupSelector(
    state: ProxyHomeUiState,
    onAction: (ProxyHomeAction) -> Unit,
    onSelectGroup: (String) -> Unit = { groupId -> onAction(ProxyHomeAction.SelectGroup(groupId)) },
    modifier: Modifier = Modifier,
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
                if (state.groups.getOrNull(groupIndex - 1)?.canMove == true) {
                    add(SkipiProxyGroupPickerAction("move-up", stringResource(Res.string.home_move_up)))
                }
                if (state.groups.getOrNull(groupIndex + 1)?.canMove == true) {
                    add(SkipiProxyGroupPickerAction("move-down", stringResource(Res.string.home_move_down)))
                }
            }
        }
    }.toMap()
    SkipiProxyGroupPicker(
        groups = groupItems,
        selectedGroupId = state.selectedGroupId,
        colors = SkipiProxyGroupPickerColors(
            surface = SkipiTheme.colors.surface,
            raisedSurface = SkipiTheme.colors.surfaceVariant,
            border = SkipiTheme.colors.onSurface.copy(alpha = 0.10f),
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

@Composable
private fun HomeSubscriptionCard(
    subscription: ProxySubscriptionSummary,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    state: ProxyHomeUiState,
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
            canRefresh = canUseHomeAction(state, ProxyHomeActionId.RefreshSubscription),
        canPingAction = canUseHomeAction(state, ProxyHomeActionId.PingSubscription) &&
                (!subscription.pinging || subscription.canCancelPing),
            canEdit = canUseHomeAction(state, ProxyHomeActionId.EditSubscription),
        ),
        actions = SkipiSubscriptionSummaryActions(
            onRefresh = { onAction(ProxyHomeAction.RefreshSubscription(subscription.id)) },
            onPing = { onAction(ProxyHomeAction.PingSubscription(subscription.id)) },
            onEdit = { onAction(ProxyHomeAction.EditSubscription(subscription.id)) },
            onAnnouncement = subscription.announcementUrl
                ?.takeIf { canUseHomeAction(state, ProxyHomeActionId.OpenExternalLink) }
                ?.let { url -> { onAction(ProxyHomeAction.OpenExternalLink(url)) } },
            onSupport = subscription.supportUrl
                ?.takeIf { canUseHomeAction(state, ProxyHomeActionId.OpenExternalLink) }
                ?.let { url -> { onAction(ProxyHomeAction.OpenExternalLink(url)) } },
            onSite = subscription.siteUrl
                ?.takeIf { canUseHomeAction(state, ProxyHomeActionId.OpenExternalLink) }
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

@Composable
private fun SelectedServerDetailsPane(
    state: ProxyHomeUiState,
    server: ProxyServerSummary?,
    onTest: (ProxyServerSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = SkipiTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = SkipiTheme.colors.surface),
        border = BorderStroke(1.dp, SkipiTheme.colors.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(SkipiTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(Res.string.home_server_detail_title),
                color = SkipiTheme.colors.onSurface,
                style = SkipiTheme.typography.titleLarge,
                fontWeight = themedFontWeight(FontWeight.Bold),
            )
            if (server == null) {
                Text(
                    text = stringResource(Res.string.home_server_detail_hint),
                    color = SkipiTheme.colors.onSurfaceVariant,
                    style = SkipiTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = server.title,
                    color = SkipiTheme.colors.onSurface,
                    style = SkipiTheme.typography.headlineSmall,
                    fontWeight = themedFontWeight(FontWeight.SemiBold),
                )
                ServerDetailValue(
                    label = stringResource(Res.string.home_server_address),
                    value = server.address,
                )
                ServerDetailValue(
                    label = stringResource(Res.string.home_server_protocol),
                    value = server.protocol,
                )
                server.transport?.takeIf(String::isNotBlank)?.let { transport ->
                    ServerDetailValue(
                        label = stringResource(Res.string.home_server_transport),
                        value = transport,
                    )
                }
                val latencyMs = server.latencyMs
                val latency = when {
                    server.latencyTesting -> stringResource(Res.string.home_server_testing_latency)
                    latencyMs != null -> stringResource(Res.string.home_latency_milliseconds, latencyMs)
                    server.latencyError -> server.latencyErrorText?.takeIf(String::isNotBlank)
                        ?: stringResource(Res.string.proxy_server_list_latency_failed)
                    else -> "—"
                }
                ServerDetailValue(
                    label = stringResource(Res.string.home_server_latency),
                    value = latency,
                )
                OutlinedButton(
                    onClick = { onTest(server) },
                    enabled = server.canTest && !server.latencyTesting && canUseHomeAction(state, ProxyHomeActionId.TestServer),
                ) {
                    Text(stringResource(Res.string.home_server_test_latency))
                }
            }
        }
    }
}

@Composable
private fun ServerDetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.extraSmall)) {
        Text(
            text = label,
            color = SkipiTheme.colors.onSurfaceVariant,
            style = SkipiTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = SkipiTheme.colors.onSurface,
            style = SkipiTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun buildServerMenuActions(
    server: ProxyServerSummary,
    state: ProxyHomeUiState,
    serverIndex: Int,
    totalServers: Int,
): List<SkipiProxyServerCardMenuAction> {
    val canMove = state.sortMode == ProxyHomeSortMode.Default && state.searchQuery.isBlank() &&
        canUseHomeAction(state, ProxyHomeActionId.MoveServer)
    return buildList {
        val serverCopyFormats = if (canUseHomeAction(state, ProxyHomeActionId.CopyServer)) {
            server.availableCopyFormats.intersect(state.availableCopyFormats)
        } else {
            emptySet()
        }
        if (serverCopyFormats.isNotEmpty()) {
            serverCopyFormats.forEach { format ->
                add(SkipiProxyServerCardMenuAction("copy:" + format.name, copyFormatTitle(format)))
            }
        }
        if (
            canUseHomeAction(state, ProxyHomeActionId.ShowServerQr) &&
            ProxyHomeCopyFormat.QrCode in server.availableCopyFormats &&
            ProxyHomeCopyFormat.QrCode in state.availableCopyFormats
        ) {
            add(SkipiProxyServerCardMenuAction("show-qr", stringResource(Res.string.home_show_qr_code)))
        }
        if (canUseHomeAction(state, ProxyHomeActionId.EditServer)) {
            add(SkipiProxyServerCardMenuAction("edit", stringResource(Res.string.common_edit)))
        }
        if (canUseHomeAction(state, ProxyHomeActionId.DeleteServer)) {
            add(SkipiProxyServerCardMenuAction("delete", stringResource(Res.string.common_delete)))
        }
        if (server.isStrategyGroup && canUseHomeAction(state, ProxyHomeActionId.OpenStrategyMemberPicker)) {
            add(SkipiProxyServerCardMenuAction("strategy-members", stringResource(Res.string.proxy_editor_strategy_group_select_servers)))
        }
        if (canMove && serverIndex > 0) {
            add(SkipiProxyServerCardMenuAction("move-up", stringResource(Res.string.home_move_up)))
        }
        if (canMove && serverIndex in 0 until (totalServers - 1)) {
            add(SkipiProxyServerCardMenuAction("move-down", stringResource(Res.string.home_move_down)))
        }
    }
}

private fun handleServerMenuAction(
    actionId: String,
    server: ProxyServerSummary,
    onAction: (ProxyHomeAction) -> Unit,
) {
    when {
        actionId.startsWith("copy:") -> ProxyHomeCopyFormat.entries
            .firstOrNull { it.name == actionId.substringAfter("copy:") }
            ?.let { format -> onAction(ProxyHomeAction.CopyServer(server.id, format)) }
        actionId == "show-qr" -> onAction(ProxyHomeAction.ShowServerQr(server.id))
        actionId == "edit" -> onAction(ProxyHomeAction.EditServer(server.id))
        actionId == "delete" -> onAction(ProxyHomeAction.DeleteServer(server.id))
        actionId == "strategy-members" -> onAction(ProxyHomeAction.OpenStrategyMemberPicker(server.id))
        actionId == "move-up" -> onAction(ProxyHomeAction.MoveServer(server.id, -1))
        actionId == "move-down" -> onAction(ProxyHomeAction.MoveServer(server.id, 1))
    }
}

@Composable
private fun ServerListItem(
    server: ProxyServerSummary,
    state: ProxyHomeUiState,
    serverIndex: Int,
    totalServers: Int,
    columns: Int,
    onAction: (ProxyHomeAction) -> Unit,
    inSubscriptionGroup: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val menuActions = buildServerMenuActions(server, state, serverIndex, totalServers)
    val serverLatencyMs = server.latencyMs
    val latencyText = when {
        server.latencyTesting -> stringResource(Res.string.home_server_testing_latency)
        serverLatencyMs != null -> stringResource(Res.string.home_latency_milliseconds, serverLatencyMs)
        server.latencyError -> server.latencyErrorText?.takeIf(String::isNotBlank)
            ?: stringResource(Res.string.proxy_server_list_latency_failed)
        else -> null
    }
    val latencyColor = serverLatencyColor(server, state.displayOptions)
    val protocolColor = when (server.protocol.lowercase()) {
        "vless", "vmess" -> Color(0xFF5C6DB0)
        "hysteria2", "hy2" -> Color(0xFFB65A3D)
        "shadowsocks", "ss" -> Color(0xFF387D58)
        "trojan" -> Color(0xFF9C4146)
        else -> Color(0xFF426A4B)
    }

    if (columns > 1 || inSubscriptionGroup) {
        var menuExpanded by remember(server.id) { mutableStateOf(false) }
        SkipiProxyServerCompactListCard(
            state = SkipiProxyServerCompactListCardState(
                flag = server.flag,
                title = server.title,
                summary = server.address,
                protocol = server.protocol,
                protocolColor = protocolColor,
                transport = server.transport,
                transportTextColor = Color.White.copy(alpha = 0.9f),
                transportContainerColor = Color(0xFF40505A).copy(alpha = 0.5f),
                selected = server.selected,
                inSubscriptionGroup = inSubscriptionGroup,
                latencyText = latencyText,
                latencyTesting = server.latencyTesting,
                latencyColor = latencyColor,
                progressColor = SkipiTheme.colors.accent,
                isStrategyGroup = server.isStrategyGroup,
                isDragging = false,
            ),
            colors = SkipiProxyServerCompactListCardColors(
                surface = SkipiTheme.colors.surface,
                selectedSurface = SkipiTheme.colorScheme.primaryContainer,
                selectedBorder = SkipiTheme.colorScheme.outline,
            ),
            titleFontWeight = themedFontWeight(FontWeight.SemiBold),
            latencyFontWeight = themedFontWeight(FontWeight.Medium),
            onSelect = { onAction(ProxyHomeAction.SelectServer(server.id)) },
            onLongPress = { menuExpanded = true },
            overlay = {
                if (menuActions.isNotEmpty()) {
                    SkipiHomeDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        menuActions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.title) },
                                enabled = action.enabled,
                                colors = skipiHomeDropdownItemColors(),
                                onClick = {
                                    menuExpanded = false
                                    handleServerMenuAction(action.id, server, onAction)
                                },
                            )
                        }
                    }
                }
            },
            modifier = modifier,
        )
    } else {
        val copyMenuActions = menuActions.filter { it.id.startsWith("copy:") }
        val overflowActions = menuActions.filterNot {
            it.id.startsWith("copy:") || it.id == "edit" ||
                (it.id == "strategy-members" && server.isStrategyGroup)
        }
        var copyMenuExpanded by remember(server.id) { mutableStateOf(false) }
        var overflowMenuExpanded by remember(server.id) { mutableStateOf(false) }
        SkipiProxyServerExpandedListCard(
            state = SkipiProxyServerExpandedListCardState(
                flag = server.flag,
                title = server.title,
                summary = server.address,
                protocol = server.protocol,
                protocolColor = protocolColor,
                transport = server.transport,
                transportTextColor = Color.White.copy(alpha = 0.9f),
                transportContainerColor = Color(0xFF40505A).copy(alpha = 0.5f),
                selected = server.selected,
                groupName = null,
                latencyText = latencyText,
                latencyTesting = server.latencyTesting,
                latencyColor = latencyColor,
                progressColor = SkipiTheme.colors.accent,
                isStrategyGroup = server.isStrategyGroup,
                isDragging = false,
            ),
            colors = SkipiProxyServerExpandedListCardColors(
                surface = SkipiTheme.colors.surface,
                selectedSurface = SkipiTheme.colorScheme.primaryContainer,
                selectedBorder = SkipiTheme.colorScheme.outline,
            ),
            titleFontWeight = themedFontWeight(FontWeight.SemiBold),
            latencyFontWeight = themedFontWeight(FontWeight.Medium),
            onSelect = { onAction(ProxyHomeAction.SelectServer(server.id)) },
            actions = {
                val copyContentDescription = stringResource(Res.string.common_copy)
                if (copyMenuActions.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { copyMenuExpanded = true },
                            modifier = Modifier.semantics {
                                contentDescription = copyContentDescription
                            },
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = SkipiTheme.colors.onSurface)
                        }
                        SkipiHomeDropdownMenu(
                            expanded = copyMenuExpanded,
                            onDismissRequest = { copyMenuExpanded = false },
                        ) {
                            copyMenuActions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.title) },
                                    enabled = action.enabled,
                                    colors = skipiHomeDropdownItemColors(),
                                    onClick = {
                                        copyMenuExpanded = false
                                        handleServerMenuAction(action.id, server, onAction)
                                    },
                                )
                            }
                        }
                    }
                }
                if (menuActions.any { it.id == "edit" }) {
                    IconButton(onClick = { handleServerMenuAction("edit", server, onAction) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.common_edit), tint = SkipiTheme.colors.onSurface)
                    }
                }
                if (overflowActions.isNotEmpty()) {
                    val moreContentDesc = stringResource(Res.string.common_more) + ": " + server.title
                    Box {
                        IconButton(
                            onClick = { overflowMenuExpanded = true },
                            modifier = Modifier.semantics {
                                contentDescription = moreContentDesc
                            },
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = SkipiTheme.colors.onSurface)
                        }
                        SkipiHomeDropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false },
                        ) {
                            overflowActions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.title) },
                                    enabled = action.enabled,
                                    colors = skipiHomeDropdownItemColors(),
                                    onClick = {
                                        overflowMenuExpanded = false
                                        handleServerMenuAction(action.id, server, onAction)
                                    },
                                )
                            }
                        }
                    }
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun serverLatencyColor(
    server: ProxyServerSummary,
    options: ProxyHomeDisplayOptions,
): Color {
    if (server.latencyTesting) return SkipiTheme.colors.accent

    val latencyMs = server.latencyMs
    if (latencyMs == null && !server.latencyError) return SkipiTheme.colors.onSurfaceVariant

    val darkTheme = SkipiTheme.colorScheme.isDark
    val failedColor = options.latencyErrorColor?.let { Color(it) } ?: SkipiTheme.colorScheme.error
    return when {
        server.latencyError || latencyMs == null || latencyMs >= 400L -> failedColor
        latencyMs < 100L -> options.latencyFastColor?.let { Color(it) } ?: SkipiTheme.colorScheme.success
        latencyMs < 200L -> options.latencyMediumColor?.let { Color(it) } ?: SkipiTheme.colorScheme.warning
        else -> options.latencySlowColor?.let { Color(it) }
            ?: if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400)
    }
}

@Composable
private fun EmptyServerListCard(
    hasSearch: Boolean,
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
                fontWeight = themedFontWeight(FontWeight.Bold),
            )
            if (!hasSearch) {
                Text(
                    text = stringResource(Res.string.home_empty_hint),
                    color = SkipiTheme.colors.onSurfaceVariant,
                    style = SkipiTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ProxyHomeStatusNotice(
    currentPresentation: ProxyHomeStatusPresentation?,
    onDismiss: () -> Unit,
) {
    var retainedPresentation by remember { mutableStateOf(currentPresentation) }
    SideEffect {
        if (currentPresentation != null) retainedPresentation = currentPresentation
    }
    val displayedPresentation = currentPresentation ?: retainedPresentation

    AnimatedVisibility(
        visible = currentPresentation != null,
        modifier = Modifier.fillMaxWidth(),
        enter = fadeIn(animationSpec = tween(200)) +
            expandVertically(expandFrom = Alignment.Top, animationSpec = tween(200)),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) +
            fadeOut(animationSpec = tween(160)),
        label = "home_status_notice_visibility",
    ) {
        displayedPresentation?.let { presentation ->
            AnimatedContent(
                targetState = presentation,
                transitionSpec = {
                    fadeIn(animationSpec = tween(130)) togetherWith
                        fadeOut(animationSpec = tween(110))
                },
                label = "home_status_notice_content",
            ) { targetPresentation ->
                Box(Modifier.fillMaxWidth().padding(bottom = SkipiTheme.spacing.small)) {
                    StatusBanner(
                        message = targetPresentation.message,
                        isError = targetPresentation.isError,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

private data class ProxyHomeStatusPresentation(
    val message: String,
    val isError: Boolean,
)

@Composable
private fun StatusBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
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
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.common_close), tint = SkipiTheme.colorScheme.onErrorContainer)
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SkipiTheme.spacing.small, vertical = SkipiTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = SkipiTheme.colors.onSurfaceVariant,
                style = SkipiTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.common_close), tint = SkipiTheme.colors.onSurfaceVariant)
            }
        }
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

private fun canUseHomeAction(
    state: ProxyHomeUiState,
    actionId: ProxyHomeActionId,
    allowBusy: Boolean = false,
): Boolean = canDispatchHomeAction(
    actionId = actionId,
    availableActions = state.availableActions,
    busyActions = state.busyActions,
    allowBusy = allowBusy,
)

private fun dispatchHomeIntent(
    state: ProxyHomeUiState,
    action: ProxyHomeAction,
    onAction: (ProxyHomeAction) -> Unit,
) {
    val actionId = action.requiredActionId()
    if (actionId == null) {
        onAction(action)
        return
    }
    if (!canUseHomeAction(state, actionId, allowBusy = actionId == ProxyHomeActionId.ToggleTunnel)) return
    if (action is ProxyHomeAction.ToggleTunnel && !state.canToggleTunnel) return
    val server = when (action) {
        is ProxyHomeAction.TestServer,
        is ProxyHomeAction.CopyServer,
        is ProxyHomeAction.OpenStrategyMemberPicker,
        is ProxyHomeAction.SelectStrategyMember,
        is ProxyHomeAction.MoveServer,
        is ProxyHomeAction.EditServer,
        is ProxyHomeAction.DeleteServer,
        is ProxyHomeAction.ShowServerQr -> state.servers.firstOrNull { it.id == action.serverIdOrNull() }
            ?: state.selectedServer?.takeIf { it.id == action.serverIdOrNull() }
        else -> null
    }
    val variantAvailable = when (action) {
        is ProxyHomeAction.AddServer -> action.kind in state.availableServerKinds
        is ProxyHomeAction.ImportServers -> action.source in state.availableImportSources
        is ProxyHomeAction.CopyServer -> action.format in state.availableCopyFormats && action.format in (server?.availableCopyFormats.orEmpty())
        is ProxyHomeAction.RunServerTool -> action.tool in state.availableServerTools
        is ProxyHomeAction.OpenStrategyMemberPicker,
        is ProxyHomeAction.SelectStrategyMember -> server?.isStrategyGroup == true
        is ProxyHomeAction.TestServer -> server?.canTest == true
        is ProxyHomeAction.MoveServer -> state.sortMode == ProxyHomeSortMode.Default && state.searchQuery.isBlank()
        is ProxyHomeAction.TestGroup -> state.groups.any { it.id == action.groupId && it.enabled && it.serverCount > 0 }
        is ProxyHomeAction.EditGroup -> action.groupId == null || state.groups.any { it.id == action.groupId && it.canEdit }
        is ProxyHomeAction.DeleteGroup -> state.groups.any { it.id == action.groupId && it.canDelete }
        is ProxyHomeAction.MoveGroup -> state.groups.any { it.id == action.groupId && it.canMove }
        is ProxyHomeAction.OpenExternalLink -> action.url.isNotBlank()
        else -> true
    }
    if (variantAvailable) onAction(action)
}

private fun ProxyHomeAction.serverIdOrNull(): String? = when (this) {
    is ProxyHomeAction.TestServer -> serverId
    is ProxyHomeAction.CopyServer -> id
    is ProxyHomeAction.OpenStrategyMemberPicker -> serverId
    is ProxyHomeAction.SelectStrategyMember -> serverId
    is ProxyHomeAction.MoveServer -> serverId
    is ProxyHomeAction.EditServer -> id
    is ProxyHomeAction.DeleteServer -> id
    is ProxyHomeAction.ShowServerQr -> id
    else -> null
}

@Composable
private fun serverKindTitle(kind: ProxyHomeServerKind): String = stringResource(
    when (kind) {
        ProxyHomeServerKind.Http -> Res.string.proxy_server_list_add_http
        ProxyHomeServerKind.Vmess -> Res.string.proxy_server_list_add_vmess
        ProxyHomeServerKind.Vless -> Res.string.proxy_server_list_add_vless
        ProxyHomeServerKind.Trojan -> Res.string.proxy_server_list_add_trojan
        ProxyHomeServerKind.Shadowsocks -> Res.string.proxy_server_list_add_shadowsocks
        ProxyHomeServerKind.Socks -> Res.string.proxy_server_list_add_socks
        ProxyHomeServerKind.Hysteria2 -> Res.string.proxy_server_list_add_hysteria2
        ProxyHomeServerKind.Wireguard -> Res.string.proxy_server_list_add_wireguard
        ProxyHomeServerKind.AmneziaWg -> Res.string.proxy_server_list_add_amnezia_wg
        ProxyHomeServerKind.OlcRtc -> Res.string.proxy_server_list_add_olcrtc
        ProxyHomeServerKind.StrategyGroup -> Res.string.proxy_server_list_add_strategy_group
        ProxyHomeServerKind.ChainProxy -> Res.string.proxy_server_list_add_chain_proxy
        ProxyHomeServerKind.Custom -> Res.string.proxy_server_list_add_custom
    },
)

@Composable
private fun importSourceTitle(source: ProxyHomeImportSource): String = stringResource(
    when (source) {
        ProxyHomeImportSource.ManualInput -> Res.string.proxy_server_list_manual_input
        ProxyHomeImportSource.QrCode -> Res.string.proxy_server_list_scan_qr_code
        ProxyHomeImportSource.Clipboard -> Res.string.proxy_server_list_import_clipboard
        ProxyHomeImportSource.File -> Res.string.proxy_server_list_import_file
    },
)

@Composable
private fun copyFormatTitle(format: ProxyHomeCopyFormat): String = stringResource(
    when (format) {
        ProxyHomeCopyFormat.Url -> Res.string.proxy_server_copy_url
        ProxyHomeCopyFormat.FullJson -> Res.string.proxy_server_copy_full_json
        ProxyHomeCopyFormat.QrCode -> Res.string.proxy_server_copy_qr_code
    },
)

@Composable
private fun sortModeTitle(mode: ProxyHomeSortMode): String = stringResource(
    when (mode) {
        ProxyHomeSortMode.Default -> Res.string.proxy_server_list_option_sort_default
        ProxyHomeSortMode.Name -> Res.string.proxy_server_list_option_sort_name
        ProxyHomeSortMode.Latency -> Res.string.proxy_server_list_option_sort_latency
    },
)

@Composable
private fun serverToolTitle(tool: ProxyHomeServerTool): String = stringResource(
    when (tool) {
        ProxyHomeServerTool.RestartService -> Res.string.proxy_server_list_restart_service
        ProxyHomeServerTool.UpdateSubscriptions -> Res.string.proxy_server_list_update_subscriptions
        ProxyHomeServerTool.DeleteDuplicateServers -> Res.string.proxy_server_list_delete_duplicates
        ProxyHomeServerTool.DeleteInvalidServers -> Res.string.proxy_server_list_delete_invalid
        ProxyHomeServerTool.DeleteAllServers -> Res.string.proxy_server_list_delete_all
    },
)
