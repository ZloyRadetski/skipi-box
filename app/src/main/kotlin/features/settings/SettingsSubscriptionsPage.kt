// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.LocalAppChromeState
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.Route
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import ui.AppTheme
import ui.components.BackNavigationIcon
import ui.components.WarningConfirmDialog
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

@Composable
fun SettingsSubscriptionsPage(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var showDeviceHeadersDisableConfirmation by rememberSaveable { mutableStateOf(false) }

    val pingSettingsSummary = subscriptionPingSettingsSummary(
        url = appState.subscriptionPingUrl,
        timeoutMillis = appState.subscriptionPingTimeoutMillis,
    )
    val subscriptionFetchTimeoutValues = listOf(10, 15, 20, 30, 45, 60, 90, 120)
    val secondsUnit = stringResource(R.string.unit_seconds_short)
    val subscriptionFetchTimeoutOptions = listOf(
        "10 $secondsUnit",
        "15 $secondsUnit",
        "20 $secondsUnit",
        "30 $secondsUnit",
        "45 $secondsUnit",
        "60 $secondsUnit",
        "90 $secondsUnit",
        "120 $secondsUnit",
    )
    val subscriptionFetchTimeoutIndex = subscriptionFetchTimeoutValues
        .indexOf(appState.subscriptionFetchTimeoutSeconds)
        .let { if (it >= 0) it else subscriptionFetchTimeoutValues.indexOf(30).coerceAtLeast(0) }

    Scaffold(
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_category_subscriptions),
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
                item(key = "subscriptions_general_card") {
                    SmallTitle(text = stringResource(R.string.settings_header_subscriptions))
                    SettingsSectionCard {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_subscription_fetch_timeout),
                            summary = stringResource(R.string.settings_subscription_fetch_timeout_summary),
                            items = subscriptionFetchTimeoutOptions,
                            selectedIndex = subscriptionFetchTimeoutIndex,
                            onSelectedIndexChange = { index ->
                                val seconds = subscriptionFetchTimeoutValues[index]
                                updateAppState { it.copy(subscriptionFetchTimeoutSeconds = seconds) }
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_user_agents),
                            summary = stringResource(R.string.settings_user_agents_summary, appState.subscriptionUserAgents.size),
                            onClick = { navigator.push(Route.SubscriptionUserAgents) },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_subscription_device_headers),
                            summary = stringResource(R.string.settings_subscription_device_headers_summary),
                            checked = appState.enableSubscriptionDeviceHeaders,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    updateAppState { it.copy(enableSubscriptionDeviceHeaders = true) }
                                } else {
                                    showDeviceHeadersDisableConfirmation = true
                                }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_deletion_confirmation),
                            summary = stringResource(R.string.settings_deletion_confirmation_summary),
                            checked = appState.enableDeletionConfirmation,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableDeletionConfirmation = enabled) }
                            },
                        )
                    }
                }

                item(key = "subscriptions_ping_card") {
                    SmallTitle(text = stringResource(R.string.subscription_ping_settings))
                    SettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(R.string.subscription_ping_settings),
                            summary = pingSettingsSummary,
                            onClick = { navigator.push(Route.SubscriptionPingSettings) },
                        )
                    }
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = innerContentPadding,
            )

            WarningConfirmDialog(
                show = showDeviceHeadersDisableConfirmation,
                title = stringResource(R.string.settings_subscription_device_headers_disable_title),
                summary = stringResource(R.string.settings_subscription_device_headers_disable_summary),
                dismissText = stringResource(R.string.common_cancel),
                confirmText = stringResource(R.string.settings_subscription_device_headers_disable_action),
                onDismissRequest = { showDeviceHeadersDisableConfirmation = false },
                onConfirm = {
                    updateAppState { it.copy(enableSubscriptionDeviceHeaders = false) }
                    showDeviceHeadersDisableConfirmation = false
                },
            )
        }
    }
}
