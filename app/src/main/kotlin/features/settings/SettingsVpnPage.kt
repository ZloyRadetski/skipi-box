// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.Route
import app.skipi.ui.settings.SkipiVpnSettingsScreen
import app.skipi.ui.settings.rememberVpnSettingsLabels
import app.skipi.ui.settings.VpnSettingsState
import app.skipi.ui.settings.tunSettingsSummary
import system.isIgnoringBatteryOptimizations
import system.openAppDetailsSettings
import system.openBatteryOptimizationSettings
import system.requestIgnoreBatteryOptimizations

@Composable
fun SettingsVpnPage(padding: PaddingValues) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val sheetState = rememberSettingsSheetState(updateAppState)
    var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) unrestricted = isIgnoringBatteryOptimizations(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val labels = rememberVpnSettingsLabels()
    val state = VpnSettingsState(
        tunSummary = tunSettingsSummary(
            mtu = appState.tunMtu,
            vpnDns = appState.tunVpnDns,
            ipv4Cidr = appState.tunIpv4Cidr,
            ipv6Cidr = appState.tunIpv6Cidr,
            showVpnDns = false,
        ),
        hevTunEnabled = appState.enableVpnHevTun,
        appendHttpProxyEnabled = appState.enableVpnAppendHttpProxy,
        strictFullTunnelEnabled = appState.enableStrictFullTunnel,
        autoConnectOnAppOpen = appState.autoConnectOnAppOpen,
        autoConnectOnBoot = appState.autoConnectOnBoot,
        killSwitchEnabled = appState.enableKillSwitch,
        batteryOptimizationUnrestricted = unrestricted,
        wakeLockEnabled = appState.enableWakeLock,
        seamlessNetworkSwitchingEnabled = appState.enableSeamlessNetworkSwitching,
    )

    Box(Modifier.fillMaxSize()) {
        SkipiVpnSettingsScreen(
            state = state,
            labels = labels,
            padding = padding,
            isWideScreen = isWideScreen,
            onBack = navigator::pop,
            onOpenTunSettings = { sheetState.openTunSettings(appState) },
            onHevTunChange = { value -> updateAppState { it.copy(enableVpnHevTun = value) } },
            onAppendHttpProxyChange = { value -> updateAppState { it.copy(enableVpnAppendHttpProxy = value) } },
            onStrictFullTunnelChange = { value -> updateAppState { it.copy(enableStrictFullTunnel = value) } },
            onAutoConnectOnOpenChange = { value -> updateAppState { it.copy(autoConnectOnAppOpen = value) } },
            onAutoConnectOnBootChange = { value -> updateAppState { it.copy(autoConnectOnBoot = value) } },
            onKillSwitchChange = { enabled ->
                updateAppState { it.copy(enableKillSwitch = enabled) }
                if (enabled) openSystemVpnSettings(context)
            },
            onBatteryOptimizationChange = { enabled ->
                if (enabled) requestIgnoreBatteryOptimizations(context) else openBatteryOptimizationSettings(context)
            },
            onWakeLockChange = { value -> updateAppState { it.copy(enableWakeLock = value) } },
            onSeamlessSwitchingChange = { value -> updateAppState { it.copy(enableSeamlessNetworkSwitching = value) } },
            onOpenSystemVpnSettings = { openSystemVpnSettings(context) },
            onOpenNetworkAutomation = { navigator.push(Route.SettingsNetworkAutomation) },
        )
        SettingsBottomSheetsHost(
            appState = appState,
            sheetState = sheetState,
            updateAppState = updateAppState,
        )
    }
}

private fun openSystemVpnSettings(context: Context) {
    val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}
