// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.LocalIsWideScreen
import app.LocalNavigator
import app.ProjectInfo
import app.R
import app.navigation.Route
import app.skipi.core.skipicore.Skipicore
import app.skipi.ui.settings.AboutRuntimeInfo
import app.skipi.ui.settings.AboutSettingsLabels
import app.skipi.ui.settings.SkipiAboutScreen

private const val SkipiProjectSourceUri = "https://github.com/ZloyRadetski/skipi-box"

@Composable
fun AboutPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val runtimeInfo = AboutRuntimeInfo(
        appName = ProjectInfo.PROJECT_NAME,
        appVersion = "${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
        skipiCoreVersion = ProjectInfo.SKIPI_CORE_VERSION,
        xrayCoreVersion = runCatching { Skipicore.coreVersion() }.getOrNull()?.ifBlank { null }
            ?: ProjectInfo.XRAY_CORE_VERSION,
        hevTunnelVersion = ProjectInfo.HEV_SOCKS5_TUNNEL_VERSION,
    )

    SkipiAboutScreen(
        labels = AboutSettingsLabels(
            screenTitle = stringResource(R.string.about_title),
            updatesTitle = stringResource(R.string.settings_updates_title),
            runtimeTitle = stringResource(R.string.about_runtime),
            otherTitle = stringResource(R.string.about_other),
            replayOnboardingTitle = stringResource(R.string.about_replay_onboarding),
            replayOnboardingSummary = stringResource(R.string.about_replay_onboarding_summary),
            telegramTitle = stringResource(R.string.about_telegram_channel),
            bugReportTitle = stringResource(R.string.about_bug_report),
            bugReportSummary = stringResource(R.string.about_bug_report_summary),
            sourceTitle = stringResource(R.string.about_view_skipi_source),
        ),
        runtime = runtimeInfo,
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = { navigator.pop() },
        onOpenCoreInfo = { uriHandler.openUri("https://github.com/ZloyRadetski/skipi-core") },
        onReplayOnboarding = { navigator.push(Route.Onboarding) },
        onOpenTelegram = { uriHandler.openUri(SkipiTelegramChannelUri) },
        onOpenBugReport = { uriHandler.openUri(SkipiBugReportUri) },
        onOpenSource = { uriHandler.openUri(SkipiProjectSourceUri) },
        logo = { AboutAppLogo() },
        updatesContent = { AboutUpdatesCard() },
    )
}
