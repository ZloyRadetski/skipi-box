// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.updater

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
import app.skipi.ui.resources.app_update_available_title
import app.skipi.ui.resources.app_update_ready_description
import app.skipi.ui.resources.app_update_dismiss_action
import app.skipi.ui.resources.app_update_changelog_action
import app.skipi.ui.resources.app_update_downloading_action
import app.skipi.ui.resources.app_update_install_action
import androidx.compose.runtime.getValue
import features.updater.AppUpdateBannerModel
import app.skipi.ui.resources.Res
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.skipi.ui.text.themedFontWeight

/** Update notice: hosts own the transfer/install and provide the primary action. */
@Composable
fun SkipiAppUpdateBanner(
    banner: AppUpdateBannerModel,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    primaryActionLabel: String = stringResource(Res.string.app_update_install_action),
) {
    val updateInfo = banner.update
    var showChangelog by remember(updateInfo.versionCode, updateInfo.versionName) { mutableStateOf(false) }
    val isDownloading = banner.isDownloading
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
                                text = "${stringResource(Res.string.app_update_available_title)} v${updateInfo.versionName}",
                                fontSize = 15.sp,
                                fontWeight = themedFontWeight(FontWeight.Bold),
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = banner.sizeText ?: stringResource(Res.string.app_update_ready_description),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            onDismiss()
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = stringResource(Res.string.app_update_dismiss_action),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (isDownloading) {
                    val progress = banner.progress ?: 0f
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
                            text = banner.progressPercent?.let { "$it%" } ?: stringResource(Res.string.app_update_downloading_action),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                } else {
                    banner.errorMessage?.let { error ->
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
                    if (banner.hasChangelog) {
                        TextButton(
                            text = stringResource(Res.string.app_update_changelog_action),
                            onClick = { showChangelog = true },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        enabled = !isDownloading,
                        onClick = onPrimaryAction,
                    ) {
                        Text(
                            text = if (isDownloading) stringResource(Res.string.app_update_downloading_action) else primaryActionLabel,
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }

    if (showChangelog) {
        SkipiAppUpdateChangelogDialog(
            show = showChangelog,
            updateInfo = updateInfo,
            onDismiss = { showChangelog = false },
            onInstallClick = onPrimaryAction,
            actionLabel = primaryActionLabel,
        )
    }
}
