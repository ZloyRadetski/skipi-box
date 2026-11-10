// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import app.skipi.ui.navigation.OnNavigationReselect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeGroupKind
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomePageUiState
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyHomeUiState
import app.skipi.app.home.compactPingAction
import app.skipi.app.home.groupHeader
import app.skipi.ui.components.longPressReorderDragHandle
import app.skipi.ui.components.rememberReorderableGridStateByKey
import app.skipi.ui.components.rememberReorderableScrollThresholdPadding
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_more
import app.skipi.ui.resources.home_action_add_server
import app.skipi.ui.resources.home_action_add_subscription
import app.skipi.ui.resources.home_action_refresh_subscription
import app.skipi.ui.resources.home_action_refreshing
import app.skipi.ui.resources.home_action_add_subscription
import app.skipi.ui.resources.home_empty_search
import app.skipi.ui.resources.home_empty_servers
import app.skipi.ui.resources.home_search_servers
import app.skipi.ui.resources.proxy_server_list_option_sort
import app.skipi.ui.resources.proxy_server_list_update_subscriptions
import app.skipi.ui.resources.subscriptions_pull_to_refresh
import app.skipi.ui.resources.subscriptions_release_to_refresh
import app.skipi.ui.resources.subscriptions_refreshing
import app.skipi.ui.resources.subscriptions_refreshed
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.SkipiWindowClass
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem

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
    wideSidePanel: (@Composable (Modifier) -> Unit)? = null,
    leftColumnExtra: Dp = 0.dp,
    onLeftColumnExtraChange: (Dp) -> Unit = {},
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
        wideSidePanel = wideSidePanel,
        leftColumnExtra = leftColumnExtra,
        onLeftColumnExtraChange = onLeftColumnExtraChange,
    )
}

/**
 * [wideSidePanel] fills what the left column of a wide window has left under the server details.
 * It owns its state, so what it shows never recomposes the server list.
 */
