// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalAppChromeState
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.ProjectInfo
import app.R
import app.collectAppState
import app.modes.RunModeVpnService
import app.navigation.Route
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

@Composable
fun SettingsPage(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_title),
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    subtitle = "v${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
                )
            }
        },
    ) { innerPadding ->
        SettingsContent(
            innerPadding = innerPadding,
            outerPadding = padding,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
        )
    }
}

@Composable
private fun SettingsContent(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
) {
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val isWideScreen = LocalIsWideScreen.current
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val lazyListState = rememberLazyListState()

    val contentPadding = pageContentPaddingWithCutout(
        innerPadding = innerPadding,
        outerPadding = outerPadding,
        isWideScreen = isWideScreen,
    )
    val listPadding = pageListPadding(contentPadding)

    LaunchedEffect(appState.runMode) {
        if (appState.runMode != RunModeVpnService) {
            updateAppState { state -> state.copy(runMode = RunModeVpnService, proxyRunning = false) }
        }
    }

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
            contentPadding = listPadding,
        ) {
            item(key = "section_appearance") {
                SmallTitle(text = stringResource(R.string.settings_header_appearance))
                SettingsCategoryGroupCard {
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Palette,
                        iconBackgroundColor = appState.customCategoryAppearanceColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_category_appearance),
                        summary = stringResource(R.string.settings_category_appearance_summary),
                        onClick = { navigator.push(Route.SettingsAppearance) },
                    )
                }
            }

            item(key = "section_network") {
                SmallTitle(text = stringResource(R.string.settings_header_network))
                SettingsCategoryGroupCard {
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Shield,
                        iconBackgroundColor = appState.customCategoryVpnColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_category_vpn),
                        summary = stringResource(R.string.settings_category_vpn_summary),
                        value = "MTU: ${appState.tunMtu}",
                        onClick = { navigator.push(Route.SettingsVpn) },
                        showDivider = true,
                    )
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Server,
                        iconBackgroundColor = appState.customCategoryProxyColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_category_local_proxy),
                        summary = stringResource(R.string.settings_category_local_proxy_summary),
                        value = ":${appState.localProxyPort}",
                        onClick = { navigator.push(Route.LocalProxySettings) },
                    )
                }
            }

            item(key = "section_subscriptions") {
                SmallTitle(text = stringResource(R.string.settings_header_subscriptions))
                SettingsCategoryGroupCard {
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Sync,
                        iconBackgroundColor = appState.customCategorySubscriptionsColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_category_subscriptions),
                        summary = stringResource(R.string.settings_category_subscriptions_summary),
                        onClick = { navigator.push(Route.SettingsSubscriptions) },
                    )
                }
            }

            item(key = "section_integration") {
                SmallTitle(text = stringResource(R.string.settings_header_integration))
                SettingsCategoryGroupCard {
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Bolt,
                        iconBackgroundColor = appState.customCategoryIntegrationColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_category_integration),
                        summary = stringResource(R.string.settings_category_integration_summary),
                        onClick = { navigator.push(Route.SettingsIntegration) },
                    )
                }
            }

            item(key = "section_diagnostics") {
                SmallTitle(text = stringResource(R.string.settings_header_diagnostics))
                SettingsCategoryGroupCard {
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Logs,
                        iconBackgroundColor = appState.customCategoryLogsColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_category_logs),
                        summary = stringResource(R.string.settings_category_logs_summary),
                        value = SettingsLogLevelOptions.getOrNull(appState.coreLogLevel)?.uppercase(),
                        onClick = { navigator.push(Route.SettingsLogs) },
                        showDivider = true,
                    )
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Backup,
                        iconBackgroundColor = appState.customCategoryBackupColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_category_backup_reset),
                        summary = stringResource(R.string.settings_category_backup_reset_summary),
                        onClick = { navigator.push(Route.SettingsBackupReset) },
                        showDivider = true,
                    )
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Bolt,
                        iconBackgroundColor = MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_category_speed_test),
                        summary = stringResource(R.string.settings_category_speed_test_summary),
                        onClick = { navigator.push(Route.SpeedTest) },
                        showDivider = true,
                    )
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Shield,
                        iconBackgroundColor = MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_category_dns_leak),
                        summary = stringResource(R.string.settings_category_dns_leak_summary),
                        onClick = { navigator.push(Route.DnsLeakTest) },
                    )
                }
            }

            item(key = "section_about") {
                SmallTitle(text = stringResource(R.string.settings_header_about))
                SettingsCategoryGroupCard(bottomPadding = 0.dp) {
                    SettingsCategoryEntry(
                        icon = SettingsIcons.Info,
                        iconBackgroundColor = appState.customCategoryAboutColor?.let { Color(it) } ?: MiuixTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_about_project),
                        summary = "v${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
                        onClick = { navigator.push(Route.About) },
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
