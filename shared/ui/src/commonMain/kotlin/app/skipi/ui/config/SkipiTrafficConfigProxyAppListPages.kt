// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalScrollBarApi::class)

package app.skipi.ui.config

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.modes.ProxyAppListModeGlobal
import app.skipi.ui.components.AppPullToRefresh
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.proxy_app_list_pull_to_refresh
import app.skipi.ui.resources.proxy_app_list_refreshed
import app.skipi.ui.resources.proxy_app_list_refreshing
import app.skipi.ui.resources.proxy_app_list_release_to_refresh
import app.skipi.ui.theme.SkipiTheme
import features.proxy.app.model.AppPackageEntry
import features.proxy.app.model.ProxyAppListItem
import features.proxy.app.model.ProxyAppListUserSpaceTabUi
import kotlinx.coroutines.flow.collect
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Portable user pager and package rows; the host supplies inventory and native icons. */
@Composable
fun SkipiTrafficConfigProxyAppListPages(
    itemsByUser: Map<Int, List<ProxyAppListItem>>,
    tabs: List<ProxyAppListUserSpaceTabUi>,
    selectedUserId: Int?,
    onSelectedUserIdChange: (Int) -> Unit,
    selectedKeys: Set<String>,
    modeIndex: Int,
    contentPadding: PaddingValues,
    listPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    iconContent: @Composable (app: AppPackageEntry, enabled: Boolean) -> Unit,
    onSelectionChange: (selectionKeys: List<String>, isChecked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val userTabIds = remember(tabs) { tabs.map { tab -> tab.id } }
    val selectedUserIndex = remember(userTabIds, selectedUserId) {
        userTabIds.indexOf(selectedUserId).coerceAtLeast(0)
    }
    val userPagerState = key(userTabIds) {
        rememberPagerState(
            initialPage = selectedUserIndex,
            pageCount = { userTabIds.size.coerceAtLeast(1) },
        )
    }
    val currentTabs by rememberUpdatedState(tabs)
    val currentSelectedUserId by rememberUpdatedState(selectedUserId)
    val currentOnSelectedUserIdChange by rememberUpdatedState(onSelectedUserIdChange)

    LaunchedEffect(userTabIds) {
        val lastIndex = userTabIds.lastIndex
        if (lastIndex >= 0 && userPagerState.currentPage > lastIndex) {
            userPagerState.scrollToPage(lastIndex)
        }
    }

    LaunchedEffect(selectedUserIndex, userTabIds) {
        if (
            userTabIds.isNotEmpty() &&
            !userPagerState.isScrollInProgress &&
            userPagerState.currentPage != selectedUserIndex
        ) {
            userPagerState.animateScrollToPage(selectedUserIndex)
        }
    }

    LaunchedEffect(userPagerState, userTabIds) {
        snapshotFlow { userPagerState.targetPage }
            .collect { page ->
                currentTabs.getOrNull(page)?.let { tab ->
                    if (currentSelectedUserId != tab.id) {
                        currentOnSelectedUserIdChange(tab.id)
                    }
                }
            }
    }

    val hapticFeedback = LocalHapticFeedback.current
    val pullToRefreshTexts = listOf(
        stringResource(Res.string.proxy_app_list_pull_to_refresh),
        stringResource(Res.string.proxy_app_list_release_to_refresh),
        stringResource(Res.string.proxy_app_list_refreshing),
        stringResource(Res.string.proxy_app_list_refreshed),
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SkipiTheme.colors.background),
    ) {
        AppPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            hapticFeedback = hapticFeedback,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentPadding = listPadding,
            topAppBarScrollBehavior = scrollBehavior,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            refreshTexts = pullToRefreshTexts,
        ) {
            HorizontalPager(
                state = userPagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
                verticalAlignment = Alignment.Top,
            ) { page ->
                ProxyAppListUserPage(
                    userId = tabs.getOrNull(page)?.id,
                    itemsByUser = itemsByUser,
                    selectedKeys = selectedKeys,
                    modeIndex = modeIndex,
                    contentPadding = contentPadding,
                    listPadding = listPadding,
                    scrollBehavior = scrollBehavior,
                    iconContent = iconContent,
                    onSelectionChange = onSelectionChange,
                )
            }
        }
    }
}

@Composable
private fun ProxyAppListUserPage(
    userId: Int?,
    itemsByUser: Map<Int, List<ProxyAppListItem>>,
    selectedKeys: Set<String>,
    modeIndex: Int,
    contentPadding: PaddingValues,
    listPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    iconContent: @Composable (app: AppPackageEntry, enabled: Boolean) -> Unit,
    onSelectionChange: (selectionKeys: List<String>, isChecked: Boolean) -> Unit,
) {
    val visibleApps = userId?.let { id -> itemsByUser[id] }.orEmpty()
    val lazyListState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
        ) {
            if (visibleApps.isEmpty()) {
                item(key = "app_empty", contentType = "empty") {
                    ProxyAppListEmptyState()
                }
            } else {
                items(
                    items = visibleApps,
                    key = { item -> item.key },
                    contentType = { "app" },
                ) { item ->
                    val checked = remember(item.selectionKeys, selectedKeys) {
                        item.selectionKeys.any { key -> key in selectedKeys }
                    }
                    val enabled = modeIndex != ProxyAppListModeGlobal
                    ProxyAppListItemCard(
                        app = item.app,
                        checked = checked,
                        enabled = enabled,
                        sharedUid = item.sharedUid,
                        iconContent = iconContent,
                        onCheckedChange = { isChecked ->
                            onSelectionChange(item.selectionKeys, isChecked)
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
