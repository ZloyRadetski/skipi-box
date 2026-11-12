// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Backup
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.layout.withTopBarGap
import app.skipi.ui.layout.fadeUnderTopBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import app.skipi.ui.components.SkipiDropdownPreference
import app.skipi.ui.components.AppSlider
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_back
import app.skipi.ui.resources.*
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

data class GeneralSettingsState(
    val languageMode: Int,
    val enableHaptics: Boolean,
    val enableTrafficStatsNotification: Boolean,
    val trafficStatsNotificationRefreshIntervalSeconds: Int,
    val enableResourceFileNotifications: Boolean,
    val enableDeletionConfirmation: Boolean,
    val enableBroadcastControl: Boolean,
    val closeToBackground: Boolean = false,
)

object GeneralSettingsValues {
    const val LanguageSystem = 0
    const val LanguageEnglish = 1
    const val LanguageChinese = 2
    const val LanguageRussian = 3
    const val LanguagePersian = 4
    const val MinTrafficRefreshSeconds = 1
    const val MaxTrafficRefreshSeconds = 10

    fun normalizeTrafficRefreshIntervalSeconds(value: Int): Int =
        value.coerceIn(MinTrafficRefreshSeconds, MaxTrafficRefreshSeconds)
}

enum class GeneralSettingsControl {
    Language,
    Haptics,
    TrafficStatsNotifications,
    ResourceFileNotifications,
    DeletionConfirmation,
    UrlSchemes,
    BroadcastControl,
    BackupAndReset,
    CloseToBackground,
}

/** Host operations that are available alongside the portable general settings. */
data class GeneralSettingsCapabilities(
    val language: Boolean = true,
    val haptics: Boolean = true,
    val trafficStatsNotifications: Boolean = true,
    val resourceFileNotifications: Boolean = true,
    val deletionConfirmation: Boolean = true,
    val urlSchemes: Boolean = true,
    val broadcastControl: Boolean = true,
    val backupAndReset: Boolean = true,
    /** A window that can go away while the app keeps running; Android has no such window. */
    val closeToBackground: Boolean = false,
) {
    val visibleControls: Set<GeneralSettingsControl>
        get() = buildSet {
            if (language) add(GeneralSettingsControl.Language)
            if (haptics) add(GeneralSettingsControl.Haptics)
            if (trafficStatsNotifications) add(GeneralSettingsControl.TrafficStatsNotifications)
            if (resourceFileNotifications) add(GeneralSettingsControl.ResourceFileNotifications)
            if (deletionConfirmation) add(GeneralSettingsControl.DeletionConfirmation)
            if (urlSchemes) add(GeneralSettingsControl.UrlSchemes)
            if (broadcastControl) add(GeneralSettingsControl.BroadcastControl)
            if (backupAndReset) add(GeneralSettingsControl.BackupAndReset)
            if (closeToBackground) add(GeneralSettingsControl.CloseToBackground)
        }

    companion object {
        /** Desktop has locale and deletion behavior, but no Android notification or intent adapters. */
        val Desktop = GeneralSettingsCapabilities(
            haptics = false,
            trafficStatsNotifications = false,
            resourceFileNotifications = false,
            broadcastControl = false,
            closeToBackground = true,
        )
    }
}

