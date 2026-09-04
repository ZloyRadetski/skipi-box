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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalAppChromeState
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.modes.LanguageModeChinese
import app.modes.LanguageModeEnglish
import app.modes.LanguageModePersian
import app.modes.LanguageModeRussian
import app.modes.LanguageModeSystem
import app.navigation.Route
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import ui.AppTheme
import ui.components.AppOverlayDropdownPreference
import ui.components.BackNavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

@Composable
fun SettingsGeneralPage(
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

    val languageOptions = listOf(
        stringResource(R.string.option_follow_system),
        stringResource(R.string.option_english),
        stringResource(R.string.option_russian),
        stringResource(R.string.option_chinese),
        stringResource(R.string.option_persian),
    )
    val languageModes = listOf(
        LanguageModeSystem,
        LanguageModeEnglish,
        LanguageModeRussian,
        LanguageModeChinese,
        LanguageModePersian,
    )
    val languageSelectionIndex = languageModes.indexOf(appState.languageMode).coerceAtLeast(0)

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_category_general),
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
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = innerListPadding,
            ) {
                item(key = "general_localization_feedback") {
                    SmallTitle(text = stringResource(R.string.settings_localization_and_feedback))
                    SettingsSectionCard {
                        AppOverlayDropdownPreference(
                            title = stringResource(R.string.settings_language),
                            items = languageOptions,
                            selectedIndex = languageSelectionIndex,
                            onSelectedIndexChange = { index ->
                                updateAppState { it.copy(languageMode = languageModes[index]) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_enable_haptics),
                            summary = stringResource(R.string.settings_enable_haptics_summary),
                            checked = appState.enableHaptics,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableHaptics = enabled) }
                            },
                        )
                    }
                }

                item(key = "general_notifications") {
                    SmallTitle(text = stringResource(R.string.settings_notifications_title))
                    SettingsSectionCard {
                        SwitchPreference(
                            title = stringResource(R.string.settings_traffic_stats_notification),
                            summary = stringResource(R.string.settings_traffic_stats_notification_summary),
                            checked = appState.enableTrafficStatsNotification,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableTrafficStatsNotification = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_resource_files_notifications),
                            summary = stringResource(R.string.settings_resource_files_notifications_summary),
                            checked = appState.enableResourceFileNotifications,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableResourceFileNotifications = enabled) }
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

                item(key = "general_integration") {
                    SmallTitle(text = stringResource(R.string.settings_category_integration))
                    SettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(R.string.settings_url_schemes),
                            summary = stringResource(R.string.settings_url_schemes_summary),
                            onClick = { navigator.push(Route.SkipiUrlSchemes) },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_broadcast_control),
                            summary = stringResource(R.string.settings_broadcast_control_summary),
                            checked = appState.enableBroadcastControl,
                            onCheckedChange = { enabled ->
                                updateAppState { it.copy(enableBroadcastControl = enabled) }
                            },
                        )
                    }
                }

                item(key = "general_backup_reset") {
                    SmallTitle(text = stringResource(R.string.settings_category_backup_reset))
                    SettingsSectionCard(bottomPadding = 0.dp) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_category_backup_reset),
                            summary = stringResource(R.string.settings_category_backup_reset_summary),
                            onClick = { navigator.push(Route.SettingsBackupReset) },
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
