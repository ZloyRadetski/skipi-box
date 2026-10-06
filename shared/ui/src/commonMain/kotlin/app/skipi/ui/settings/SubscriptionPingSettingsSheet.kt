// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.runtime.Composable
import app.skipi.ui.components.AppWindowBottomSheet
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import features.subscription.SubscriptionPingSettingsRules

@Composable
fun SubscriptionPingSettingsBottomSheet(
    show: Boolean,
    url: String,
    timeoutMillis: String,
    onUrlChange: (String) -> Unit,
    onTimeoutMillisChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val urlError = if (SubscriptionPingSettingsRules.isValidHttpUrl(url)) null else {
        stringResource(Res.string.subscription_ping_url_invalid)
    }
    val timeoutError = if (SubscriptionPingSettingsRules.isValidTimeoutMillis(timeoutMillis)) null else {
        stringResource(Res.string.subscription_ping_timeout_invalid)
    }
    val canSave = urlError == null && timeoutError == null

    AppWindowBottomSheet(
        show = show,
        title = stringResource(Res.string.subscription_ping_settings),
        startAction = {
            TextButton(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(Res.string.common_save),
                onClick = {
                    if (canSave) onSave(url.trim(), timeoutMillis.trim())
                },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        key(show) {
            SettingsSheetContent {
                SettingsTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = stringResource(Res.string.subscription_ping_url),
                    errorText = urlError,
                )
                Text(
                    text = stringResource(Res.string.subscription_ping_timeout),
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                SettingsTextField(
                    value = timeoutMillis,
                    onValueChange = onTimeoutMillisChange,
                    label = stringResource(Res.string.subscription_ping_timeout_ms),
                    errorText = timeoutError,
                    keyboardOptions = fiveDigitKeyboardOptions(),
                    sanitizeInput = ::sanitizeFiveDigitInput,
                )
            }
        }
    }
}
