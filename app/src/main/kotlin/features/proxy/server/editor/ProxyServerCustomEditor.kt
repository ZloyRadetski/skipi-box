// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.R
import app.skipi.ui.server.editor.SkipiCustomProxyServerEditorPresentation
import features.proxy.server.model.Custom
import features.proxy.server.model.formatCustomXrayConfigJson
import kotlinx.coroutines.launch

/** Android adapter keeps the native code editor and its structured text state. */
@Composable
internal fun CustomProxyServerEditor(
    customEdit: Custom,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val focusManager = LocalFocusManager.current
    val tipNotifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()
    val invalidJsonMessage = stringResource(R.string.proxy_editor_custom_json_invalid)
    val formatJsonContentDescription = stringResource(R.string.proxy_editor_custom_format_json)
    val jsonEditorColors = rememberJsonEditorColors()
    var remarks by remember(customEdit) { mutableStateOf(customEdit.remarks) }
    var overrideInboundAndDns by remember(customEdit) { mutableStateOf(customEdit.overrideInboundAndDns) }
    val configJsonState = remember(customEdit) { JsonCodeEditorState(customEdit.configJson) }

    LaunchedEffect(customEdit) {
        remarks = customEdit.remarks
        overrideInboundAndDns = customEdit.overrideInboundAndDns
    }
    LaunchedEffect(configJsonState.documentVersion) {
        customEdit.configJson = configJsonState.snapshotText()
    }

    SkipiCustomProxyServerEditorPresentation(
        modifier = modifier,
        draftIdentity = customEdit,
        remarks = remarks,
        onRemarksChange = {
            remarks = it
            customEdit.remarks = it
        },
        overrideInboundAndDns = overrideInboundAndDns,
        onOverrideChange = {
            overrideInboundAndDns = it
            customEdit.overrideInboundAndDns = it
        },
        contentPadding = contentPadding,
        formatJsonContentDescription = formatJsonContentDescription,
        formatButtonBackground = jsonEditorColors.formatButtonBackground,
        formatButtonTint = jsonEditorColors.accent,
        onFormatJson = {
            runCatching { formatCustomXrayConfigJson(configJsonState.snapshotText()) }
                .onSuccess { formatted ->
                    configJsonState.replaceText(formatted)
                    customEdit.configJson = formatted
                    focusManager.clearFocus()
                }
                .onFailure { scope.launch { tipNotifier.show(invalidJsonMessage) } }
        },
        jsonEditor = { editorModifier ->
            JsonCodeEditor(
                label = stringResource(R.string.proxy_editor_custom_json),
                state = configJsonState,
                modifier = editorModifier,
            )
        },
    )
}
