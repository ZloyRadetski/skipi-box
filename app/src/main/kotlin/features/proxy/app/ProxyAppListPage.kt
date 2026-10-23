// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.proxy.app

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.modes.RunModeVpnService
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListEditorScreen
import features.config.rememberTrafficConfigEditorController
import features.proxy.app.model.ProxyAppListItem
import kotlinx.coroutines.delay
import system.ANDROID_APP_ICON_SIZE_DP
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import ui.components.AppPullToRefresh
import ui.clipboard.getPlainText
import ui.clipboard.setPlainText
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.layout.pageScrollModifiers
import kotlin.time.Duration.Companion.milliseconds

private const val ProxyAppListAutomaticLoadingMinVisibleMillis = 500L

@Composable
fun ProxyAppListPage(
    padding: PaddingValues,
    trafficConfigId: Int,
) {
    val pageState = rememberProxyAppListPageState()
    val appState by LocalAppStateStore.current.collectAppState()
    val selfPackageName = LocalContext.current.applicationContext.packageName
    val updateAppState = LocalUpdateAppState.current
    val isWideScreen = LocalIsWideScreen.current
    val services = LocalAppServices.current
    val packageCatalog = services.packageCatalog
    val userSpaces = services.userSpaces
    val tipNotifier = services.tipNotifier
    val clipboard = LocalClipboard.current
    val actionScope = rememberCoroutineScope()

    val trafficConfig = appState.trafficConfigs.firstOrNull { config -> config.id == trafficConfigId }
        ?: return
    val configuredSelectedApps = trafficConfig.proxyAppListSelectedApps
    val editorController = rememberTrafficConfigEditorController(updateAppState)
    val isVpnServiceMode = appState.runMode == RunModeVpnService
    val selectedAppKeys = remember(configuredSelectedApps) {
        configuredSelectedApps.toSet()
    }
    val vpnServiceUserId = if (isVpnServiceMode) {
        pageState.userSpaces.firstOrNull()?.id
    } else {
        null
    }
    val userTabIds = remember(pageState.userTabs) {
        pageState.userTabs.map { tab -> tab.id }
    }
    val selectedUserId = pageState.selectedUserId
        ?.takeIf { userId -> userId in userTabIds }
        ?: userTabIds.firstOrNull()
    val selectedUserIndex = remember(userTabIds, selectedUserId) {
        userTabIds.indexOf(selectedUserId)
            .coerceAtLeast(0)
    }
    val userPagerState = key(userTabIds) {
        rememberPagerState(
            initialPage = selectedUserIndex,
            pageCount = { userTabIds.size.coerceAtLeast(1) },
        )
    }
    val iconSizePx = with(LocalDensity.current) {
        ANDROID_APP_ICON_SIZE_DP.dp.roundToPx()
    }

    ProxyAppListPageEffects(
        pageState = pageState,
        selectedAppKeys = selectedAppKeys,
        isVpnServiceMode = isVpnServiceMode,
        vpnServiceUserId = vpnServiceUserId,
        selfPackageName = selfPackageName,
        selectedUserIndex = selectedUserIndex,
        userTabIds = userTabIds,
        userPagerState = userPagerState,
        packageCatalog = packageCatalog,
        userSpaces = userSpaces,
        tipNotifier = tipNotifier,
    )

    SkipiTrafficConfigProxyAppListEditorScreen(
        profile = trafficConfig,
        controller = editorController,
        actionScope = actionScope,
        padding = padding,
        isWideScreen = isWideScreen,
        readClipboardText = { clipboard.getPlainText() },
        writeClipboardText = { text -> clipboard.setPlainText(text) },
        currentUserId = selectedUserId,
        selfPackageName = selfPackageName,
        onNotice = { notice -> tipNotifier.show(notice) },
        showSystemApps = pageState.showSystemApps,
        onToggleSystemApps = { pageState.showSystemApps = !pageState.showSystemApps },
        headerContent = {
            Column {
                ProxyAppListSearchBar(
                    searchValue = pageState.searchValue,
                    onSearchValueChange = { value -> pageState.searchValue = value },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                )
                if (pageState.userTabs.size > 1) {
                    ProxyAppListUserSpaceTabs(
                        tabs = pageState.userTabs,
                        selectedUserId = selectedUserId,
                        onSelectedUserIdChange = { userId -> pageState.selectedUserId = userId },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
        },
        appContent = { contentPadding, listPadding, scrollBehavior, selectedKeys, modeIndex, onSelectionChange ->
            ProxyAppListContent(
                pageState = pageState,
                selectedAppKeys = selectedKeys,
                modeIndex = modeIndex,
                iconSizePx = iconSizePx,
                contentPadding = contentPadding,
                listPadding = listPadding,
                scrollBehavior = scrollBehavior,
                userPagerState = userPagerState,
                onAppCheckedChange = { item, isChecked ->
                    onSelectionChange(item.selectionKeys, isChecked)
                },
            )
        },
    )
}

@Composable
private fun ProxyAppListContent(
    pageState: ProxyAppListPageState,
    selectedAppKeys: Set<String>,
    modeIndex: Int,
    iconSizePx: Int,
    contentPadding: PaddingValues,
    listPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    userPagerState: PagerState,
    onAppCheckedChange: (ProxyAppListItem, Boolean) -> Unit,
) {
    var showAutomaticLoading by remember { mutableStateOf(false) }
    var automaticLoadingStartedAtMillis by remember { mutableStateOf<Long?>(null) }
    val automaticLoading = pageState.loadingApps && !pageState.refreshingApps

    LaunchedEffect(pageState.loadingApps, pageState.refreshingApps) {
        when {
            automaticLoading -> {
                automaticLoadingStartedAtMillis = SystemClock.elapsedRealtime()
                showAutomaticLoading = true
            }

            pageState.refreshingApps -> {
                automaticLoadingStartedAtMillis = null
                showAutomaticLoading = false
            }

            showAutomaticLoading -> {
                val startedAt = automaticLoadingStartedAtMillis
                val elapsed = if (startedAt == null) {
                    ProxyAppListAutomaticLoadingMinVisibleMillis
                } else {
                    SystemClock.elapsedRealtime() - startedAt
                }
                val remaining = ProxyAppListAutomaticLoadingMinVisibleMillis - elapsed
                if (remaining > 0L) {
                    delay(remaining.milliseconds)
                }
                automaticLoadingStartedAtMillis = null
                showAutomaticLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
    ) {
        AppPullToRefresh(
            isRefreshing = pageState.refreshingApps,
            onRefresh = pageState::requestRefresh,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentPadding = listPadding,
            topAppBarScrollBehavior = scrollBehavior,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            refreshTexts = listOf(
                stringResource(R.string.proxy_app_list_pull_to_refresh),
                stringResource(R.string.proxy_app_list_release_to_refresh),
                stringResource(R.string.proxy_app_list_refreshing),
                stringResource(R.string.proxy_app_list_refreshed),
            ),
        ) {
            HorizontalPager(
                state = userPagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
                verticalAlignment = Alignment.Top,
            ) { page ->
                ProxyAppListUserPage(
                    userId = pageState.userTabs.getOrNull(page)?.id,
                    pageState = pageState,
                    selectedAppKeys = selectedAppKeys,
                    modeIndex = modeIndex,
                    iconSizePx = iconSizePx,
                    contentPadding = contentPadding,
                    listPadding = listPadding,
                    scrollBehavior = scrollBehavior,
                    onAppCheckedChange = onAppCheckedChange,
                )
            }
        }
        if (showAutomaticLoading) {
            ProxyAppListLoadingState(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ProxyAppListLoadingState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(MiuixTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InfiniteProgressIndicator(
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            size = 28.dp,
            strokeWidth = 2.dp,
            orbitingDotSize = 2.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.proxy_app_list_loading),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ProxyAppListUserPage(
    userId: Int?,
    pageState: ProxyAppListPageState,
    selectedAppKeys: Set<String>,
    modeIndex: Int,
    iconSizePx: Int,
    contentPadding: PaddingValues,
    listPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onAppCheckedChange: (ProxyAppListItem, Boolean) -> Unit,
) {
    val visibleApps = userId?.let { id ->
        pageState.preparedAppListData.visibleItemsByUser[id]
    }.orEmpty()
    val lazyListState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
        ) {
            when {
                visibleApps.isEmpty() -> item(key = "app_empty", contentType = "empty") {
                    ProxyAppListEmptyState()
                }

                else -> items(
                    items = visibleApps,
                    key = { item -> item.key },
                    contentType = { "app" },
                ) { item ->
                    val checked = remember(item.selectionKeys, selectedAppKeys) {
                        item.selectionKeys.any { key -> key in selectedAppKeys }
                    }
                    ProxyAppListItemCard(
                        app = item.app,
                        checked = checked,
                        enabled = modeIndex != ProxyAppListGlobalModeIndex,
                        sharedUid = item.sharedUid,
                        iconSizePx = iconSizePx,
                        onCheckedChange = { isChecked ->
                            onAppCheckedChange(item, isChecked)
                        },
                    )
                }
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }
}
