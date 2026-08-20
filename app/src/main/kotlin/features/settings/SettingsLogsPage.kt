// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.LocalAppChromeState
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.Route
import features.about.SkipiBugReportUri
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
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

@Composable
fun SettingsLogsPage(
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
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_category_logs),
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
                item(key = "logs_options_card") {
                    SmallTitle(text = stringResource(R.string.settings_logs))
                    SettingsSectionCard {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_log_level),
                            items = SettingsLogLevelOptions,
                            selectedIndex = appState.coreLogLevel,
                            onSelectedIndexChange = { index ->
                                updateAppState { it.copy(coreLogLevel = index) }
                            },
                        )
                        val retentionIndex = remember(appState.logRetentionDays) {
                            SettingsLogRetentionOptionValues.indexOfFirst { it.first == appState.logRetentionDays }
                                .takeIf { it >= 0 } ?: 2
                        }
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_log_retention),
                            summary = stringResource(R.string.settings_log_retention_summary),
                            items = SettingsLogRetentionOptionValues.map { stringResource(it.second) },
                            selectedIndex = retentionIndex,
                            onSelectedIndexChange = { index ->
                                val days = SettingsLogRetentionOptionValues.getOrNull(index)?.first ?: 7
                                updateAppState { it.copy(logRetentionDays = days) }
                                features.logs.AndroidCoreLogRepository.pruneOlderThanDays(days)
                                features.logs.AndroidAccessLogRepository.pruneOlderThanDays(days)
                                features.logs.AndroidLogcatRepository.pruneOlderThanDays(days)
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_record_access_log),
                            checked = appState.enableAccessLog,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableAccessLog = enabled) }
                            },
                        )
                    }
                }

                item(key = "logs_viewers_card") {
                    SmallTitle(text = stringResource(R.string.settings_access_logs))
                    SettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(R.string.settings_core_logs),
                            onClick = { navigator.push(Route.CoreLogs) },
                        )
                        AnimatedVisibility(
                            visible = appState.enableAccessLog,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            ArrowPreference(
                                title = stringResource(R.string.settings_access_logs),
                                onClick = { navigator.push(Route.AccessLogs) },
                            )
                        }
                        ArrowPreference(
                            title = stringResource(R.string.settings_logcat),
                            onClick = { navigator.push(Route.LogcatLogs) },
                        )
                    }
                }

                item(key = "logs_feedback_card") {
                    SmallTitle(text = stringResource(R.string.settings_feedback))
                    SettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(R.string.about_bug_report),
                            summary = stringResource(R.string.about_bug_report_summary),
                            onClick = { uriHandler.openUri(SkipiBugReportUri) },
                        )
                    }
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = innerContentPadding,
            )
        }
    }
}
