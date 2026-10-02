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
import app.skipi.ui.settings.VpnSettingsLabels
import app.skipi.ui.settings.VpnSettingsState
import features.settings.sheets.tunSettingsSummary
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

    val labels = VpnSettingsLabels(
        screenTitle = stringResource(R.string.settings_category_vpn),
        vpnSectionTitle = stringResource(R.string.settings_proxy_vpn_service),
        tunTitle = stringResource(R.string.settings_tun),
        hevTunTitle = stringResource(R.string.configs_hevtun),
        hevTunSummary = stringResource(R.string.configs_hevtun_summary),
        appendHttpTitle = stringResource(R.string.configs_append_http_proxy),
        appendHttpSummary = stringResource(R.string.configs_append_http_proxy_summary),
        strictFullTunnelTitle = stringResource(R.string.settings_strict_full_tunnel),
        strictFullTunnelSummary = stringResource(R.string.settings_strict_full_tunnel_summary),
        autoConnectSectionTitle = stringResource(R.string.settings_auto_connect_title),
        autoConnectOnOpenTitle = stringResource(R.string.settings_auto_connect_on_app_open_title),
        autoConnectOnOpenSummary = stringResource(R.string.settings_auto_connect_on_app_open_summary),
        autoConnectOnBootTitle = stringResource(R.string.settings_auto_connect_on_boot_title),
        autoConnectOnBootSummary = stringResource(R.string.settings_auto_connect_on_boot_summary),
        killSwitchSectionTitle = stringResource(R.string.settings_kill_switch),
        killSwitchTitle = stringResource(R.string.settings_kill_switch),
        killSwitchSummary = stringResource(R.string.settings_kill_switch_summary),
        systemVpnSettingsTitle = stringResource(R.string.settings_system_vpn_settings),
        systemVpnSettingsSummary = stringResource(R.string.settings_system_vpn_settings_summary),
        stabilitySectionTitle = stringResource(R.string.settings_stability_and_background),
        batteryOptimizationTitle = stringResource(R.string.settings_battery_optimization),
        batteryUnrestrictedSummary = stringResource(R.string.settings_battery_optimization_unrestricted),
        batteryRestrictedSummary = stringResource(R.string.settings_battery_optimization_restricted),
        wakeLockTitle = stringResource(R.string.settings_wake_lock),
        wakeLockSummary = stringResource(R.string.settings_wake_lock_summary),
        seamlessSwitchingTitle = stringResource(R.string.settings_seamless_network_switching),
        seamlessSwitchingSummary = stringResource(R.string.settings_seamless_network_switching_summary),
        networkAutomationTitle = stringResource(R.string.settings_network_automation_title),
        networkAutomationSummary = stringResource(R.string.settings_network_automation_summary),
    )
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
