// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.LocalAppChromeState
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.navigation.Route
import app.R
import app.collectAppState
import features.settings.sheets.tunSettingsSummary
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import ui.AppTheme
import ui.components.BackNavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

@Composable
fun SettingsVpnPage(
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val sheetState = rememberSettingsSheetState(updateAppState)

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tunSummary = tunSettingsSummary(
        mtu = appState.tunMtu,
        vpnDns = appState.tunVpnDns,
        ipv4Cidr = appState.tunIpv4Cidr,
        ipv6Cidr = appState.tunIpv6Cidr,
        showVpnDns = true,
    )

    Scaffold(
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_category_vpn),
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        BackNavigationIcon(
                            onClick = { navigator.pop() },
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        val innerContentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val innerListPadding = pageListPadding(innerContentPadding)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.background)
                    .pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = innerListPadding,
            ) {
                item(key = "vpn_tun_card") {
                    SmallTitle(text = stringResource(R.string.settings_proxy_vpn_service))
                    SettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(R.string.settings_tun),
                            summary = tunSummary,
                            onClick = { sheetState.openTunSettings(appState) },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.configs_hevtun),
                            summary = stringResource(R.string.configs_hevtun_summary),
                            checked = appState.enableVpnHevTun,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableVpnHevTun = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.configs_local_dns),
                            summary = stringResource(R.string.configs_local_dns_summary),
                            checked = appState.enableVpnLocalDns,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableVpnLocalDns = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.configs_append_http_proxy),
                            summary = stringResource(R.string.configs_append_http_proxy_summary),
                            checked = appState.enableVpnAppendHttpProxy,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableVpnAppendHttpProxy = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_traffic_stats_notification),
                            summary = stringResource(R.string.settings_traffic_stats_notification_summary),
                            checked = appState.enableTrafficStatsNotification,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableTrafficStatsNotification = enabled) }
                            },
                        )
                    }
                }

                item(key = "vpn_stability_card") {
                    SmallTitle(text = stringResource(R.string.settings_stability_and_background))
                    SettingsSectionCard {
                        SwitchPreference(
                            title = stringResource(R.string.settings_battery_optimization),
                            summary = stringResource(
                                if (isIgnoringBatteryOptimizations) {
                                    R.string.settings_battery_optimization_unrestricted
                                } else {
                                    R.string.settings_battery_optimization_restricted
                                },
                            ),
                            checked = isIgnoringBatteryOptimizations,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    requestIgnoreBatteryOptimizations(context)
                                } else {
                                    openBatteryOptimizationSettings(context)
                                }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_wake_lock),
                            summary = stringResource(R.string.settings_wake_lock_summary),
                            checked = appState.enableWakeLock,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableWakeLock = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_seamless_network_switching),
                            summary = stringResource(R.string.settings_seamless_network_switching_summary),
                            checked = appState.enableSeamlessNetworkSwitching,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableSeamlessNetworkSwitching = enabled) }
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_network_automation_title),
                            summary = stringResource(R.string.settings_network_automation_summary),
                            onClick = { navigator.push(Route.SettingsNetworkAutomation) },
                        )
                    }
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = innerContentPadding,
            )

            SettingsBottomSheetsHost(
                appState = appState,
                sheetState = sheetState,
                updateAppState = updateAppState,
            )
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(directIntent)
    }.onFailure {
        openBatteryOptimizationSettings(context)
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(settingsIntent)
    }.onFailure {
        runCatching {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetailsIntent)
        }
    }
}