/** Shared general settings form; platform locale changes and destination navigation stay in the host. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiGeneralSettingsScreen(
    state: GeneralSettingsState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onSettingsChange: ((GeneralSettingsState) -> GeneralSettingsState) -> Unit,
    onBack: () -> Unit,
    onOpenUrlSchemes: () -> Unit,
    onOpenBackupReset: () -> Unit,
    capabilities: GeneralSettingsCapabilities = GeneralSettingsCapabilities(),
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val languageModes = listOf(
        GeneralSettingsValues.LanguageSystem,
        GeneralSettingsValues.LanguageEnglish,
        GeneralSettingsValues.LanguageRussian,
        GeneralSettingsValues.LanguageChinese,
        GeneralSettingsValues.LanguagePersian,
    )
    val languageOptions = listOf(
        stringResource(Res.string.option_follow_system),
        stringResource(Res.string.option_english),
        stringResource(Res.string.option_russian),
        stringResource(Res.string.option_chinese),
        stringResource(Res.string.option_persian),
    )
    val visibleControls = capabilities.visibleControls

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            key(state.languageMode) {
                val title = stringResource(Res.string.settings_category_general)
                val navigateBack: @Composable () -> Unit = {
                    BackNavigationIcon(onClick = onBack)
                }
                SmallTopAppBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = !isWideScreen,
                    navigationIcon = navigateBack,
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + if (isWideScreen) padding.calculateTopPadding() else 0.dp,
            start = innerPadding.calculateStartPadding(layoutDirection) + padding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection) + padding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + padding.calculateBottomPadding() + 12.dp,
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fadeUnderTopBar(contentPadding.calculateTopPadding()).fillMaxSize(),
                contentPadding = contentPadding.withTopBarGap(),
            ) {
                if (
                    GeneralSettingsControl.Language in visibleControls ||
                    GeneralSettingsControl.Haptics in visibleControls
                ) {
                    item(key = "general_localization_feedback") {
                        SmallTitle(text = stringResource(Res.string.settings_localization_and_feedback))
                        SkipiSettingsSectionCard {
                            if (GeneralSettingsControl.Language in visibleControls) {
                                SkipiDropdownPreference(
                                    title = stringResource(Res.string.settings_language),
                                    startAction = settingIcon(Icons.Rounded.Translate),
                                    items = languageOptions,
                                    selectedIndex = languageModes.indexOf(state.languageMode).coerceAtLeast(0),
                                    onSelectedIndexChange = { index ->
                                        languageModes.getOrNull(index)?.let { mode ->
                                            onSettingsChange { it.copy(languageMode = mode) }
                                        }
                                    },
                                )
                            }
                            if (GeneralSettingsControl.Haptics in visibleControls) {
                                SwitchPreference(
                                    title = stringResource(Res.string.settings_enable_haptics),
                                    startAction = settingIcon(Icons.Rounded.Vibration),
                                    summary = stringResource(Res.string.settings_enable_haptics_summary),
                                    checked = state.enableHaptics,
                                    onCheckedChange = { enabled -> onSettingsChange { it.copy(enableHaptics = enabled) } },
                                )
                            }
                        }
                    }
                }

                if (
                    GeneralSettingsControl.TrafficStatsNotifications in visibleControls ||
                    GeneralSettingsControl.ResourceFileNotifications in visibleControls ||
                    GeneralSettingsControl.DeletionConfirmation in visibleControls
                ) {
                    item(key = "general_notifications") {
                        SmallTitle(text = stringResource(Res.string.settings_notifications_title))
                        SkipiSettingsSectionCard {
                            if (GeneralSettingsControl.TrafficStatsNotifications in visibleControls) {
                                SwitchPreference(
                                    title = stringResource(Res.string.settings_traffic_stats_notification),
                                    startAction = settingIcon(Icons.Rounded.Insights),
                                    summary = stringResource(Res.string.settings_traffic_stats_notification_summary),
                                    checked = state.enableTrafficStatsNotification,
                                    onCheckedChange = { enabled ->
                                        onSettingsChange { it.copy(enableTrafficStatsNotification = enabled) }
                                    },
                                )
                                if (state.enableTrafficStatsNotification) {
                                    TrafficStatsRefreshIntervalPreference(
                                        refreshIntervalSeconds = state.trafficStatsNotificationRefreshIntervalSeconds,
                                        onRefreshIntervalSecondsChange = { seconds ->
                                            onSettingsChange {
                                                it.copy(trafficStatsNotificationRefreshIntervalSeconds = seconds)
                                            }
                                        },
                                    )
                                }
                            }
                            if (GeneralSettingsControl.ResourceFileNotifications in visibleControls) {
                                SwitchPreference(
                                    title = stringResource(Res.string.settings_resource_files_notifications),
                                    startAction = settingIcon(Icons.Rounded.NotificationsActive),
                                    summary = stringResource(Res.string.settings_resource_files_notifications_summary),
                                    checked = state.enableResourceFileNotifications,
                                    onCheckedChange = { enabled ->
                                        onSettingsChange { it.copy(enableResourceFileNotifications = enabled) }
                                    },
                                )
                            }
                            if (GeneralSettingsControl.DeletionConfirmation in visibleControls) {
                                SwitchPreference(
                                    title = stringResource(Res.string.settings_deletion_confirmation),
                                    startAction = settingIcon(Icons.Rounded.Delete),
                                    summary = stringResource(Res.string.settings_deletion_confirmation_summary),
                                    checked = state.enableDeletionConfirmation,
                                    onCheckedChange = { enabled ->
                                        onSettingsChange { it.copy(enableDeletionConfirmation = enabled) }
                                    },
                                )
                            }
                        }
                    }
                }

                if (
                    GeneralSettingsControl.UrlSchemes in visibleControls ||
                    GeneralSettingsControl.BroadcastControl in visibleControls ||
                    GeneralSettingsControl.CloseToBackground in visibleControls
                ) {
                    item(key = "general_integration") {
                        SmallTitle(text = stringResource(Res.string.settings_category_integration))
                        SkipiSettingsSectionCard {
                            if (GeneralSettingsControl.UrlSchemes in visibleControls) {
                                ArrowPreference(
                                    title = stringResource(Res.string.settings_url_schemes),
                                    startAction = settingIcon(Icons.Rounded.Link),
                                    summary = stringResource(Res.string.settings_url_schemes_summary),
                                    onClick = onOpenUrlSchemes,
                                )
                            }
                            if (GeneralSettingsControl.BroadcastControl in visibleControls) {
                                SwitchPreference(
                                    title = stringResource(Res.string.settings_broadcast_control),
                                    startAction = settingIcon(Icons.Rounded.SettingsRemote),
                                    summary = stringResource(Res.string.settings_broadcast_control_summary),
                                    checked = state.enableBroadcastControl,
                                    onCheckedChange = { enabled ->
                                        onSettingsChange { it.copy(enableBroadcastControl = enabled) }
                                    },
                                )
                            }
                            if (GeneralSettingsControl.CloseToBackground in visibleControls) {
                                SwitchPreference(
                                    title = stringResource(Res.string.settings_close_to_background),
                                    startAction = settingIcon(Icons.Rounded.Minimize),
                                    summary = stringResource(Res.string.settings_close_to_background_summary),
                                    checked = state.closeToBackground,
                                    onCheckedChange = { enabled ->
                                        onSettingsChange { it.copy(closeToBackground = enabled) }
                                    },
                                )
                            }
                        }
                    }
                }

                if (GeneralSettingsControl.BackupAndReset in visibleControls) {
                    item(key = "general_backup_reset") {
                        SmallTitle(text = stringResource(Res.string.settings_category_backup_reset))
                        SkipiSettingsSectionCard(bottomPadding = 0.dp) {
                            ArrowPreference(
                                title = stringResource(Res.string.settings_category_backup_reset),
                                startAction = settingIcon(Icons.Rounded.Backup),
                                summary = stringResource(Res.string.settings_category_backup_reset_summary),
                                onClick = onOpenBackupReset,
                            )
                        }
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun TrafficStatsRefreshIntervalPreference(
    refreshIntervalSeconds: Int,
    onRefreshIntervalSecondsChange: (Int) -> Unit,
) {
    val minimum = GeneralSettingsValues.MinTrafficRefreshSeconds
    val maximum = GeneralSettingsValues.MaxTrafficRefreshSeconds
    val current = GeneralSettingsValues.normalizeTrafficRefreshIntervalSeconds(refreshIntervalSeconds)
    var sliderValue by remember(refreshIntervalSeconds) { mutableFloatStateOf(current.toFloat()) }
    val secondsUnit = stringResource(Res.string.unit_seconds_short)

    SkipiSliderPreference(
        title = stringResource(Res.string.settings_traffic_stats_notification_refresh_interval),
        startAction = settingIcon(Icons.Rounded.Timelapse),
        valueLabel = "${sliderValue.roundToInt()} $secondsUnit",
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = {
            onRefreshIntervalSecondsChange(
                GeneralSettingsValues.normalizeTrafficRefreshIntervalSeconds(sliderValue.roundToInt()),
            )
        },
        valueRange = minimum.toFloat()..maximum.toFloat(),
        steps = maximum - minimum - 1,
        minLabel = "$minimum $secondsUnit",
        maxLabel = "$maximum $secondsUnit",
    )
}
