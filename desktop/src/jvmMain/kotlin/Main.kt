// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.LocalAppStateStore
import app.LocalUpdateAppState
import app.modes.ColorModeDark
import desktop.DesktopProcessProxyEngine
import desktop.DesktopSettingsStore
import desktop.DesktopSubscriptionManager
import engine.proxy.ProxyEngineStatus
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.DesktopMainView

fun main() = application {
    val settingsStore = remember { DesktopSettingsStore() }
    val engine = remember { DesktopProcessProxyEngine() }
    val subscriptionManager = remember { DesktopSubscriptionManager() }
    val appState by settingsStore.state.collectAsState()
    var engineStatus by remember { mutableStateOf(engine.status()) }

    Window(
        onCloseRequest = {
            if (engineStatus.running) {
                engine.stop()
            }
            exitApplication()
        },
        title = "SKIPI — Xray Proxy Client",
        state = WindowState(size = DpSize(980.dp, 700.dp)),
    ) {
        CompositionLocalProvider(
            LocalAppStateStore provides settingsStore,
            LocalUpdateAppState provides { updater -> settingsStore.update(updater) },
        ) {
            AppTheme(
                colorMode = ColorModeDark,
                enableMaterialYou = false,
                systemDark = true,
            ) {
                DesktopMainView(
                    appState = appState,
                    onUpdateAppState = { updater -> settingsStore.update(updater) },
                    proxyEngine = engine,
                    subscriptionManager = subscriptionManager,
                    engineStatus = engineStatus,
                    onStatusChange = { newStatus -> engineStatus = newStatus },
                )
            }
        }
    }
}
