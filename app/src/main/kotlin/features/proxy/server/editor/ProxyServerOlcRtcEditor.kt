// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.R
import features.proxy.server.model.OlcRtc
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField

internal fun LazyListScope.olcRtcProxyServer(olcEdit: OlcRtc) {
    item(key = "properties") {
        val focusManager = LocalFocusManager.current
        SmallTitle(text = stringResource(R.string.proxy_editor_properties))
        TextField(
            label = stringResource(R.string.proxy_editor_remarks),
            state = rememberTextFieldState(initialText = olcEdit.remarks),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                olcEdit.remarks = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_olcrtc_provider),
            state = rememberTextFieldState(initialText = olcEdit.provider),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                olcEdit.provider = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_olcrtc_transport),
            state = rememberTextFieldState(initialText = olcEdit.transport),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                olcEdit.transport = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_olcrtc_room_url),
            state = rememberTextFieldState(initialText = olcEdit.roomUrl),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                olcEdit.roomUrl = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_olcrtc_encryption_key),
            state = rememberTextFieldState(initialText = olcEdit.encryptionKey),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                olcEdit.encryptionKey = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_olcrtc_payload_optional),
            state = rememberTextFieldState(initialText = olcEdit.payload),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                olcEdit.payload = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_olcrtc_socks_port),
            state = rememberTextFieldState(initialText = olcEdit.localSocksPort),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                olcEdit.localSocksPort = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}
