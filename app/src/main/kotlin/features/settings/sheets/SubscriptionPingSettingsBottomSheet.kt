// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings.sheets

import androidx.compose.runtime.Composable
import ui.components.AppWindowBottomSheet
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import features.subscription.SubscriptionPingSettingsRules

@Composable
internal fun SubscriptionPingSettingsBottomSheet(
    show: Boolean,
    url: String,
    timeoutMillis: String,
    onUrlChange: (String) -> Unit,
    onTimeoutMillisChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val urlError = if (SubscriptionPingSettingsRules.isValidHttpUrl(url)) null else {
        stringResource(R.string.subscription_ping_url_invalid)
    }
    val timeoutError = if (SubscriptionPingSettingsRules.isValidTimeoutMillis(timeoutMillis)) null else {
        stringResource(R.string.subscription_ping_timeout_invalid)
    }
    val canSave = urlError == null && timeoutError == null

    AppWindowBottomSheet(
        show = show,
        title = stringResource(R.string.subscription_ping_settings),
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
                    label = stringResource(R.string.subscription_ping_url),
                    errorText = urlError,
                )
                Text(
                    text = stringResource(R.string.subscription_ping_timeout),
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                SettingsTextField(
                    value = timeoutMillis,
                    onValueChange = onTimeoutMillisChange,
                    label = stringResource(R.string.subscription_ping_timeout_ms),
                    errorText = timeoutError,
                    keyboardOptions = fiveDigitKeyboardOptions(),
                    sanitizeInput = ::sanitizeFiveDigitInput,
                )
            }
        }
    }
}
