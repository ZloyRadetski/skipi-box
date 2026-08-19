// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.AppState
import desktop.DesktopProcessProxyEngine
import desktop.DesktopSubscriptionManager
import engine.proxy.ProxyEngineStatus
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class DesktopNavTab {
    Servers,
    Subscriptions,
    Routing,
    Settings,
}

private data class NavItemData(
    val tab: DesktopNavTab,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun DesktopMainView(
    appState: AppState,
    onUpdateAppState: ((AppState) -> AppState) -> Unit,
    proxyEngine: DesktopProcessProxyEngine,
    subscriptionManager: DesktopSubscriptionManager,
    engineStatus: ProxyEngineStatus,
    onStatusChange: (ProxyEngineStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(DesktopNavTab.Servers) }

    val navItems = remember {
        listOf(
            NavItemData(DesktopNavTab.Servers, "Серверы", MiuixIcons.AppRecording),
            NavItemData(DesktopNavTab.Subscriptions, "Подписки", MiuixIcons.Layers),
            NavItemData(DesktopNavTab.Routing, "Маршрутизация", MiuixIcons.MindMap),
            NavItemData(DesktopNavTab.Settings, "Настройки", MiuixIcons.Settings),
        )
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        // Left Navigation Rail
        NavigationRail(
            modifier = Modifier
                .fillMaxHeight()
                .background(MiuixTheme.colorScheme.surface),
        ) {
            navItems.forEach { item ->
                NavigationRailItem(
                    selected = selectedTab == item.tab,
                    onClick = { selectedTab = item.tab },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Crossfade(targetState = selectedTab) { tab ->
                when (tab) {
                    DesktopNavTab.Servers -> DesktopProxyScreen(
                        appState = appState,
                        onUpdateAppState = onUpdateAppState,
                        proxyEngine = proxyEngine,
                        engineStatus = engineStatus,
                        onStatusChange = onStatusChange,
                    )
                    DesktopNavTab.Subscriptions -> DesktopSubscriptionScreen(
                        appState = appState,
                        onUpdateAppState = onUpdateAppState,
                        subscriptionManager = subscriptionManager,
                    )
                    DesktopNavTab.Routing -> DesktopRoutingScreen(
                        appState = appState,
                        onUpdateAppState = onUpdateAppState,
                    )
                    DesktopNavTab.Settings -> DesktopSettingsScreen(
                        appState = appState,
                        onUpdateAppState = onUpdateAppState,
                    )
                }
            }
        }
    }
}
