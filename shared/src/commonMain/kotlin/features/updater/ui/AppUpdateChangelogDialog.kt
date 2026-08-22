// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import app.shared.res.Res
import app.shared.res.app_update_available_title
import app.shared.res.app_update_dismiss_action
import app.shared.res.app_update_install_action
import app.shared.res.app_update_size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import features.updater.AppUpdateInfo
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun AppUpdateChangelogDialog(
    show: Boolean,
    updateInfo: AppUpdateInfo?,
    onDismiss: () -> Unit,
    onInstallClick: () -> Unit,
) {
    if (!show || updateInfo == null) return

    val formattedSize = if (updateInfo.apkSizeBytes > 0) {
        String.format(Locale.US, "%.1f MB", updateInfo.apkSizeBytes / (1024f * 1024f))
    } else ""

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MiuixTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = "${stringResource(Res.string.app_update_available_title)} v${updateInfo.versionName}",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                if (formattedSize.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${stringResource(Res.string.app_update_size)}: $formattedSize",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (updateInfo.changelog.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        ) {
                            Text(
                                text = updateInfo.changelog,
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = stringResource(Res.string.app_update_dismiss_action),
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        onClick = {
                            onDismiss()
                            onInstallClick()
                        },
                    ) {
                        Text(
                            text = stringResource(Res.string.app_update_install_action),
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