@Composable
fun ProxyHomeScreenContent(
    state: ProxyHomeUiState,
    onAction: (ProxyHomeAction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    floatingNavigationBottomInset: Dp = 0.dp,
    topContentPadding: Dp? = 40.dp,
    containerColor: Color = SkipiTheme.colors.background,
    wideSidePanel: (@Composable (Modifier) -> Unit)? = null,
    /** How much wider than its base the wide Home's left column starts; the host saves what [onLeftColumnExtraChange] reports. */
    leftColumnExtra: Dp = 0.dp,
    onLeftColumnExtraChange: (Dp) -> Unit = {},
) {
    val windowClass = SkipiTheme.windowClass
    val isWide = proxyHomeLayoutMode(windowClass) == ProxyHomeLayoutMode.WideListAndDetails
    val isCompact = windowClass == SkipiWindowClass.Compact
    // One instance for the whole screen: a lambda capturing `state` is new on every emission and
    // would recompose every visible server row (each latency result, each tunnel status change).
    val sendState by rememberUpdatedState(state)
    val sendOnAction by rememberUpdatedState(onAction)
    val send: (ProxyHomeAction) -> Unit = remember {
        { action -> dispatchHomeIntent(sendState, action, sendOnAction) }
    }
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
    // Pulling the list down is a touch gesture; on Desktop a wheel or touchpad swipe up at the top
    // would only drag the list, so subscriptions refresh there through the toolbar action.
    val pullRefreshController = rememberedPullRefreshController.takeIf {
        canRefreshAllSubscriptions && SkipiTheme.visualProfile !is SkipiVisualProfile.Desktop
    }

    val maxWidth = when (windowClass) {
        SkipiWindowClass.Compact -> Modifier.fillMaxWidth()
        SkipiWindowClass.Medium,
        SkipiWindowClass.Expanded -> Modifier.widthIn(max = SkipiTheme.spacing.contentMaxWidth)
    }

    val selectedServer = state.selectedServer
    val hasTestableVisibleServers = state.servers.any { it.canTest }
    // The compact ping button measures only the selected server: its live connection while the
    // tunnel is up, otherwise the server on its own.
    val compactPing = state.compactPingAction()
    val compactPingEnabled = when (compactPing) {
        is ProxyHomeAction.TestConnection -> canUseHomeAction(state, ProxyHomeActionId.TestConnection)
        is ProxyHomeAction.TestServer -> canUseHomeAction(state, ProxyHomeActionId.TestServer)
        else -> false
    }
    // Long press on the floating ping: the whole subscription on its page, otherwise everything the page lists.
    val pagePing: ProxyHomeAction? = state.subscription
        ?.takeIf { it.serverCount > 0 && canUseHomeAction(state, ProxyHomeActionId.PingSubscription) }
        ?.let { ProxyHomeAction.PingSubscription(it.id) }
        ?: ProxyHomeAction.TestAllVisibleServers.takeIf {
            hasTestableVisibleServers && canUseHomeAction(state, ProxyHomeActionId.TestVisibleServers)
        }
    val refreshSubscriptionTitle = stringResource(Res.string.home_action_refresh_subscription)
    val refreshingSubscriptionTitle = stringResource(Res.string.home_action_refreshing)

    val addActions = proxyHomeAddMenuIds(
        canAddSubscription = canUseHomeAction(state, ProxyHomeActionId.AddSubscription),
        canImport = canUseHomeAction(state, ProxyHomeActionId.ImportServers),
        importSources = state.availableImportSources,
        canAddServer = canUseHomeAction(state, ProxyHomeActionId.AddServer) && state.availableServerKinds.isNotEmpty(),
    ).map { id ->
        when (id) {
            "add-subscription" ->
                SkipiProxyHomeHeaderAction(id, stringResource(Res.string.home_action_add_subscription))
            "add-server" -> SkipiProxyHomeHeaderAction(
                id = id,
                title = stringResource(Res.string.home_action_add_server),
                children = state.availableServerKinds.map { kind ->
                    SkipiProxyHomeHeaderAction("server:" + kind.name, serverKindTitle(kind))
                },
            )
            else -> SkipiProxyHomeHeaderAction(
                id,
                importSourceTitle(ProxyHomeImportSource.valueOf(id.substringAfter("import:"))),
            )
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
    val bottomInsetTreatment = proxyHomeBottomInsetTreatment(
        contentPadding = contentPadding,
        bottomOverlayInset = floatingNavigationBottomInset,
        listReservedBottom = if (showFloatingPower) 88.dp else SkipiTheme.spacing.large,
        layoutDirection = LocalLayoutDirection.current,
    )
    val listBottomPadding = bottomInsetTreatment.listBottomPadding
    // The server list scrolls full-bleed: it grows over the column's side padding (pages keep the padding
    // as content padding) and fades at the top and bottom instead of ending in a visible edge.
    val listEdge = SkipiTheme.spacing.screenHorizontal
    val listFade = 12.dp
    val listBottomBleed = SkipiTheme.spacing.screenVertical
    val phoneListPadding = PaddingValues(
        start = listEdge,
        top = listFade,
        end = listEdge,
        bottom = listBottomPadding + listBottomBleed,
    )
    val phoneListModifier = Modifier
        .fillMaxSize()
        .scrollBleed(horizontal = listEdge, top = listFade, bottom = listBottomBleed)
        .verticalEdgeFade(top = listFade, bottom = listFade)

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

    val latestState by rememberUpdatedState(state)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to pagerState.layoutInfo.viewportSize.width }
            .collect { (settledIndex, viewportWidth) ->
                val current = latestState
                pagerSettledGroupToSelect(
                    groupIds = current.pages.map { it.group.id },
                    settledIndex = settledIndex,
                    viewportWidth = viewportWidth,
                    selectedGroupId = current.selectedGroupId,
                )?.let { groupId -> send(ProxyHomeAction.SelectGroup(groupId)) }
            }
    }

    // While the user swipes, the title follows the page that is more than half in view. The heavy part, selecting the
    // group and recomputing the screen, still waits for the swipe to settle.
    var swipeGroupId by remember { mutableStateOf<String?>(null) }
    val programmaticScroll = remember { BooleanArray(1) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                if (!programmaticScroll[0]) {
                    val pageGroupId = latestState.pages.getOrNull(page)?.group?.id
                    swipeGroupId = pageGroupId?.takeIf { it != latestState.selectedGroupId }
                }
            }
    }
    LaunchedEffect(state.selectedGroupId) { swipeGroupId = null }

    val onSelectGroupTab: (String) -> Unit = { groupId ->
        val targetIndex = state.pages.indexOfFirst { it.group.id == groupId }
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            scope.launch {
                programmaticScroll[0] = true
                swipeGroupId = null
                try {
                    // A far target would compose every page in between; jump to it instead of animating past them.
                    if (kotlin.math.abs(targetIndex - pagerState.currentPage) > 1) {
                        pagerState.scrollToPage(targetIndex)
                    } else {
                        pagerState.animateScrollToPage(targetIndex)
                    }
                } finally {
                    programmaticScroll[0] = false
                }
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
                displayedGroupId = swipeGroupId,
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
                state = SkipiProxyHomeHeaderState(title = "SKIPI"),
                colors = SkipiProxyHomeHeaderColors(
                    text = SkipiTheme.colors.onBackground,
                    mutedText = SkipiTheme.colors.onSurfaceVariant,
                    accent = SkipiTheme.colors.accent,
                ),
                addActions = addActions,
                toolActions = toolActions,
                onAddAction = { id ->
                    when {
                        id == "add-subscription" -> send(ProxyHomeAction.AddSubscription)
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
                val wideList: @Composable (Modifier) -> Unit = { listModifier ->
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
                    Box(modifier = listModifier.fillMaxSize()) {
                        PageServerGrid(
                            page = wideSelectedPage,
                            state = state,
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
                }
                if (state.displayOptions.connectionMode == ProxyHomeConnectionMode.Legacy) {
                    // The widget from before the redesign keeps its layout: the card on top, details beside the list.
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
                        wideList(Modifier.weight(1.1f))
                        LegacyServerDetailsPane(
                            state = state,
                            server = selectedServer,
                            onTest = { s -> send(ProxyHomeAction.TestServer(s.id)) },
                            modifier = Modifier.weight(0.9f).fillMaxSize(),
                        )
                    }
                } else {
                    // The left column starts as wide as the button needs; dragging the divider widens it.
                    // Read only while measuring, so a drag relayouts the row and never recomposes the screen.
                    val leftColumnExtraState = remember { mutableStateOf(leftColumnExtra) }
                    val latestOnLeftColumnExtraChange by rememberUpdatedState(onLeftColumnExtraChange)
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val leftColumnBase = maxOf(300.dp, skipiClassicButtonOuterSize(state.displayOptions.classicButtonSize, compact = false))
                    val leftColumnMax = maxOf(leftColumnBase, this@BoxWithConstraints.maxWidth * 0.6f)
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .layout { measurable, constraints ->
                                    val width = (leftColumnBase + leftColumnExtraState.value)
                                        .coerceIn(leftColumnBase, leftColumnMax)
                                        .roundToPx()
                                    val placeable = measurable.measure(
                                        constraints.copy(minWidth = width, maxWidth = width),
                                    )
                                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                                },
                        ) {
                            val leftColumnScroll = rememberScrollState()
                            val auraBleed = skipiClassicButtonAuraBleed(state.displayOptions.classicButtonSize, compact = false)
                            Column(
                                modifier = Modifier
                                    .scrollBleed(horizontal = auraBleed, top = auraBleed)
                                    // The aura may reach over the header while it rests; scrolled content may not.
                                    .drawWithContent {
                                        clipRect(top = minOf(auraBleed.toPx(), leftColumnScroll.value.toFloat())) {
                                            this@drawWithContent.drawContent()
                                        }
                                    }
                                    .verticalScroll(leftColumnScroll)
                                    .padding(start = auraBleed, top = auraBleed, end = auraBleed),
                            ) {
                                HomeConnectionPanel(
                                    state = state,
                                    compact = false,
                                    onToggle = { send(ProxyHomeAction.ToggleTunnel) },
                                )
                                Spacer(Modifier.height(SkipiTheme.spacing.medium))
                                SelectedServerDetailsPane(
                                    state = state,
                                    server = selectedServer,
                                    onTest = { s -> send(ProxyHomeAction.TestServer(s.id)) },
                                )
                            }
                            wideSidePanel?.let { panel ->
                                Spacer(Modifier.height(SkipiTheme.spacing.medium))
                                panel(Modifier.fillMaxWidth().weight(1f))
                            }
                        }
                        SkipiPaneDivider(
                            onDrag = { delta ->
                                leftColumnExtraState.value = (leftColumnExtraState.value + delta)
                                    .coerceIn(0.dp, leftColumnMax - leftColumnBase)
                            },
                            onDragStopped = { latestOnLeftColumnExtraChange(leftColumnExtraState.value) },
                            width = SkipiTheme.spacing.large,
                        )
                        wideList(Modifier.weight(1f))
                    }
                    }
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
                            modifier = phoneListModifier,
                            contentPadding = phoneListPadding,
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
                            modifier = phoneListModifier,
                            userScrollEnabled = state.subscriptionSwipeEnabled,
                            verticalAlignment = Alignment.Top,
                            pageSpacing = SkipiTheme.spacing.medium,
                            beyondViewportPageCount = 0,
                        ) { pageIndex ->
                            val page = state.pages.getOrNull(pageIndex) ?: state.pages[0]
                            PageServerGrid(
                                page = page,
                                state = state,
                                contentPadding = phoneListPadding,
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
                            compactPingEnabled
                        },
                        showToggleAction = showFloatingPower,
                        showPingAction = showFloatingPower,
                        onToggleRunning = { send(ProxyHomeAction.ToggleTunnel) },
                        onTestLatency = {
                            if (state.isTestingLatency) send(ProxyHomeAction.CancelLatencyTests)
                            else compactPing?.let(send)
                        },
                        onTestAllLatency = pagePing?.let { action -> { send(action) } },
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
    // Manual servers, the user's proxy groups and balancers get a subscription's panel with a ping-only header.
    val groupHeader = state.groupHeader(page)
    val panelGroup = (subscriptionGroup && subscription != null) || groupHeader != null
    val panelExpanded = if (subscriptionGroup) page.subscriptionExpanded else true
    val providerSurface = SkipiTheme.colors.surface
    val providerBorder = Color.Transparent
    val providerDivider = SkipiTheme.colors.onSurface.copy(alpha = 0.08f)
    val gridState = rememberLazyGridState()
    OnNavigationReselect { gridState.animateScrollToItem(0) }
    val rowContext = remember(
        state.sortMode,
        state.searchQuery,
        state.availableActions,
        state.busyActions,
        state.availableCopyFormats,
        state.displayOptions,
    ) { state.toServerRowContext() }
    val reorderEnabled = proxyHomeCanReorderServers(
        sortMode = state.sortMode,
        searchQuery = state.searchQuery,
        visibleServerCount = pageServers.size,
    ) && canUseHomeAction(state, ProxyHomeActionId.ReorderServer)
    val reorderableState = rememberReorderableGridStateByKey(
        lazyGridState = gridState,
        hapticFeedback = LocalHapticFeedback.current,
        scrollThresholdPadding = rememberReorderableScrollThresholdPadding(
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) { fromKey, toKey ->
        if (!reorderEnabled) return@rememberReorderableGridStateByKey
        val fromServerId = pageServers.firstOrNull { server -> "server-${server.id}" == fromKey }?.id
            ?: return@rememberReorderableGridStateByKey
        val toServerId = pageServers.firstOrNull { server -> "server-${server.id}" == toKey }?.id
            ?: return@rememberReorderableGridStateByKey
        onAction(ProxyHomeAction.ReorderServer(fromServerId, toServerId))
    }
    val pullRefreshBaseHeightPx = with(LocalDensity.current) {
        (pullRefreshPresentation.circleSize + 32.dp).toPx()
    }
    Box(modifier = modifier.fillMaxSize()) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        state = gridState,
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
        if (panelGroup) {
            proxyHomeGroupSection(groupSelector)
            item(key = "group-header-${page.group.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "subscription") {
                SkipiSubscriptionServerPanelSegment(
                    segment = if (panelExpanded) {
                        SkipiSubscriptionPanelSegment.ExpandedHeader
                    } else {
                        SkipiSubscriptionPanelSegment.CollapsedHeader
                    },
                    surfaceColor = providerSurface,
                    borderColor = providerBorder,
                    dividerColor = providerDivider,
                ) {
                    if (groupHeader != null) {
                        HomeProxyGroupCard(
                            groupId = page.group.id,
                            header = groupHeader,
                            onAction = onAction,
                        )
                    } else if (subscription != null) {
                        HomeSubscriptionCard(
                            subscription = subscription,
                            expanded = page.subscriptionExpanded,
                            onToggleExpanded = { onAction(ProxyHomeAction.ToggleSubscriptionExpanded(page.group.id)) },
                            context = rowContext,
                            onAction = onAction,
                        )
                    }
                }
            }
            if (panelExpanded) {
                if (pageServers.isEmpty()) {
                    item(key = "subscription-empty-${page.group.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "subscription_empty") {
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
                        ReorderableItem(
                            state = reorderableState.reorderableState,
                            key = "server-${server.id}",
                            enabled = reorderEnabled,
                            // Rows appear and disappear instantly; the default spring would animate every row.
                            animateItemModifier = Modifier,
                        ) { isDragging ->
                            val dragModifier = Modifier.longPressReorderDragHandle(
                                scope = this,
                                enabled = reorderEnabled,
                                state = reorderableState,
                            )
                            SkipiSubscriptionServerPanelSegment(
                                segment = segment,
                                surfaceColor = providerSurface,
                                borderColor = providerBorder,
                                dividerColor = providerDivider,
                            ) {
                                ServerListItem(
                                    server = server,
                                    context = rowContext,
                                    serverIndex = serverIndex,
                                    totalServers = pageServers.size,
                                    inSubscriptionGroup = true,
                                    isDragging = isDragging && reorderEnabled,
                                    dragModifier = dragModifier,
                                    onAction = onAction,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp),
                                )
                            }
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
                        val hasSearch = state.searchQuery.isNotBlank()
                        EmptyServerListCard(
                            hasSearch = hasSearch,
                            actions = proxyHomeEmptyStateActions(
                                hasAnyServers = state.pages.any { it.servers.isNotEmpty() } || state.servers.isNotEmpty(),
                                hasSearch = hasSearch,
                                canAddSubscription = canUseHomeAction(state, ProxyHomeActionId.AddSubscription),
                                canImport = canUseHomeAction(state, ProxyHomeActionId.ImportServers),
                                importSources = state.availableImportSources,
                                canAddServer = canUseHomeAction(state, ProxyHomeActionId.AddServer) &&
                                    state.availableServerKinds.isNotEmpty(),
                            ),
                            canAddSubscription = canUseHomeAction(state, ProxyHomeActionId.AddSubscription),
                            serverKinds = state.availableServerKinds.takeIf {
                                canUseHomeAction(state, ProxyHomeActionId.AddServer)
                            }.orEmpty(),
                            onAction = onAction,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = pageServers,
                    key = { _, server -> "server-${server.id}" },
                    contentType = { _, _ -> "server" },
                ) { serverIndex, server ->
                    ReorderableItem(
                        state = reorderableState.reorderableState,
                        key = "server-${server.id}",
                        enabled = reorderEnabled,
                        animateItemModifier = Modifier,
                    ) { isDragging ->
                        val dragModifier = Modifier.longPressReorderDragHandle(
                            scope = this,
                            enabled = reorderEnabled,
                            state = reorderableState,
                        )
                        ServerListItem(
                            server = server,
                            context = rowContext,
                            serverIndex = serverIndex,
                            totalServers = pageServers.size,
                            onAction = onAction,
                            inSubscriptionGroup = subscriptionGroup,
                            isDragging = isDragging && reorderEnabled,
                            dragModifier = dragModifier,
                            modifier = Modifier.padding(bottom = SkipiTheme.spacing.extraSmall),
                        )
                    }
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

/** `HH:MM:SS`; hours grow past two digits instead of wrapping. */
internal fun formatSessionDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val h = if (hours < 10) "0$hours" else "$hours"
    val m = if (minutes < 10) "0$minutes" else "$minutes"
    val s = if (seconds < 10) "0$seconds" else "$seconds"
    return "$h:$m:$s"
}

/**
 * Seconds of the current session. The host's start of the connection wins; only a host that cannot
 * say when the connection began makes this screen count from when it appeared.
 */
internal fun sessionDurationSeconds(connectedSince: TimeMark?, screenAppeared: TimeMark): Long =
    (connectedSince ?: screenAppeared).elapsedNow().inWholeSeconds

/**
 * The ticking state is returned unread: the panel must not read it, so the one-second tick
 * recomposes only the text that shows it instead of the whole connection panel.
 */
@Composable
internal fun rememberSessionDuration(connected: Boolean, connectedSince: TimeMark?): State<String>? {
    if (!connected) return null
    return produceState(initialValue = formatSessionDuration(sessionDurationSeconds(connectedSince, TimeSource.Monotonic.markNow())), connected, connectedSince) {
        val screenAppeared = TimeSource.Monotonic.markNow()
        while (true) {
            value = formatSessionDuration(sessionDurationSeconds(connectedSince, screenAppeared))
            delay(1000)
        }
    }
}

internal fun canUseHomeAction(
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
        is ProxyHomeAction.ReorderServer -> state.sortMode == ProxyHomeSortMode.Default &&
            action.fromServerId != action.toServerId &&
            state.servers.any { it.id == action.fromServerId } &&
            state.servers.any { it.id == action.toServerId }
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

internal fun proxyHomeAddMenuIds(
    canAddSubscription: Boolean,
    canImport: Boolean,
    importSources: Set<ProxyHomeImportSource>,
    canAddServer: Boolean,
): List<String> = buildList {
    if (canAddSubscription) add("add-subscription")
    if (canImport) {
        if (ProxyHomeImportSource.Clipboard in importSources) add("import:" + ProxyHomeImportSource.Clipboard.name)
        if (ProxyHomeImportSource.QrCode in importSources) add("import:" + ProxyHomeImportSource.QrCode.name)
    }
    if (canAddServer) add("add-server")
}

/** Buttons of the first-run Home: shown only while there is no server at all. */
internal data class ProxyHomeEmptyStateActions(
    val paste: Boolean,
    val add: Boolean,
    val scanQr: Boolean,
) {
    val any: Boolean get() = paste || add || scanQr
}

internal fun proxyHomeEmptyStateActions(
    hasAnyServers: Boolean,
    hasSearch: Boolean,
    canAddSubscription: Boolean,
    canImport: Boolean,
    importSources: Set<ProxyHomeImportSource>,
    canAddServer: Boolean,
): ProxyHomeEmptyStateActions {
    if (hasAnyServers || hasSearch) return ProxyHomeEmptyStateActions(paste = false, add = false, scanQr = false)
    return ProxyHomeEmptyStateActions(
        paste = canImport && ProxyHomeImportSource.Clipboard in importSources,
        add = canAddSubscription || canAddServer,
        scanQr = canImport && ProxyHomeImportSource.QrCode in importSources,
    )
}

/** Entries behind the first-run "Add" button: the "+" menu without its import entries. */
internal fun proxyHomeEmptyStateAddMenuIds(
    canAddSubscription: Boolean,
    canAddServer: Boolean,
): List<String> = proxyHomeAddMenuIds(
    canAddSubscription = canAddSubscription,
    canImport = false,
    importSources = emptySet(),
    canAddServer = canAddServer,
)

/**
 * The group a settled pager page should select, or null when the pager must not override the selection:
 * a collapsed (e.g. minimized window) viewport, an unknown page, or a page that is already selected.
 */
internal fun pagerSettledGroupToSelect(
    groupIds: List<String>,
    settledIndex: Int,
    viewportWidth: Int,
    selectedGroupId: String?,
): String? {
    if (viewportWidth <= 0) return null
    val groupId = groupIds.getOrNull(settledIndex) ?: return null
    return groupId.takeIf { it != selectedGroupId }
}
