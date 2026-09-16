// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalUpdateAppState
import app.ProjectInfo
import app.R
import app.collectAppState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import features.settings.SettingsSectionCard
import features.updater.AppUpdateDownloadStatus
import features.updater.AppUpdateInfo
import features.updater.runtime.AppUpdateCheckOutcome
import features.updater.ui.AppUpdateBanner
import features.updater.ui.AppUpdateChangelogDialog
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

import androidx.compose.foundation.layout.aspectRatio

private const val SkipiProjectSourceUri = "https://github.com/ZloyRadetski/skipi-box"
internal const val SkipiBugReportUri = "https://github.com/ZloyRadetski/skipi-box/issues/new"
internal const val SkipiTelegramChannelUri = "https://t.me/skipi_public"

@Composable
internal fun AboutHeader(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 20.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AboutAppLogo()
        Spacer(Modifier.height(14.dp))
        Text(
            text = ProjectInfo.PROJECT_NAME,
            fontSize = MiuixTheme.textStyles.title2.fontSize,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Text(
            text = "v${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun AboutAppLogo(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.ic_about_logo),
        contentDescription = ProjectInfo.PROJECT_NAME,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(150.dp)
            .aspectRatio(2f)
            .clip(RoundedCornerShape(30.dp)),
    )
}

@Composable
internal fun AboutRuntimeCard(
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val xrayVersion = remember {
        runCatching { app.skipi.core.skipicore.Skipicore.coreVersion() }.getOrNull()?.ifBlank { null }
            ?: ProjectInfo.XRAY_CORE_VERSION
    }

    SmallTitle(text = stringResource(R.string.about_runtime))
    SettingsSectionCard(
        modifier = modifier,
        bottomPadding = 12.dp,
    ) {
        ArrowPreference(
            title = "SKIPI Core",
            summary = ProjectInfo.SKIPI_CORE_VERSION,
            onClick = {
                uriHandler.openUri("https://github.com/ZloyRadetski/skipi-core")
            },
        )
        BasicComponent(
            title = "Xray-core",
            summary = xrayVersion,
        )
        BasicComponent(
            title = "hev-socks5-tunnel",
            summary = ProjectInfo.HEV_SOCKS5_TUNNEL_VERSION,
        )
    }
}

@Composable
internal fun AboutLinksCard(
    title: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    SmallTitle(text = title)
    SettingsSectionCard(
        modifier = modifier,
        bottomPadding = 12.dp,
    ) {
        val navigator = app.LocalNavigator.current
        ArrowPreference(
            title = stringResource(R.string.about_replay_onboarding),
            summary = stringResource(R.string.about_replay_onboarding_summary),
            onClick = { navigator.push(app.navigation.Route.Onboarding) },
        )
        ArrowPreference(
            title = stringResource(R.string.about_telegram_channel),
            summary = "@skipi_public",
            onClick = { uriHandler.openUri(SkipiTelegramChannelUri) },
        )
        ArrowPreference(
            title = stringResource(R.string.about_bug_report),
            summary = stringResource(R.string.about_bug_report_summary),
            onClick = { uriHandler.openUri(SkipiBugReportUri) },
        )
        ArrowPreference(
            title = stringResource(R.string.about_view_skipi_source),
            onClick = { uriHandler.openUri(SkipiProjectSourceUri) },
        )
    }
}

@Composable
internal fun AboutUpdatesCard(
    modifier: Modifier = Modifier,
) {
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val tipNotifier = services.tipNotifier
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var updateInfoToShow by remember { mutableStateOf<AppUpdateInfo?>(null) }
    val checkingText = stringResource(R.string.app_update_checking)
    val latestText = stringResource(R.string.app_update_already_latest)
    val checkFailedText = stringResource(R.string.app_update_check_failed)

    AppUpdateBanner(modifier = modifier.padding(bottom = 8.dp))

    SmallTitle(text = stringResource(R.string.settings_updates_title))
    SettingsSectionCard(
        modifier = modifier,
        bottomPadding = 12.dp,
    ) {
        SwitchPreference(
            title = stringResource(R.string.settings_auto_check_updates_title),
            summary = stringResource(R.string.settings_auto_check_updates_summary),
            checked = appState.autoCheckAppUpdates,
            onCheckedChange = { checked ->
                updateAppState { it.copy(autoCheckAppUpdates = checked) }
            },
        )
        if (appState.autoCheckAppUpdates) {
            SwitchPreference(
                title = stringResource(R.string.settings_auto_install_night_title),
                summary = stringResource(R.string.settings_auto_install_night_summary),
                checked = appState.autoInstallAppUpdatesAtNight,
                onCheckedChange = { checked ->
                    updateAppState { it.copy(autoInstallAppUpdatesAtNight = checked) }
                },
            )
        }
        ArrowPreference(
            title = stringResource(R.string.settings_check_updates_now_action),
            summary = when {
                isChecking -> checkingText
                appState.appUpdateDownloadStatus in setOf(
                    AppUpdateDownloadStatus.QUEUED,
                    AppUpdateDownloadStatus.DOWNLOADING,
                ) -> {
                    val p = if (appState.appUpdateTotalBytes > 0L) {
                        appState.appUpdateDownloadedBytes.toFloat() / appState.appUpdateTotalBytes.toFloat()
                    } else {
                        0f
                    }
                    "${stringResource(R.string.app_update_downloading_action)} ${(p * 100).toInt()}%"
                }
                appState.availableAppUpdate != null -> {
                    "${stringResource(R.string.app_update_available_title)} v${appState.availableAppUpdate?.versionName}"
                }
                else -> null
            },
            onClick = {
                if (isChecking) return@ArrowPreference
                isChecking = true
                scope.launch {
                    when (val outcome = services.appUpdateCheckCoordinator.checkLatestRelease()) {
                        is AppUpdateCheckOutcome.UpdateAvailable -> updateInfoToShow = outcome.update
                        AppUpdateCheckOutcome.UpToDate -> tipNotifier.show(latestText)
                        AppUpdateCheckOutcome.NotModified -> {
                            val knownUpdate = stateStore.currentState.availableAppUpdate
                            if (knownUpdate != null) {
                                updateInfoToShow = knownUpdate
                            } else {
                                tipNotifier.show(latestText)
                            }
                        }
                        AppUpdateCheckOutcome.Failed -> tipNotifier.show(checkFailedText)
                    }
                    isChecking = false
                }
            },
        )
    }

    if (updateInfoToShow != null) {
        AppUpdateChangelogDialog(
            show = updateInfoToShow != null,
            updateInfo = updateInfoToShow,
            onDismiss = { updateInfoToShow = null },
            onInstallClick = {
                val update = updateInfoToShow
                updateInfoToShow = null
                if (update != null) {
                    services.appUpdateDownloadCoordinator.enqueue(update, automatic = false)
                }
            },
        )
    }
}
