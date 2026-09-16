// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import features.updater.AppUpdateDownloadStatus
import features.updater.AppUpdateInstaller
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.text.themedFontWeight
import java.io.File
import java.util.Locale

/** UI only observes durable state; WorkManager owns every APK transfer. */
@Composable
fun AppUpdateBanner(
    modifier: Modifier = Modifier,
) {
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateInfo = appState.availableAppUpdate ?: return
    if (updateInfo.versionName == appState.dismissedUpdateVersion) return

    val context = LocalContext.current
    val services = LocalAppServices.current
    val updateAppState = LocalUpdateAppState.current
    var showChangelog by remember(updateInfo.versionCode, updateInfo.versionName) { mutableStateOf(false) }
    val status = appState.appUpdateDownloadStatus
    val isDownloading = status == AppUpdateDownloadStatus.QUEUED || status == AppUpdateDownloadStatus.DOWNLOADING
    val isReadyToInstall = status == AppUpdateDownloadStatus.READY_TO_INSTALL

    fun startOrInstall() {
        if (isReadyToInstall) {
            val apkFile = appState.appUpdateApkFilePath?.let(::File)
            if (apkFile?.isFile == true) {
                AppUpdateInstaller.installApk(context, apkFile)
                return
            }
        }
        services.appUpdateDownloadCoordinator.enqueue(updateInfo, automatic = false)
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "${stringResource(R.string.app_update_available_title)} v${updateInfo.versionName}",
                                fontSize = 15.sp,
                                fontWeight = themedFontWeight(FontWeight.Bold),
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            val sizeText = if (updateInfo.apkSizeBytes > 0) {
                                String.format(Locale.US, "%.1f MB", updateInfo.apkSizeBytes / (1024f * 1024f))
                            } else {
                                ""
                            }
                            Text(
                                text = if (sizeText.isNotBlank()) sizeText else stringResource(R.string.app_update_ready_description),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            updateAppState { it.copy(dismissedUpdateVersion = updateInfo.versionName) }
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = stringResource(R.string.app_update_dismiss_action),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (isDownloading) {
                    val total = appState.appUpdateTotalBytes
                    val progress = if (total > 0L) {
                        (appState.appUpdateDownloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(MiuixTheme.colorScheme.primary),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (total > 0L) "${(progress * 100).toInt()}%" else stringResource(R.string.app_update_downloading_action),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                } else if (status == AppUpdateDownloadStatus.FAILED) {
                    appState.appUpdateDownloadError?.takeIf(String::isNotBlank)?.let { error ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (updateInfo.changelog.isNotBlank()) {
                        TextButton(
                            text = stringResource(R.string.app_update_changelog_action),
                            onClick = { showChangelog = true },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        enabled = !isDownloading,
                        onClick = ::startOrInstall,
                    ) {
                        Text(
                            text = when {
                                isDownloading -> stringResource(R.string.app_update_downloading_action)
                                isReadyToInstall -> stringResource(R.string.app_update_install_action)
                                else -> stringResource(R.string.app_update_install_action)
                            },
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }

    if (showChangelog) {
        AppUpdateChangelogDialog(
            show = showChangelog,
            updateInfo = updateInfo,
            onDismiss = { showChangelog = false },
            onInstallClick = ::startOrInstall,
        )
    }
}
