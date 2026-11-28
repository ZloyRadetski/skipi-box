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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.*
import features.proxy.server.model.NaiveProxy
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference

fun LazyListScope.naiveProxyServer(naiveEdit: NaiveProxy) {
    item(key = "properties") {
        SmallTitle(text = stringResource(Res.string.proxy_editor_properties))
        NaiveTextField(stringResource(Res.string.proxy_editor_remarks), naiveEdit.remarks) { naiveEdit.remarks = it }
        NaiveTextField(stringResource(Res.string.proxy_editor_server), naiveEdit.server) { naiveEdit.server = it }
        NaiveTextField(stringResource(Res.string.proxy_editor_port), naiveEdit.port, digitsOnly = true) {
            naiveEdit.port = it
        }
        NaiveTextField(stringResource(Res.string.proxy_editor_username_optional), naiveEdit.user) {
            naiveEdit.user = it
        }
        NaiveTextField(stringResource(Res.string.proxy_editor_password_optional), naiveEdit.pass) {
            naiveEdit.pass = it
        }
        var quic by remember { mutableStateOf(naiveEdit.quic) }
        SwitchPreference(
            title = stringResource(Res.string.proxy_editor_naive_quic),
            summary = stringResource(Res.string.proxy_editor_naive_quic_summary),
            checked = quic,
            onCheckedChange = {
                quic = it
                naiveEdit.quic = it
            },
        )
        NaiveTextField(
            stringResource(Res.string.proxy_editor_naive_concurrency),
            naiveEdit.concurrency,
            digitsOnly = true,
        ) { naiveEdit.concurrency = it }
        NaiveTextField(stringResource(Res.string.proxy_editor_masque_headers), naiveEdit.headers, multiLine = true) {
            naiveEdit.headers = it
        }
        SmallTitle(text = stringResource(Res.string.proxy_editor_tls_settings))
        NaiveTextField("SNI", naiveEdit.sni) { naiveEdit.sni = it }
    }
}

@Composable
private fun NaiveTextField(
    label: String,
    initialText: String,
    digitsOnly: Boolean = false,
    multiLine: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    TextField(
        label = label,
        state = rememberTextFieldState(initialText = initialText),
        lineLimits = if (multiLine) {
            TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 12)
        } else {
            TextFieldLineLimits.SingleLine
        },
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
