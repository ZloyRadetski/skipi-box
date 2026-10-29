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
import app.skipi.ui.updater.SkipiAppUpdateChangelogDialog
import app.skipi.ui.updater.SkipiAppUpdatesSettingsCard
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

import androidx.compose.foundation.layout.aspectRatio

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

    SkipiAppUpdatesSettingsCard(
        modifier = modifier,
        autoCheckEnabled = appState.autoCheckAppUpdates,
        onAutoCheckChange = { checked -> updateAppState { it.copy(autoCheckAppUpdates = checked) } },
        nightInstallEnabled = appState.autoInstallAppUpdatesAtNight,
        onNightInstallChange = { checked -> updateAppState { it.copy(autoInstallAppUpdatesAtNight = checked) } },
        checkSummary = when {
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
        onCheckNow = {
            if (isChecking) return@SkipiAppUpdatesSettingsCard
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

    if (updateInfoToShow != null) {
        SkipiAppUpdateChangelogDialog(
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
