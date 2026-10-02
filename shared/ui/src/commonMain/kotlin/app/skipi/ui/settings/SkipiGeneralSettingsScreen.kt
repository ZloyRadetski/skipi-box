// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

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
import app.skipi.ui.components.AppOverlayDropdownPreference
import app.skipi.ui.components.AppSlider
import app.skipi.ui.resources.Res
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            key(state.languageMode) {
                val title = stringResource(Res.string.settings_category_general)
                val navigateBack: @Composable () -> Unit = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "general_localization_feedback") {
                    SmallTitle(text = stringResource(Res.string.settings_localization_and_feedback))
                    SkipiSettingsSectionCard {
                        AppOverlayDropdownPreference(
                            title = stringResource(Res.string.settings_language),
                            items = languageOptions,
                            selectedIndex = languageModes.indexOf(state.languageMode).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                languageModes.getOrNull(index)?.let { mode ->
                                    onSettingsChange { it.copy(languageMode = mode) }
                                }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(Res.string.settings_enable_haptics),
                            summary = stringResource(Res.string.settings_enable_haptics_summary),
                            checked = state.enableHaptics,
                            onCheckedChange = { enabled -> onSettingsChange { it.copy(enableHaptics = enabled) } },
                        )
                    }
                }

                item(key = "general_notifications") {
                    SmallTitle(text = stringResource(Res.string.settings_notifications_title))
                    SkipiSettingsSectionCard {
                        SwitchPreference(
                            title = stringResource(Res.string.settings_traffic_stats_notification),
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
                        SwitchPreference(
                            title = stringResource(Res.string.settings_resource_files_notifications),
                            summary = stringResource(Res.string.settings_resource_files_notifications_summary),
                            checked = state.enableResourceFileNotifications,
                            onCheckedChange = { enabled ->
                                onSettingsChange { it.copy(enableResourceFileNotifications = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(Res.string.settings_deletion_confirmation),
                            summary = stringResource(Res.string.settings_deletion_confirmation_summary),
                            checked = state.enableDeletionConfirmation,
                            onCheckedChange = { enabled ->
                                onSettingsChange { it.copy(enableDeletionConfirmation = enabled) }
                            },
                        )
                    }
                }

                item(key = "general_integration") {
                    SmallTitle(text = stringResource(Res.string.settings_category_integration))
                    SkipiSettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(Res.string.settings_url_schemes),
                            summary = stringResource(Res.string.settings_url_schemes_summary),
                            onClick = onOpenUrlSchemes,
                        )
                        SwitchPreference(
                            title = stringResource(Res.string.settings_broadcast_control),
                            summary = stringResource(Res.string.settings_broadcast_control_summary),
                            checked = state.enableBroadcastControl,
                            onCheckedChange = { enabled ->
                                onSettingsChange { it.copy(enableBroadcastControl = enabled) }
                            },
                        )
                    }
                }

                item(key = "general_backup_reset") {
                    SmallTitle(text = stringResource(Res.string.settings_category_backup_reset))
                    SkipiSettingsSectionCard(bottomPadding = 0.dp) {
                        ArrowPreference(
                            title = stringResource(Res.string.settings_category_backup_reset),
                            summary = stringResource(Res.string.settings_category_backup_reset_summary),
                            onClick = onOpenBackupReset,
                        )
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

    Column(Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_traffic_stats_notification_refresh_interval),
                fontSize = 14.sp,
                fontWeight = themedFontWeight(FontWeight.Medium),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
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
        Spacer(Modifier.height(12.dp))
        AppSlider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                onRefreshIntervalSecondsChange(
                    GeneralSettingsValues.normalizeTrafficRefreshIntervalSeconds(sliderValue.roundToInt()),
                )
            },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = maximum - minimum - 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("$minimum $secondsUnit", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text("$maximum $secondsUnit", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
