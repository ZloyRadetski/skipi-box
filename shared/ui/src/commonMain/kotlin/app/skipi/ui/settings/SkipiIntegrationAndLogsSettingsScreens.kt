// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.AppOverlayDropdownPreference
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun SettingsBackTopBar(
    title: String,
    isWideScreen: Boolean,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val back: @Composable () -> Unit = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        }
    }
    SmallTopAppBar(
        title = title,
        scrollBehavior = scrollBehavior,
        color = Color.Transparent,
        defaultWindowInsetsPadding = !isWideScreen,
        navigationIcon = back,
    )
}

@Composable
internal fun settingsPageContentPadding(
    inner: PaddingValues,
    outer: PaddingValues,
    isWideScreen: Boolean,
): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        // Compact bars include safe drawing insets; wide bars opt out and need
        // the host's status-bar inset added to their measured height.
        top = inner.calculateTopPadding() + if (isWideScreen) outer.calculateTopPadding() else 0.dp,
        start = inner.calculateStartPadding(direction) + outer.calculateStartPadding(direction),
        end = inner.calculateEndPadding(direction) + outer.calculateEndPadding(direction),
        bottom = inner.calculateBottomPadding() + outer.calculateBottomPadding() + 12.dp,
    )
}

data class IntegrationSettingsState(val broadcastControlEnabled: Boolean)

data class IntegrationSettingsLabels(
    val screenTitle: String,
    val sectionTitle: String,
    val urlSchemesTitle: String,
    val urlSchemesSummary: String,
    val broadcastControlTitle: String,
    val broadcastControlSummary: String,
)

/** Shared integration settings page. Navigation and persistence are supplied by the platform host. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiIntegrationSettingsScreen(
    state: IntegrationSettingsState,
    labels: IntegrationSettingsLabels,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onOpenUrlSchemes: () -> Unit,
    onBroadcastControlChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            key(labels.screenTitle) {
                SettingsBackTopBar(labels.screenTitle, isWideScreen, onBack)
            }
        },
    ) { innerPadding ->
        val contentPadding = settingsPageContentPadding(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "integration_settings") {
                    SmallTitle(text = labels.sectionTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(
                            title = labels.urlSchemesTitle,
                            summary = labels.urlSchemesSummary,
                            onClick = onOpenUrlSchemes,
                        )
                        SwitchPreference(
                            title = labels.broadcastControlTitle,
                            summary = labels.broadcastControlSummary,
                            checked = state.broadcastControlEnabled,
                            onCheckedChange = onBroadcastControlChange,
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

data class LogsSettingsState(
    val coreLogLevelIndex: Int,
    val retentionDays: Int,
    val accessLogEnabled: Boolean,
)

data class LogsSettingsLabels(
    val screenTitle: String,
    val optionsSectionTitle: String,
    val logLevelTitle: String,
    val logLevels: List<String>,
    val retentionTitle: String,
    val retentionSummary: String,
    val retentionOptions: List<String>,
    val retentionDays: List<Int>,
    val accessLogTitle: String,
    val viewersSectionTitle: String,
    val coreLogsTitle: String,
    val accessLogsTitle: String,
    val logcatTitle: String,
    val feedbackSectionTitle: String,
    val bugReportTitle: String,
    val bugReportSummary: String,
)

/** Shared logs settings page. The host owns log storage, pruning, navigation, and external links. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiLogsSettingsScreen(
    state: LogsSettingsState,
    labels: LogsSettingsLabels,
    padding: PaddingValues,
    isWideScreen: Boolean,
    showAccessLogs: Boolean,
    onBack: () -> Unit,
    onLogLevelChange: (Int) -> Unit,
    onRetentionDaysChange: (Int) -> Unit,
    onAccessLogChange: (Boolean) -> Unit,
    onOpenCoreLogs: () -> Unit,
    onOpenAccessLogs: () -> Unit,
    onOpenLogcat: () -> Unit,
    onOpenBugReport: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val retentionIndex = labels.retentionDays.indexOf(state.retentionDays).coerceAtLeast(0)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            key(labels.screenTitle) {
                SettingsBackTopBar(labels.screenTitle, isWideScreen, onBack)
            }
        },
    ) { innerPadding ->
        val contentPadding = settingsPageContentPadding(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "log_options") {
                    SmallTitle(text = labels.optionsSectionTitle)
                    SkipiSettingsSectionCard {
                        AppOverlayDropdownPreference(
                            title = labels.logLevelTitle,
                            items = labels.logLevels,
                            selectedIndex = state.coreLogLevelIndex.coerceIn(labels.logLevels.indices),
                            onSelectedIndexChange = onLogLevelChange,
                        )
                        AppOverlayDropdownPreference(
                            title = labels.retentionTitle,
                            summary = labels.retentionSummary,
                            items = labels.retentionOptions,
                            selectedIndex = retentionIndex,
                            onSelectedIndexChange = { index ->
                                labels.retentionDays.getOrNull(index)?.let(onRetentionDaysChange)
                            },
                        )
                        SwitchPreference(
                            title = labels.accessLogTitle,
                            checked = state.accessLogEnabled,
                            onCheckedChange = onAccessLogChange,
                        )
                    }
                }
                item(key = "log_viewers") {
                    SmallTitle(text = labels.viewersSectionTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(title = labels.coreLogsTitle, onClick = onOpenCoreLogs)
                        if (showAccessLogs) {
                            ArrowPreference(title = labels.accessLogsTitle, onClick = onOpenAccessLogs)
                        }
                        ArrowPreference(title = labels.logcatTitle, onClick = onOpenLogcat)
                    }
                }
                item(key = "logs_feedback") {
                    SmallTitle(text = labels.feedbackSectionTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(
                            title = labels.bugReportTitle,
                            summary = labels.bugReportSummary,
                            onClick = onOpenBugReport,
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
