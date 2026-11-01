// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.proxy_editor_custom_format_json
import app.skipi.ui.resources.proxy_editor_custom_override_inbound_dns
import app.skipi.ui.resources.proxy_editor_custom_override_inbound_dns_summary
import app.skipi.ui.resources.proxy_editor_remarks
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * Common editor chrome around the host's native JSON editing widget.
 *
 * The name and the override switch share one compact card. While the keyboard serves the JSON text
 * the card steps aside, so the editor keeps the whole screen on a phone.
 */
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
    imeVisible: Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0,
    jsonEditor: @Composable (Modifier) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val remarksState = rememberTextFieldState(initialText = remarks)
    LaunchedEffect(draftIdentity) { remarksState.setTextAndPlaceCursorAtEnd(remarks) }
    var remarksFocused by remember { mutableStateOf(false) }
    val propertiesVisible = customEditorPropertiesVisible(imeVisible, remarksFocused)
    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        AnimatedVisibility(
            visible = propertiesVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp)) {
                TextField(
                    label = stringResource(Res.string.proxy_editor_remarks),
                    state = remarksState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = InputTransformation { onRemarksChange(asCharSequence().toString()) },
                    modifier = Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)
                        .onFocusChanged { remarksFocused = it.hasFocus },
                    onKeyboardAction = { focusManager.clearFocus() },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                SwitchPreference(
                    title = stringResource(Res.string.proxy_editor_custom_override_inbound_dns),
                    summary = stringResource(Res.string.proxy_editor_custom_override_inbound_dns_summary),
                    checked = overrideInboundAndDns,
                    onCheckedChange = onOverrideChange,
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)
                .padding(top = 8.dp, bottom = 12.dp).imePadding(),
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

/** The form above the JSON editor shows unless the keyboard is open for the JSON text itself. */
internal fun customEditorPropertiesVisible(imeVisible: Boolean, remarksFocused: Boolean): Boolean =
    !imeVisible || remarksFocused
