// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.Route
import app.skipi.ui.settings.LogsSettingsLabels
import app.skipi.ui.settings.LogsSettingsState
import app.skipi.ui.settings.SkipiLogsSettingsScreen
import features.about.SkipiBugReportUri

@Composable
fun SettingsLogsPage(padding: PaddingValues) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val uriHandler = LocalUriHandler.current
    val retentionValues = SettingsLogRetentionOptionValues.map { it.first }

    SkipiLogsSettingsScreen(
        state = LogsSettingsState(
            coreLogLevelIndex = appState.coreLogLevel,
            retentionDays = appState.logRetentionDays,
            accessLogEnabled = appState.enableAccessLog,
        ),
        labels = LogsSettingsLabels(
            screenTitle = stringResource(R.string.settings_category_logs),
            optionsSectionTitle = stringResource(R.string.settings_logs),
            logLevelTitle = stringResource(R.string.settings_log_level),
            logLevels = SettingsLogLevelOptions,
            retentionTitle = stringResource(R.string.settings_log_retention),
            retentionSummary = stringResource(R.string.settings_log_retention_summary),
            retentionOptions = SettingsLogRetentionOptionValues.map { stringResource(it.second) },
            retentionDays = retentionValues,
            accessLogTitle = stringResource(R.string.settings_record_access_log),
            viewersSectionTitle = stringResource(R.string.settings_access_logs),
            coreLogsTitle = stringResource(R.string.settings_core_logs),
            accessLogsTitle = stringResource(R.string.settings_access_logs),
            logcatTitle = stringResource(R.string.settings_logcat),
            feedbackSectionTitle = stringResource(R.string.settings_feedback),
            bugReportTitle = stringResource(R.string.about_bug_report),
            bugReportSummary = stringResource(R.string.about_bug_report_summary),
        ),
        padding = padding,
        isWideScreen = isWideScreen,
        showAccessLogs = appState.enableAccessLog,
        onBack = navigator::pop,
        onLogLevelChange = { index -> updateAppState { it.copy(coreLogLevel = index) } },
        onRetentionDaysChange = { days ->
            updateAppState { it.copy(logRetentionDays = days) }
            features.logs.AndroidCoreLogRepository.pruneOlderThanDays(days)
            features.logs.AndroidAccessLogRepository.pruneOlderThanDays(days)
            features.logs.AndroidLogcatRepository.pruneOlderThanDays(days)
        },
        onAccessLogChange = { enabled -> updateAppState { it.copy(enableAccessLog = enabled) } },
        onOpenCoreLogs = { navigator.push(Route.CoreLogs) },
        onOpenAccessLogs = { navigator.push(Route.AccessLogs) },
        onOpenLogcat = { navigator.push(Route.LogcatLogs) },
        onOpenBugReport = { uriHandler.openUri(SkipiBugReportUri) },
    )
}
