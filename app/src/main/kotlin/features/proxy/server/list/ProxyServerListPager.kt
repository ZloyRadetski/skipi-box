// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalScrollBarApi::class)

package features.proxy.server.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.modes.ProxyServerListSortDefault
import app.AppState
import app.ProxyServerState
import app.isTestingLatency
import app.R
import app.collectProxyServerLatency
import app.proxyServerIdFromOutboundTag
import app.navigation.Navigator
import app.navigation.Route
import app.navigation.TrafficConfigEditorSection
import data.AndroidAppStateStore
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.UrlProxyServer
import features.proxy.server.validation.rememberProxyServerValidationMessageResolver
import features.proxy.server.usecase.ProxyServerCopyTextResult
import features.proxy.server.usecase.ProxyServerCopyTextType
import features.proxy.server.usecase.proxyServerCopyText
import features.subscription.SubscriptionProviderBody
import features.subscription.SubscriptionProviderHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import ui.components.AppPullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import ui.clipboard.setPlainText
import ui.components.longPressReorderDragHandle
import ui.components.moveItem
import ui.components.rememberSkipiReorderableLazyGridState
import ui.components.rememberReorderableScrollThresholdPadding
import ui.feedback.AndroidToastTipNotifier
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
internal fun ProxyServerListPager(
    groupPagerState: PagerState,
    scrollToTopRequest: Int,
    groupState: ProxyServerListGroups,
    searchValue: String,
    servers: List<ProxyServerState>,
    selectedServerId: Int,
    columns: Int,
    sort: Int,
    unknownGroupName: String,
    itemTextFormatter: ProxyServerListItemTextFormatter,
    topAppBarScrollBehavior: ScrollBehavior,
    listPadding: PaddingValues,
    dragScrollThresholdBottomPadding: Dp,
    contentPadding: PaddingValues,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    navigator: Navigator,
    clipboard: Clipboard,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    resultKey: String,
    onSelectedServerIdChange: (Int) -> Unit,
    onDeleteServer: (ProxyServerState) -> Unit,
    onUpdateSubscription: suspend (Int) -> Unit,
    onUpdateAllSubscriptions: suspend () -> Unit,
    onPingSubscription: (Int) -> Unit,
    onEditSubscription: (Int) -> Unit,
    onOpenSelectGroupMember: (ProxyServerState) -> Unit = {},
    pingingGroupIds: Set<Int> = emptySet(),
    activeOutboundTag: String? = null,
    activeTrafficConfigId: Int? = null,
    pageHeader: (@Composable (Modifier) -> Unit)? = null,
) {
    val context = LocalContext.current
    var qrCodeDialogState by remember { mutableStateOf<ProxyServerQrCodeDialogState?>(null) }
    var allSubscriptionsRefreshing by rememberSaveable { mutableStateOf(false) }
    var updatingGroupIds by remember { mutableStateOf(emptySet<Int>()) }

    val handleUpdateSubscription: (Int) -> Unit = { groupId ->
        if (!updatingGroupIds.contains(groupId) && !allSubscriptionsRefreshing) {
            scope.launch {
                updatingGroupIds = updatingGroupIds + groupId
                try {
                    onUpdateSubscription(groupId)
                } finally {
                    updatingGroupIds = updatingGroupIds - groupId
                }
            }
        }
    }

    qrCodeDialogState?.let { state ->
        ProxyServerQrCodeDialog(
            title = state.title,
            text = state.text,
            onDismissRequest = { qrCodeDialogState = null },
        )
    }

    AppPullToRefresh(
        isRefreshing = allSubscriptionsRefreshing,
        onRefresh = {
            if (allSubscriptionsRefreshing) return@AppPullToRefresh
            scope.launch {
                allSubscriptionsRefreshing = true
                withFrameNanos { }
                try {
                    onUpdateAllSubscriptions()
                } finally {
                    withFrameNanos { }
                    allSubscriptionsRefreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(0.dp),
        topAppBarScrollBehavior = null,
        color = AppTheme.colors.onSurface.copy(alpha = 0.8f),
        circleSize = 24.dp,
        refreshTexts = listOf(
            stringResource(R.string.subscriptions_pull_to_refresh),
            stringResource(R.string.subscriptions_release_to_refresh),
            stringResource(R.string.subscriptions_refreshing),
            stringResource(R.string.subscriptions_refreshed),
        ),
    ) {
        HorizontalPager(
        state = groupPagerState,
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
        beyondViewportPageCount = 0,
    ) {
        val pageGroupId = groupState.groupTabs.getOrNull(it)?.id ?: groupState.selectedTabId
        val pageIsAllGroupsSelected = pageGroupId == AllProxyGroupId
        val pageSubscriptionGroup = groupState.visibleGroups
            .firstOrNull { group -> group.id == pageGroupId }
            ?.takeUnless { group -> group.id == AutoBalancerGroupId || group.id == features.subscription.DefaultSubscriptionGroupId }
        val keyword = searchValue.trim()
        val pageServers = remember(
            servers,
            pageGroupId,
            pageIsAllGroupsSelected,
            groupState.visibleGroupIds,
            keyword,
            sort,
            activeTrafficConfigId,
        ) {
            servers.filterPageServers(
                pageGroupId = pageGroupId,
                pageIsAllGroupsSelected = pageIsAllGroupsSelected,
                visibleGroupIds = groupState.visibleGroupIds,
                keyword = keyword,
                activeTrafficConfigId = activeTrafficConfigId,
            ).sortedForProxyServerList(sort)
        }
        val reorderEnabled = columns == 1 && sort == ProxyServerListSortDefault

        Box(Modifier.fillMaxSize()) {
            ProxyServerLazyGrid(
                pageServers = pageServers,
                servers = servers,
                selectedServerId = selectedServerId,
                columns = columns,
                reorderEnabled = reorderEnabled,
                pageIsAllGroupsSelected = pageIsAllGroupsSelected,
                pageGroupId = pageGroupId,
                unknownGroupName = unknownGroupName,
                itemTextFormatter = itemTextFormatter,
                groupState = groupState,
                scrollToTopRequest = scrollToTopRequest,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                listPadding = listPadding,
                dragScrollThresholdBottomPadding = dragScrollThresholdBottomPadding,
                contentPadding = contentPadding,
                stateStore = stateStore,
                updateAppState = updateAppState,
                navigator = navigator,
                clipboard = clipboard,
                context = context,
                tipNotifier = tipNotifier,
                scope = scope,
                messages = messages,
                resultKey = resultKey,
                onSelectedServerIdChange = onSelectedServerIdChange,
                onDeleteServer = onDeleteServer,
                subscriptionGroup = pageSubscriptionGroup,
                onUpdateSubscription = handleUpdateSubscription,
                onPingSubscription = onPingSubscription,
                onEditSubscription = onEditSubscription,
                onOpenSelectGroupMember = onOpenSelectGroupMember,
                allSubscriptionsRefreshing = allSubscriptionsRefreshing,
                updatingGroupIds = updatingGroupIds,
                pingingGroupIds = pingingGroupIds,
                activeOutboundTag = activeOutboundTag,
                pageHeader = pageHeader,
                onShowQrCode = { title, text ->
                    qrCodeDialogState = ProxyServerQrCodeDialogState(title, text)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        }
    }
}

@Composable
private fun SubscriptionProxyServerList(
    group: app.SubscriptionGroupState,
    pageServers: List<ProxyServerState>,
    servers: List<ProxyServerState>,
    selectedServerId: Int,
    pageIsAllGroupsSelected: Boolean,
    pageGroupId: Int,
    unknownGroupName: String,
    itemTextFormatter: ProxyServerListItemTextFormatter,
    groupState: ProxyServerListGroups,
    scrollToTopRequest: Int,
    topAppBarScrollBehavior: ScrollBehavior,
    listPadding: PaddingValues,
    contentPadding: PaddingValues,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    navigator: Navigator,
    clipboard: Clipboard,
    context: android.content.Context,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    resultKey: String,
    onSelectedServerIdChange: (Int) -> Unit,
    onDeleteServer: (ProxyServerState) -> Unit,
    onShowQrCode: (title: String, text: String) -> Unit,
    onUpdateSubscription: (Int) -> Unit,
    onPingSubscription: (Int) -> Unit,
    onEditSubscription: (Int) -> Unit,
    onOpenSelectGroupMember: (ProxyServerState) -> Unit = {},
    allSubscriptionsRefreshing: Boolean = false,
    updatingGroupIds: Set<Int> = emptySet(),
    pingingGroupIds: Set<Int> = emptySet(),
    activeOutboundTag: String? = null,
    pageHeader: (@Composable (Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    val layoutDirection = LocalLayoutDirection.current
    var expanded by rememberSaveable(group.id) { mutableStateOf(true) }
    val serverCount = remember(servers, group.id) { servers.count { server -> server.groupId == group.id } }
    val providerBorderColor = AppTheme.colors.onSurface.copy(alpha = 0.14f)
    val dividerColor = AppTheme.colors.onSurface.copy(alpha = 0.08f)
    val providerColor = AppTheme.colors.surface
    val providerCornerRadius = 18.dp
    val headerShape = if (expanded) {
        RoundedCornerShape(topStart = providerCornerRadius, topEnd = providerCornerRadius)
    } else {
        RoundedCornerShape(providerCornerRadius)
    }
    val lastServerId = pageServers.lastOrNull()?.id
    val listContentPadding = PaddingValues(
        start = listPadding.calculateStartPadding(layoutDirection) + 12.dp,
        top = 6.dp,
        end = listPadding.calculateEndPadding(layoutDirection) + 12.dp,
        bottom = listPadding.calculateBottomPadding(),
    )

    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .fillMaxHeight(),
            contentPadding = listContentPadding,
        ) {
            pageHeader?.let { header ->
                item(key = "page_header") {
                    header(Modifier)
                }
            }
            item(key = "servers_section_title_${group.id}") {
                SmallTitle(
                    text = stringResource(R.string.home_section_servers),
                )
            }
            item(key = "subscription_provider_${group.id}") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(headerShape)
                        .background(providerColor)
                        .border(width = 1.dp, color = providerBorderColor, shape = headerShape),
                ) {
                    SubscriptionProviderHeader(
                        group = group,
                        serverCount = serverCount,
                        expanded = expanded,
                        onExpandedChange = { value -> expanded = value },
                        onUpdate = { onUpdateSubscription(group.id) },
                        onPing = { onPingSubscription(group.id) },
                        onEdit = { onEditSubscription(group.id) },
                        isUpdating = allSubscriptionsRefreshing || updatingGroupIds.contains(group.id),
                        isPinging = pingingGroupIds.contains(group.id) || servers.any { it.groupId == group.id && it.isTestingLatency },
                    )
                }
            }
            if (expanded && pageServers.isEmpty()) {
                item(key = "subscription_empty_${group.id}") {
                    SubscriptionProviderBody(
                        color = providerColor,
                        borderColor = providerBorderColor,
                        isLastItem = true,
                        bottomCornerRadius = providerCornerRadius,
                    ) {
                        ProxyServerListEmptyState(text = stringResource(R.string.common_empty))
                    }
                }
            }
            if (expanded) {
                lazyItems(
                    items = pageServers,
                    key = { server -> server.id },
                    contentType = { "subscription_server" },
                ) { server ->
                    val isLast = server.id == lastServerId
                    SubscriptionProviderBody(
                        color = providerColor,
                        borderColor = providerBorderColor,
                        isLastItem = isLast,
                        bottomCornerRadius = providerCornerRadius,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ProxyServerListItem(
                                server = server,
                                servers = servers,
                                selectedServerId = selectedServerId,
                                pageIsAllGroupsSelected = pageIsAllGroupsSelected,
                                pageGroupId = pageGroupId,
                                unknownGroupName = unknownGroupName,
                                itemTextFormatter = itemTextFormatter,
                                groupState = groupState,
                                stateStore = stateStore,
                                updateAppState = updateAppState,
                                navigator = navigator,
                                clipboard = clipboard,
                                context = context,
                                tipNotifier = tipNotifier,
                                scope = scope,
                                messages = messages,
                                resultKey = resultKey,
                                onSelectedServerIdChange = onSelectedServerIdChange,
                                onDeleteServer = onDeleteServer,
                                onShowQrCode = onShowQrCode,
                                onOpenSelectGroupMember = onOpenSelectGroupMember,
                                compact = true,
                                inSubscriptionGroup = true,
                                isDragging = false,
                                dragModifier = Modifier,
                                activeOutboundTag = activeOutboundTag,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp),
                            )
                            if (!isLast) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .height(0.8.dp)
                                        .background(dividerColor),
                                )
                            }
                        }
                    }
                }
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }
}


@Composable
private fun ProxyServerLazyGrid(
    pageServers: List<ProxyServerState>,
    servers: List<ProxyServerState>,
    selectedServerId: Int,
    columns: Int,
    reorderEnabled: Boolean,
    pageIsAllGroupsSelected: Boolean,
    pageGroupId: Int,
    unknownGroupName: String,
    itemTextFormatter: ProxyServerListItemTextFormatter,
    groupState: ProxyServerListGroups,
    scrollToTopRequest: Int,
    topAppBarScrollBehavior: ScrollBehavior,
    listPadding: PaddingValues,
    dragScrollThresholdBottomPadding: Dp,
    contentPadding: PaddingValues,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    navigator: Navigator,
    clipboard: Clipboard,
    context: android.content.Context,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    resultKey: String,
    onSelectedServerIdChange: (Int) -> Unit,
    onDeleteServer: (ProxyServerState) -> Unit,
    onShowQrCode: (title: String, text: String) -> Unit,
    subscriptionGroup: app.SubscriptionGroupState?,
    onUpdateSubscription: (Int) -> Unit,
    onPingSubscription: (Int) -> Unit,
    onEditSubscription: (Int) -> Unit,
    onOpenSelectGroupMember: (ProxyServerState) -> Unit = {},
    allSubscriptionsRefreshing: Boolean = false,
    updatingGroupIds: Set<Int> = emptySet(),
    pingingGroupIds: Set<Int> = emptySet(),
    activeOutboundTag: String? = null,
    pageHeader: (@Composable (Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    subscriptionGroup?.let { group ->
        SubscriptionProxyServerList(
            group = group,
            pageServers = pageServers,
            servers = servers,
            selectedServerId = selectedServerId,
            pageIsAllGroupsSelected = pageIsAllGroupsSelected,
            pageGroupId = pageGroupId,
            unknownGroupName = unknownGroupName,
            itemTextFormatter = itemTextFormatter,
            groupState = groupState,
            scrollToTopRequest = scrollToTopRequest,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            listPadding = listPadding,
            contentPadding = contentPadding,
            stateStore = stateStore,
            updateAppState = updateAppState,
            navigator = navigator,
            clipboard = clipboard,
            context = context,
            tipNotifier = tipNotifier,
            scope = scope,
            messages = messages,
            resultKey = resultKey,
            onSelectedServerIdChange = onSelectedServerIdChange,
            onDeleteServer = onDeleteServer,
            onShowQrCode = onShowQrCode,
            onUpdateSubscription = onUpdateSubscription,
            onPingSubscription = onPingSubscription,
            onEditSubscription = onEditSubscription,
            allSubscriptionsRefreshing = allSubscriptionsRefreshing,
            updatingGroupIds = updatingGroupIds,
            pingingGroupIds = pingingGroupIds,
            activeOutboundTag = activeOutboundTag,
            pageHeader = pageHeader,
            modifier = modifier,
        )
        return
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) gridState.animateScrollToItem(0)
    }
    val layoutDirection = LocalLayoutDirection.current
    val compact = columns > 1
    val subscriptionGroupEnabled = subscriptionGroup != null
    var subscriptionExpanded by rememberSaveable(subscriptionGroup?.id) { mutableStateOf(true) }
    val subscriptionCollapsed = subscriptionGroupEnabled && !subscriptionExpanded
    val gridHorizontalExtra = if (compact) ProxyServerListGridSpacing else 0.dp
    val gridItemSpacing = if (compact) ProxyServerListGridSpacing else 0.dp
    val gridContentPadding = PaddingValues(
        start = if (subscriptionGroupEnabled) 0.dp else {
            listPadding.calculateStartPadding(layoutDirection) + gridHorizontalExtra
        },
        top = if (subscriptionGroupEnabled) 0.dp else 6.dp,
        end = if (subscriptionGroupEnabled) 0.dp else {
            listPadding.calculateEndPadding(layoutDirection) + gridHorizontalExtra
        },
        bottom = if (subscriptionGroupEnabled) 0.dp else listPadding.calculateBottomPadding(),
    )
    val reorderableLazyGridState = rememberSkipiReorderableLazyGridState(
        lazyGridState = gridState,
        itemCount = if (subscriptionExpanded) pageServers.size else 0,
        scrollThresholdPadding = rememberReorderableScrollThresholdPadding(
            bottom = dragScrollThresholdBottomPadding,
        ),
    ) { fromIndex, toIndex ->
        if (!reorderEnabled) return@rememberSkipiReorderableLazyGridState
        updateAppState { state ->
            val reorderedServers = state.proxyServers.reorderVisibleServer(
                pageServers = pageServers,
                fromIndex = fromIndex,
                toIndex = toIndex,
            )
            if (reorderedServers === state.proxyServers) {
                state
            } else {
                state.copy(proxyServers = reorderedServers)
            }
        }
    }

    @Composable
    fun ServerGrid() {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .fillMaxHeight(),
            contentPadding = gridContentPadding,
            verticalArrangement = Arrangement.spacedBy(gridItemSpacing),
            horizontalArrangement = Arrangement.spacedBy(gridItemSpacing),
        ) {
            pageHeader?.let { header ->
                item(
                    key = "page_header",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "page_header",
                ) {
                    header(Modifier.padding(horizontal = 12.dp))
                }
            }
            subscriptionGroup?.let { group ->
                item(
                    key = "servers_section_title_${group.id}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "section_title",
                ) {
                    SmallTitle(
                        text = stringResource(R.string.home_section_servers),
                    )
                }
                item(
                    key = "subscription_provider_${group.id}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "subscription_provider",
                ) {
                    SubscriptionProviderHeader(
                        group = group,
                        serverCount = servers.count { server -> server.groupId == group.id },
                        expanded = subscriptionExpanded,
                        onExpandedChange = { expanded -> subscriptionExpanded = expanded },
                        onUpdate = { onUpdateSubscription(group.id) },
                        onPing = { onPingSubscription(group.id) },
                        onEdit = { onEditSubscription(group.id) },
                        isUpdating = allSubscriptionsRefreshing || updatingGroupIds.contains(group.id),
                        isPinging = pingingGroupIds.contains(group.id) || servers.any { it.groupId == group.id && it.isTestingLatency },
                    )
                }
            }
            if (subscriptionExpanded && pageServers.isEmpty()) {
                item(
                    key = "proxy_empty",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "empty",
                ) {
                    ProxyServerListEmptyState(text = stringResource(R.string.common_empty))
                }
            } else if (subscriptionExpanded) {
                items(
                    items = pageServers,
                    key = { server -> server.id },
                    contentType = { "proxy_server" },
                ) { server ->
                    ReorderableItem(
                        state = reorderableLazyGridState.reorderableState,
                        key = server.id,
                        enabled = reorderEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        animateItemModifier = Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = folmeSpring(damping = 0.9f, response = 0.38f),
                        ),
                    ) { isDragging ->
                        ProxyServerListItem(
                            server = server,
                            servers = servers,
                            selectedServerId = selectedServerId,
                            pageIsAllGroupsSelected = pageIsAllGroupsSelected,
                            pageGroupId = pageGroupId,
                            unknownGroupName = unknownGroupName,
                            itemTextFormatter = itemTextFormatter,
                            groupState = groupState,
                            stateStore = stateStore,
                            updateAppState = updateAppState,
                            navigator = navigator,
                            clipboard = clipboard,
                            context = context,
                            tipNotifier = tipNotifier,
                            scope = scope,
                            messages = messages,
                            resultKey = resultKey,
                            onSelectedServerIdChange = onSelectedServerIdChange,
                            onDeleteServer = onDeleteServer,
                            onShowQrCode = onShowQrCode,
                            onOpenSelectGroupMember = onOpenSelectGroupMember,
                            compact = compact || subscriptionGroupEnabled,
                            inSubscriptionGroup = subscriptionGroupEnabled,
                            isDragging = isDragging && reorderEnabled,
                            dragModifier = Modifier.longPressReorderDragHandle(
                                scope = this,
                                enabled = reorderEnabled && pageServers.size > 1,
                                state = reorderableLazyGridState,
                            ),
                            activeOutboundTag = activeOutboundTag,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    Box(modifier) {
        if (subscriptionGroupEnabled) {
            val providerShape = RoundedCornerShape(18.dp)
            val providerBorderColor = AppTheme.colors.onSurface.copy(alpha = 0.14f)
            Card(
                modifier = Modifier
                    .then(
                        if (subscriptionCollapsed) Modifier.wrapContentHeight() else Modifier.fillMaxSize(),
                    )
                    .padding(
                        start = listPadding.calculateStartPadding(layoutDirection),
                        top = listPadding.calculateTopPadding(),
                        end = listPadding.calculateEndPadding(layoutDirection),
                        bottom = listPadding.calculateBottomPadding(),
                    )
                    .clip(providerShape)
                    .border(width = 1.dp, color = providerBorderColor, shape = providerShape),
                colors = CardDefaults.defaultColors(
                    color = AppTheme.colors.surface,
                ),
                insideMargin = PaddingValues(0.dp),
            ) {
                ServerGrid()
            }
        } else {
            ServerGrid()
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(gridState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }
}

@Composable
private fun ProxyServerListItem(
    server: ProxyServerState,
    servers: List<ProxyServerState>,
    selectedServerId: Int,
    pageIsAllGroupsSelected: Boolean,
    pageGroupId: Int,
    unknownGroupName: String,
    itemTextFormatter: ProxyServerListItemTextFormatter,
    groupState: ProxyServerListGroups,
    stateStore: AndroidAppStateStore,
    updateAppState: ((AppState) -> AppState) -> Unit,
    navigator: Navigator,
    clipboard: Clipboard,
    context: android.content.Context,
    tipNotifier: AndroidToastTipNotifier,
    scope: CoroutineScope,
    messages: ProxyServerListMessages,
    resultKey: String,
    onSelectedServerIdChange: (Int) -> Unit,
    onDeleteServer: (ProxyServerState) -> Unit,
    onShowQrCode: (title: String, text: String) -> Unit,
    onOpenSelectGroupMember: (ProxyServerState) -> Unit = {},
    compact: Boolean,
    inSubscriptionGroup: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    dragModifier: Modifier,
    activeOutboundTag: String? = null,
) {
    val latency = stateStore.collectProxyServerLatency(
        serverId = server.id,
        initialLatency = server.latency,
    ).value
    val validationMessageOf = rememberProxyServerValidationMessageResolver()
    val displayText = remember(itemTextFormatter, server, servers) {
        itemTextFormatter.displayOf(server, servers)
    }
    val copyActions = remember(server.server) {
        if (server.server is UrlProxyServer<*>) {
            listOf(
                ProxyServerListCopyAction.QrCode,
                ProxyServerListCopyAction.Url,
                ProxyServerListCopyAction.FullJson,
            )
        } else {
            listOf(ProxyServerListCopyAction.FullJson)
        }
    }
    val isStrategyGroup = server.server is StrategyGroup
    val activeMemberFlag = remember(server.server, activeOutboundTag, servers, selectedServerId, server.id) {
        val strategyGroup = server.server as? StrategyGroup
        if (strategyGroup != null) {
            val targetMemberId = if (strategyGroup.strategy == StrategyGroupConstants.TYPE_SELECT) {
                strategyGroup.selectedMemberId ?: strategyGroup.proxyServerIds.firstOrNull()
            } else if (server.id == selectedServerId) {
                activeOutboundTag?.proxyServerIdFromOutboundTag()?.takeIf {
                    CountryFlagUtils.strategyGroupContainsMember(strategyGroup, it, servers)
                }
            } else {
                null
            }
            if (targetMemberId != null) {
                val activeMember = servers.firstOrNull { it.id == targetMemberId }
                activeMember?.let { CountryFlagUtils.extractLeadingCountryFlag(it.server.getInfo().remarks) }
            } else {
                null
            }
        } else {
            null
        }
    }

    ProxyServerListItemCard(
        latency = latency,
        displayText = displayText,
        selected = selectedServerId == server.id,
        modifier = modifier,
        groupName = if (pageIsAllGroupsSelected) {
            groupState.groupNames[server.groupId] ?: unknownGroupName
        } else {
            null
        },
        compact = compact,
        inSubscriptionGroup = inSubscriptionGroup,
        isDragging = isDragging,
        dragModifier = dragModifier,
        isStrategyGroup = isStrategyGroup,
        activeMemberFlag = activeMemberFlag,
        onSelect = {
            onSelectedServerIdChange(server.id)
            updateAppState { state ->
                if (state.selectedProxyServerId == server.id) {
                    state
                } else {
                    state.copy(selectedProxyServerId = server.id)
                }
            }
            val strategyGroup = server.server as? StrategyGroup
            if (strategyGroup?.strategy == StrategyGroupConstants.TYPE_SELECT) {
                onOpenSelectGroupMember(server)
            }
        },
        copyActions = copyActions,
        onCopyAction = { action ->
            scope.launch {
                val basicIssues = server.server.validateBasic()
                if (basicIssues.isNotEmpty()) {
                    tipNotifier.show(validationMessageOf(basicIssues.first()))
                    return@launch
                }
                val copyTextType = when (action) {
                    ProxyServerListCopyAction.QrCode,
                    ProxyServerListCopyAction.Url -> ProxyServerCopyTextType.Url

                    ProxyServerListCopyAction.FullJson -> ProxyServerCopyTextType.FullJson
                }
                when (
                    val result = server.proxyServerCopyText(
                        context = context,
                        appState = stateStore.state.value,
                        type = copyTextType,
                    )
                ) {
                    is ProxyServerCopyTextResult.Success -> {
                        if (action == ProxyServerListCopyAction.QrCode) {
                            onShowQrCode(displayText.title, result.text)
                        } else {
                            clipboard.setPlainText(result.text)
                            tipNotifier.show(messages.copied)
                        }
                    }

                    ProxyServerCopyTextResult.Unsupported -> {
                        tipNotifier.show(messages.unsupported)
                    }

                    ProxyServerCopyTextResult.InvalidConfig -> {
                        tipNotifier.show(messages.configInvalid)
                    }
                }
            }
        },
        onEdit = {
            val sourceTrafficConfigId = (server.server as? StrategyGroup)?.sourceTrafficConfigId
            if (sourceTrafficConfigId != null) {
                navigator.push(
                    Route.TrafficConfigSection(
                        trafficConfigId = sourceTrafficConfigId,
                        section = TrafficConfigEditorSection.ProxyGroups,
                    ),
                )
            } else {
                navigator.navigateForResult(
                    route = Route.ProxyServerEditor(
                        ps = server.server,
                        serverId = server.id,
                        groupId = server.groupId,
                        returnGroupId = pageGroupId,
                        resultKey = resultKey,
                    ),
                    requestKey = resultKey,
                )
            }
        },
        onDelete = {
            onDeleteServer(server)
        },
    )
}

private data class ProxyServerQrCodeDialogState(
    val title: String,
    val text: String,
)

private fun List<ProxyServerState>.filterPageServers(
    pageGroupId: Int,
    pageIsAllGroupsSelected: Boolean,
    visibleGroupIds: Set<Int>,
    keyword: String,
    activeTrafficConfigId: Int? = null,
): List<ProxyServerState> {
    return filter { server ->
        val groupMatches = if (pageIsAllGroupsSelected) {
            server.groupId in visibleGroupIds
        } else {
            server.groupId == pageGroupId
        }
        groupMatches && server.isVisibleOnProxyServerList(activeTrafficConfigId) && (
            keyword.isEmpty() ||
                server.server.getInfo().remarks.contains(keyword, ignoreCase = true)
            )
    }
}

private fun List<ProxyServerState>.reorderVisibleServer(
    pageServers: List<ProxyServerState>,
    fromIndex: Int,
    toIndex: Int,
): List<ProxyServerState> {
    val serverId = pageServers.getOrNull(fromIndex)?.id ?: return this
    val targetServerId = pageServers.getOrNull(toIndex)?.id ?: return this

    return moveItem(
        fromIndex = indexOfFirst { server -> server.id == serverId },
        toIndex = indexOfFirst { server -> server.id == targetServerId },
    )
}

private val ProxyServerListGridSpacing = 12.dp
