// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.proxy_editor_custom_format_json
import app.skipi.ui.resources.proxy_editor_custom_json
import app.skipi.ui.resources.proxy_editor_custom_override_inbound_dns
import app.skipi.ui.resources.proxy_editor_custom_override_inbound_dns_summary
import app.skipi.ui.resources.proxy_editor_properties
import app.skipi.ui.resources.proxy_editor_remarks
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** Common editor chrome around the host's native JSON editing widget. */
@Composable
fun SkipiCustomProxyServerEditorPresentation(
    modifier: Modifier = Modifier,
    draftIdentity: Any,
    remarks: String,
    onRemarksChange: (String) -> Unit,
    overrideInboundAndDns: Boolean,
    onOverrideChange: (Boolean) -> Unit,
    contentPadding: PaddingValues,
    formatJsonContentDescription: String,
    formatButtonBackground: Color,
    formatButtonTint: Color,
    onFormatJson: () -> Unit,
    jsonEditor: @Composable (Modifier) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val remarksState = rememberTextFieldState(initialText = remarks)
    LaunchedEffect(draftIdentity) { remarksState.setTextAndPlaceCursorAtEnd(remarks) }
    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        SmallTitle(text = stringResource(Res.string.proxy_editor_properties))
        TextField(
            label = stringResource(Res.string.proxy_editor_remarks),
            state = remarksState,
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation { onRemarksChange(asCharSequence().toString()) },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            onKeyboardAction = { focusManager.clearFocus() },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        SwitchPreference(
            title = stringResource(Res.string.proxy_editor_custom_override_inbound_dns),
            summary = stringResource(Res.string.proxy_editor_custom_override_inbound_dns_summary),
            checked = overrideInboundAndDns,
            onCheckedChange = onOverrideChange,
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
        )
        SmallTitle(text = stringResource(Res.string.proxy_editor_custom_json))
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)
                .padding(bottom = 12.dp).imePadding(),
        ) {
            jsonEditor(Modifier.fillMaxSize())
            IconButton(
                onClick = onFormatJson,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(8.dp).clip(RoundedCornerShape(12.dp)).background(formatButtonBackground),
            ) {
                Icon(imageVector = MiuixIcons.ConvertFile, contentDescription = formatJsonContentDescription, tint = formatButtonTint)
            }
        }
    }
}
