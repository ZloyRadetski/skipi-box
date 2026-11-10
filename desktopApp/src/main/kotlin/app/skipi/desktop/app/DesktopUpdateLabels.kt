// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.app_update_download_action
import app.skipi.ui.resources.app_update_open_installer_action
import app.skipi.ui.resources.app_update_open_page_action
import app.skipi.ui.resources.app_update_restart_action
import features.updater.AppUpdateBannerModel
import features.updater.AppUpdateInfo
import features.updater.DesktopUpdatePackage
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the update button does next: open the page, download, or finish the install. */
internal fun desktopUpdateActionResource(
    update: AppUpdateInfo,
    readyToInstall: Boolean,
    installedPackage: DesktopUpdatePackage?,
): StringResource = when {
    !update.canDownload -> Res.string.app_update_open_page_action
    !readyToInstall -> Res.string.app_update_download_action
    installedPackage?.isInstalledBySystem() == true -> Res.string.app_update_open_installer_action
    else -> Res.string.app_update_restart_action
}

@Composable
internal fun desktopUpdateActionLabel(banner: AppUpdateBannerModel, installedPackage: DesktopUpdatePackage?): String =
    stringResource(desktopUpdateActionResource(banner.update, banner.isReadyToInstall, installedPackage))

@Composable
internal fun desktopUpdateActionLabel(update: AppUpdateInfo, readyToInstall: Boolean, installedPackage: DesktopUpdatePackage?): String =
    stringResource(desktopUpdateActionResource(update, readyToInstall, installedPackage))
