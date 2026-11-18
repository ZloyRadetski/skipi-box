// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.*
import features.proxy.server.model.Masque
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField

fun LazyListScope.masqueProxyServer(masqueEdit: Masque) {
    item(key = "properties") {
        SmallTitle(text = stringResource(Res.string.proxy_editor_properties))
        MasqueTextField(stringResource(Res.string.proxy_editor_remarks), masqueEdit.remarks) { masqueEdit.remarks = it }
        MasqueTextField(stringResource(Res.string.proxy_editor_server), masqueEdit.server) { masqueEdit.server = it }
        MasqueTextField(stringResource(Res.string.proxy_editor_port), masqueEdit.port, digitsOnly = true) {
            masqueEdit.port = it
        }
        MasqueTextField(stringResource(Res.string.proxy_editor_username_optional), masqueEdit.user) {
            masqueEdit.user = it
        }
        MasqueTextField(stringResource(Res.string.proxy_editor_password_optional), masqueEdit.pass) {
            masqueEdit.pass = it
        }
        MasqueTextField(stringResource(Res.string.proxy_editor_masque_host), masqueEdit.host) { masqueEdit.host = it }
        MasqueTextField(stringResource(Res.string.proxy_editor_masque_path), masqueEdit.path) { masqueEdit.path = it }
        MasqueTextField(stringResource(Res.string.proxy_editor_masque_remote_dns), masqueEdit.remoteDns) {
            masqueEdit.remoteDns = it
        }
        SmallTitle(text = stringResource(Res.string.proxy_editor_masque_warp_title))
        MasqueTextField(stringResource(Res.string.proxy_editor_masque_warp_private_key), masqueEdit.warpPrivateKey) {
            masqueEdit.warpPrivateKey = it
        }
        MasqueTextField(stringResource(Res.string.proxy_editor_masque_warp_public_key), masqueEdit.warpPublicKey) {
            masqueEdit.warpPublicKey = it
        }
        MasqueTextField(stringResource(Res.string.proxy_editor_masque_warp_address), masqueEdit.warpAddress) {
            masqueEdit.warpAddress = it
        }
        SmallTitle(text = stringResource(Res.string.proxy_editor_tls_settings))
        MasqueTextField("SNI", masqueEdit.sni) { masqueEdit.sni = it }
        MasqueTextField(stringResource(Res.string.proxy_editor_certificate_fingerprint), masqueEdit.pinSHA256) {
            masqueEdit.pinSHA256 = it
        }
    }
}

@Composable
private fun MasqueTextField(
    label: String,
    initialText: String,
    digitsOnly: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    TextField(
        label = label,
        state = rememberTextFieldState(initialText = initialText),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation {
            if (digitsOnly && !asCharSequence().isDigitsOnly()) {
                revertAllChanges()
                return@InputTransformation
            }
            onValueChange(asCharSequence().toString())
        },
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        onKeyboardAction = { focusManager.clearFocus() },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}
