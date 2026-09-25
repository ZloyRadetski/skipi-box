// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_add
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.proxy_server_list_add
import app.skipi.ui.resources.proxy_server_list_import_clipboard
import app.skipi.ui.resources.proxy_server_list_import_file
import app.skipi.ui.resources.subscription_add
import app.skipi.ui.resources.subscription_invalid_url
import app.skipi.ui.resources.subscription_url
import app.skipi.ui.text.themedFontWeight
import features.proxy.server.model.ProxyServer
import features.subscription.isValidManualSubscriptionUrl
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class SkipiAddSourceMode {
    Server,
    Subscription,
}

@Composable
fun SkipiAddSourceDialog(
    show: Boolean,
    mode: SkipiAddSourceMode,
    serverLink: String,
    subscriptionUrl: String,
    isEditingServer: Boolean = false,
    isSubmitting: Boolean = false,
    onModeChange: (SkipiAddSourceMode) -> Unit,
    onServerLinkChange: (String) -> Unit,
    onSubscriptionUrlChange: (String) -> Unit,
    onSaveServer: (ProxyServer<*>) -> Unit,
    onSaveSubscription: (String) -> Unit,
    onClipboardImport: (() -> Unit)? = null,
    onFileImport: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val parsedServer = remember(serverLink) {
        serverLink.trim().takeIf(String::isNotEmpty)?.let { runCatching { ProxyServer.parse(it) } }
    }
    val subscriptionUrlValid = remember(subscriptionUrl) {
        subscriptionUrl.isValidManualSubscriptionUrl()
    }

    val dialogTitle = when {
        mode == SkipiAddSourceMode.Subscription -> stringResource(Res.string.subscription_add)
        isEditingServer -> stringResource(Res.string.common_save)
        else -> stringResource(Res.string.proxy_server_list_add)
    }

    AppWindowDialog(
        show = show,
        title = dialogTitle,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.secondaryContainer)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val serverSelected = mode == SkipiAddSourceMode.Server
                Button(
                    onClick = { onModeChange(SkipiAddSourceMode.Server) },
                    modifier = Modifier.weight(1f),
                    colors = if (serverSelected) {
                        ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        )
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.proxy_server_list_add),
                        fontWeight = themedFontWeight(
                            if (serverSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                }

                val subSelected = mode == SkipiAddSourceMode.Subscription
                Button(
                    onClick = { onModeChange(SkipiAddSourceMode.Subscription) },
                    modifier = Modifier.weight(1f),
                    colors = if (subSelected) {
                        ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        )
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.subscription_add),
                        fontWeight = themedFontWeight(
                            if (subSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                }
            }

            if (mode == SkipiAddSourceMode.Server) {
                TextField(
                    value = serverLink,
                    onValueChange = onServerLinkChange,
                    label = "vless://, vmess://, hysteria2://, trojan://, ss:// …",
                    modifier = Modifier.fillMaxWidth(),
                )

                when {
                    parsedServer == null -> {
                        Text(
                            text = "Paste a server share link above.",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                        )
                    }
                    parsedServer.isFailure -> {
                        Text(
                            text = parsedServer.exceptionOrNull()?.message ?: "Invalid server configuration",
                            color = MiuixTheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                    }
                    else -> {
                        val info = parsedServer.getOrThrow().getInfo()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = info.remarks.ifBlank { "Proxy Server" },
                                    fontWeight = themedFontWeight(FontWeight.SemiBold),
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${info.protocol.uppercase()} · ${info.address}",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            } else {
                TextField(
                    value = subscriptionUrl,
                    onValueChange = onSubscriptionUrlChange,
                    label = stringResource(Res.string.subscription_url),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                )

                if (subscriptionUrl.isNotBlank() && !subscriptionUrlValid) {
                    Text(
                        text = stringResource(Res.string.subscription_invalid_url),
                        color = MiuixTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
            }

            // Quick actions (clipboard / file)
            if (onClipboardImport != null || onFileImport != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onClipboardImport != null) {
                        TextButton(
                            text = stringResource(Res.string.proxy_server_list_import_clipboard),
                            onClick = onClipboardImport,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (onFileImport != null) {
                        TextButton(
                            text = stringResource(Res.string.proxy_server_list_import_file),
                            onClick = onFileImport,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Bottom Confirm/Cancel Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(Res.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (mode == SkipiAddSourceMode.Server) {
                            parsedServer?.getOrNull()?.let { onSaveServer(it) }
                        } else {
                            if (subscriptionUrlValid) {
                                onSaveSubscription(subscriptionUrl.trim())
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSubmitting && when (mode) {
                        SkipiAddSourceMode.Server -> parsedServer?.isSuccess == true
                        SkipiAddSourceMode.Subscription -> subscriptionUrlValid
                    },
                ) {
                    Text(
                        text = if (isEditingServer) {
                            stringResource(Res.string.common_save)
                        } else {
                            stringResource(Res.string.common_add)
                        },
                    )
                }
            }
        }
    }
}
