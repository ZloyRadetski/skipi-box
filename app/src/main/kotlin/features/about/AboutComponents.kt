// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalUpdateAppState
import app.ProjectInfo
import app.R
import app.collectAppState
import androidx.compose.ui.res.stringResource
import features.settings.SettingsSectionCard
import features.updater.AppUpdateDownloadStatus
import features.updater.AppUpdateInfo
import features.updater.runtime.AppUpdateCheckOutcome
import features.updater.ui.AppUpdateBanner
import features.updater.ui.AppUpdateChangelogDialog
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

import androidx.compose.foundation.layout.aspectRatio

internal const val SkipiBugReportUri = "https://github.com/ZloyRadetski/skipi-box/issues/new"
internal const val SkipiTelegramChannelUri = "https://t.me/skipi_public"

@Composable
internal fun AboutAppLogo(
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
