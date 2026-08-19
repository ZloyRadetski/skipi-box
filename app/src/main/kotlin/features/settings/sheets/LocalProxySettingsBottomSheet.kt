// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun LocalProxySettingsBottomSheet(
    show: Boolean,
    port: String,
    enableDynamicPort: Boolean,
    listenAllInterfaces: Boolean,
    enableAuth: Boolean,
    username: String,
    password: String,
    onPortChange: (String) -> Unit,
    onEnableDynamicPortChange: (Boolean) -> Unit,
    onListenAllInterfacesChange: (Boolean) -> Unit,
    onEnableAuthChange: (Boolean) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (String, Boolean, Boolean, Boolean, String, String) -> Unit,
) {
    val portError = if (isPort(port)) null else stringResource(R.string.settings_local_proxy_port_invalid)
    var isPasswordVisible by remember { mutableStateOf(false) }

    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.settings_local_proxy),
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    if (portError == null) {
                        onSave(
                            port.trim(),
                            enableDynamicPort,
                            listenAllInterfaces,
                            enableAuth,
                            username.trim(),
                            password,
                        )
                    }
                },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        key(show) {
            SettingsSheetContent {
                SettingsTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = stringResource(R.string.settings_local_proxy_port),
                    errorText = portError,
                    keyboardOptions = fiveDigitKeyboardOptions(),
                    sanitizeInput = ::sanitizeFiveDigitInput,
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_local_proxy_dynamic_port),
                    summary = stringResource(R.string.settings_local_proxy_dynamic_port_summary),
                    checked = enableDynamicPort,
                    onCheckedChange = onEnableDynamicPortChange,
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_local_proxy_listen_all_interfaces),
                    summary = stringResource(R.string.settings_local_proxy_listen_all_interfaces_summary),
                    checked = listenAllInterfaces,
                    onCheckedChange = onListenAllInterfacesChange,
                )
                SwitchPreference(
                    title = stringResource(R.string.settings_local_proxy_enable_auth),
                    summary = stringResource(R.string.settings_local_proxy_enable_auth_summary),
                    checked = enableAuth,
                    onCheckedChange = onEnableAuthChange,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (enableAuth) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Учётные данные",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        TextButton(
                            text = "Сгенерировать",
                            onClick = {
                                onUsernameChange(app.generateRandomProxyCredential())
                                onPasswordChange(app.generateRandomProxyCredential())
                            },
                        )
                    }

                    SettingsTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = stringResource(R.string.settings_local_proxy_username),
                        errorText = null,
                    )

                    if (isPasswordVisible) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsTextField(
                                value = password,
                                onValueChange = onPasswordChange,
                                label = stringResource(R.string.settings_local_proxy_password),
                                errorText = null,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    text = "Скрыть пароль",
                                    onClick = { isPasswordVisible = false }
                                )
                            }
                        }
                    } else {
                        // Masked password row - click to reveal and edit
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .clickable { isPasswordVisible = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_local_proxy_password),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "••••••••",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                            TextButton(
                                text = "Показать",
                                onClick = { isPasswordVisible = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
