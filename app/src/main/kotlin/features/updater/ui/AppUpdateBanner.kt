// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.ui

import androidx.compose.animation.AnimatedVisibility
import ui.text.themedFontWeight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalUpdateAppState
import app.R
import features.updater.AppUpdateDownloader
import features.updater.AppUpdateDownloadProgress
import features.updater.AppUpdateInfo
import features.updater.AppUpdateInstaller
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
import java.io.File
import java.util.Locale

@Composable
fun AppUpdateBanner(
    updateInfo: AppUpdateInfo?,
    dismissedVersion: String,
    modifier: Modifier = Modifier,
) {
    if (updateInfo == null || updateInfo.versionName == dismissedVersion) return

    val context = LocalContext.current
    val updateAppState = LocalUpdateAppState.current
    val scope = rememberCoroutineScope()
    var showChangelog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<AppUpdateDownloadProgress>(AppUpdateDownloadProgress.Idle) }

    fun startDownloadAndInstall() {
        scope.launch {
            val downloader = AppUpdateDownloader(context)
            downloader.downloadApk(updateInfo).collectLatest { progress ->
                downloadProgress = progress
                if (progress is AppUpdateDownloadProgress.Completed) {
                    val apkFile = File(progress.apkFilePath)
                    AppUpdateInstaller.installApk(context, apkFile)
                }
            }
        }
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
                            } else ""
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

                // Download Progress Indicator
                when (val progress = downloadProgress) {
                    is AppUpdateDownloadProgress.Downloading -> {
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
                                        .fillMaxWidth(progress.progress)
                                        .fillMaxHeight()
                                        .background(MiuixTheme.colorScheme.primary),
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${(progress.progress * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                    is AppUpdateDownloadProgress.Failed -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = progress.errorMessage,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                    else -> {}
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

                    val isDownloading = downloadProgress is AppUpdateDownloadProgress.Downloading
                    Button(
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        enabled = !isDownloading,
                        onClick = { startDownloadAndInstall() },
                    ) {
                        Text(
                            text = if (isDownloading) stringResource(R.string.app_update_downloading_action) else stringResource(R.string.app_update_install_action),
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
            onInstallClick = { startDownloadAndInstall() },
        )
    }
}
