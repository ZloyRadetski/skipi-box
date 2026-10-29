// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import app.LocalIsWideScreen
import app.LocalNavigator
import app.ProjectInfo
import app.navigation.Route
import app.skipi.core.skipicore.Skipicore
import app.skipi.ui.settings.AboutRuntimeInfo
import app.skipi.ui.settings.SkipiBugReportUri
import app.skipi.ui.settings.SkipiCoreSourceUri
import app.skipi.ui.settings.SkipiSourceUri
import app.skipi.ui.settings.SkipiTelegramChannelUri
import app.skipi.ui.settings.rememberAboutSettingsLabels
import app.skipi.ui.settings.SkipiAboutScreen

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
        labels = rememberAboutSettingsLabels(),
        runtime = runtimeInfo,
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = { navigator.pop() },
        onOpenCoreInfo = { uriHandler.openUri(SkipiCoreSourceUri) },
        onReplayOnboarding = { navigator.push(Route.Onboarding) },
        onOpenTelegram = { uriHandler.openUri(SkipiTelegramChannelUri) },
        onOpenBugReport = { uriHandler.openUri(SkipiBugReportUri) },
        onOpenSource = { uriHandler.openUri(SkipiSourceUri) },
        updatesContent = { AboutUpdatesCard() },
    )
}
