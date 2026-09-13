// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.MaxTrafficStatsNotificationRefreshIntervalSeconds
import app.MinTrafficStatsNotificationRefreshIntervalSeconds
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.components.AppOverlayDropdownPreference
import ui.components.AppSlider
import ui.components.BackNavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import ui.text.themedFontWeight
import kotlin.math.roundToInt

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
                        if (appState.enableTrafficStatsNotification) {
                            TrafficStatsNotificationRefreshIntervalPreference(
                                refreshIntervalSeconds = appState.trafficStatsNotificationRefreshIntervalSeconds,
                                onRefreshIntervalSecondsChange = { seconds ->
                                    updateAppState {
                                        it.copy(trafficStatsNotificationRefreshIntervalSeconds = seconds)
                                    }
                                },
                            )
                        }
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

@Composable
private fun TrafficStatsNotificationRefreshIntervalPreference(
    refreshIntervalSeconds: Int,
    onRefreshIntervalSecondsChange: (Int) -> Unit,
) {
    val clampedRefreshInterval = refreshIntervalSeconds.coerceIn(
        MinTrafficStatsNotificationRefreshIntervalSeconds,
        MaxTrafficStatsNotificationRefreshIntervalSeconds,
    )
    var sliderValue by remember(refreshIntervalSeconds) {
        mutableFloatStateOf(clampedRefreshInterval.toFloat())
    }
    val secondsUnit = stringResource(R.string.unit_seconds_short)

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_traffic_stats_notification_refresh_interval),
                fontSize = 14.sp,
                fontWeight = themedFontWeight(FontWeight.Medium),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${sliderValue.roundToInt()} $secondsUnit",
                    fontSize = 14.sp,
                    fontWeight = themedFontWeight(FontWeight.Bold),
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AppSlider(
            value = sliderValue,
            onValueChange = { value ->
                sliderValue = value
            },
            onValueChangeFinished = {
                onRefreshIntervalSecondsChange(
                    sliderValue.roundToInt().coerceIn(
                        MinTrafficStatsNotificationRefreshIntervalSeconds,
                        MaxTrafficStatsNotificationRefreshIntervalSeconds,
                    ),
                )
            },
            valueRange = MinTrafficStatsNotificationRefreshIntervalSeconds.toFloat()..
                MaxTrafficStatsNotificationRefreshIntervalSeconds.toFloat(),
            steps = MaxTrafficStatsNotificationRefreshIntervalSeconds -
                MinTrafficStatsNotificationRefreshIntervalSeconds - 1,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$MinTrafficStatsNotificationRefreshIntervalSeconds $secondsUnit",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Text(
                text = "$MaxTrafficStatsNotificationRefreshIntervalSeconds $secondsUnit",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}
