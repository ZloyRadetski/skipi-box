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
import features.proxy.server.model.AmneziaWg
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField

internal fun LazyListScope.amneziaWgProxyServer(awgEdit: AmneziaWg) {
    item(key = "properties") {
        val focusManager = LocalFocusManager.current
        SmallTitle(text = stringResource(R.string.proxy_editor_properties))
        TextField(
            label = stringResource(R.string.proxy_editor_remarks),
            state = rememberTextFieldState(initialText = awgEdit.remarks),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.remarks = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_server),
            state = rememberTextFieldState(initialText = awgEdit.server),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.server = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_port),
            state = rememberTextFieldState(initialText = awgEdit.port),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                awgEdit.port = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = "SecretKey",
            state = rememberTextFieldState(initialText = awgEdit.secretKey),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.secretKey = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = "PublicKey",
            state = rememberTextFieldState(initialText = awgEdit.publicKey),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.publicKey = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_preshared_key_optional),
            state = rememberTextFieldState(initialText = awgEdit.preSharedKey),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.preSharedKey = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_reserved_optional),
            state = rememberTextFieldState(initialText = awgEdit.reserved),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.reserved = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_local_address_optional),
            state = rememberTextFieldState(initialText = awgEdit.address),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.address = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_mtu_optional),
            state = rememberTextFieldState(initialText = awgEdit.mtu),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                awgEdit.mtu = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }

    item(key = "obfuscation") {
        val focusManager = LocalFocusManager.current
        SmallTitle(text = stringResource(R.string.proxy_editor_amnezia_obfuscation))
        TextField(
            label = stringResource(R.string.proxy_editor_awg_jc),
            state = rememberTextFieldState(initialText = awgEdit.jc),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                awgEdit.jc = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_jmin),
            state = rememberTextFieldState(initialText = awgEdit.jmin),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                awgEdit.jmin = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_jmax),
            state = rememberTextFieldState(initialText = awgEdit.jmax),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                awgEdit.jmax = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_s1),
            state = rememberTextFieldState(initialText = awgEdit.s1),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                awgEdit.s1 = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_s2),
            state = rememberTextFieldState(initialText = awgEdit.s2),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                awgEdit.s2 = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_s3),
            state = rememberTextFieldState(initialText = awgEdit.s3),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                awgEdit.s3 = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_s4),
            state = rememberTextFieldState(initialText = awgEdit.s4),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                if (!asCharSequence().isDigitsOnly()) {
                    revertAllChanges()
                    return@InputTransformation
                }
                awgEdit.s4 = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_h1),
            state = rememberTextFieldState(initialText = awgEdit.h1),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.h1 = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_h2),
            state = rememberTextFieldState(initialText = awgEdit.h2),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.h2 = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_h3),
            state = rememberTextFieldState(initialText = awgEdit.h3),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.h3 = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_awg_h4),
            state = rememberTextFieldState(initialText = awgEdit.h4),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                awgEdit.h4 = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextField(
            label = stringResource(R.string.proxy_editor_final_mask),
            state = rememberTextFieldState(initialText = awgEdit.finalMask),
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 5, maxHeightInLines = 20),
            inputTransformation = InputTransformation {
                awgEdit.finalMask = asCharSequence().toString()
            },
            modifier = Modifier.padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}
