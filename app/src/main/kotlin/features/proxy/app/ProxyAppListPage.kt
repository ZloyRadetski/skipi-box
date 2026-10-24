// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalUpdateAppState
import app.collectAppState
import app.modes.RunModeVpnService
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListEditorScreen
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListLoadingState
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListPages
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListSearchBar
import app.skipi.ui.config.SkipiTrafficConfigProxyAppListUserTabs
import features.config.rememberTrafficConfigEditorController
import kotlinx.coroutines.delay
import system.ANDROID_APP_ICON_SIZE_DP
import ui.clipboard.getPlainText
import ui.clipboard.setPlainText
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
    val selectedUserId = pageState.selectedUserId
        ?.takeIf { userId -> pageState.userTabs.any { tab -> tab.id == userId } }
        ?: pageState.userTabs.firstOrNull()?.id
    val iconSizePx = with(LocalDensity.current) {
        ANDROID_APP_ICON_SIZE_DP.dp.roundToPx()
    }

    ProxyAppListPageEffects(
        pageState = pageState,
        selectedAppKeys = selectedAppKeys,
        isVpnServiceMode = isVpnServiceMode,
        vpnServiceUserId = vpnServiceUserId,
        selfPackageName = selfPackageName,
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
                SkipiTrafficConfigProxyAppListSearchBar(
                    query = pageState.searchValue,
                    onQueryChange = { value -> pageState.searchValue = value },
                    onDebouncedQueryChange = { value -> pageState.debouncedSearchValue = value },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                )
                SkipiTrafficConfigProxyAppListUserTabs(
                    tabs = pageState.userTabs,
                    selectedUserId = selectedUserId,
                    onSelectedUserIdChange = { userId -> pageState.selectedUserId = userId },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        },
        appContent = { contentPadding, listPadding, scrollBehavior, selectedKeys, modeIndex, onSelectionChange ->
            ProxyAppListLoadingOverlay(pageState = pageState) {
                SkipiTrafficConfigProxyAppListPages(
                    itemsByUser = pageState.preparedAppListData.visibleItemsByUser,
                    tabs = pageState.userTabs,
                    selectedUserId = pageState.selectedUserId,
                    onSelectedUserIdChange = { userId -> pageState.selectedUserId = userId },
                    selectedKeys = selectedKeys,
                    modeIndex = modeIndex,
                    contentPadding = contentPadding,
                    listPadding = listPadding,
                    scrollBehavior = scrollBehavior,
                    isRefreshing = pageState.refreshingApps,
                    onRefresh = pageState::requestRefresh,
                    iconContent = { app, enabled ->
                        ProxyAppListNativeIcon(
                            app = app,
                            enabled = enabled,
                            iconSizePx = iconSizePx,
                        )
                    },
                    onSelectionChange = onSelectionChange,
                )
            }
        },
    )
}

@Composable
private fun ProxyAppListLoadingOverlay(
    pageState: ProxyAppListPageState,
    content: @Composable () -> Unit,
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

    Box(Modifier.fillMaxSize()) {
        content()
        if (showAutomaticLoading) {
            SkipiTrafficConfigProxyAppListLoadingState(modifier = Modifier.fillMaxSize())
        }
    }
}
