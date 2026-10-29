// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalUpdateAppState
import app.collectAppState
import app.skipi.ui.updater.SkipiAppUpdateBanner
import features.updater.AppUpdateInstaller
import features.updater.bannerModel
import features.updater.runtime.appUpdateState
import features.updater.runtime.updatingAppUpdate
import java.io.File

/** UI only observes durable state; WorkManager owns every APK transfer. */
@Composable
fun AppUpdateBanner(
    modifier: Modifier = Modifier,
) {
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val banner = appState.appUpdateState().bannerModel() ?: return
    val context = LocalContext.current
    val services = LocalAppServices.current
    val updateAppState = LocalUpdateAppState.current

    SkipiAppUpdateBanner(
        banner = banner,
        modifier = modifier,
        onDismiss = { updateAppState { it.updatingAppUpdate { dismissed() } } },
        onPrimaryAction = {
            val apkFile = appState.appUpdateApkFilePath?.let(::File)
            if (banner.isReadyToInstall && apkFile?.isFile == true) {
                AppUpdateInstaller.installApk(context, apkFile)
            } else {
                services.appUpdateDownloadCoordinator.enqueue(banner.update, automatic = false)
            }
        },
    )
}
