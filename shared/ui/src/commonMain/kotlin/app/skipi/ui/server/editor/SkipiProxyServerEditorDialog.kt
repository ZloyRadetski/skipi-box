// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.resources.*
import app.skipi.ui.server.validation.rememberProxyServerValidationMessageResolver
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.formatCustomXrayConfigJson
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SkipiProxyServerEditorDialog(
    show: Boolean,
    server: ProxyServer<*>,
    options: ProxyServerEditorOptions = ProxyServerEditorOptions(),
    onSave: (ProxyServer<*>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val serverEdit = remember(server) { server.editableCopy() }
    val title = serverEdit.editorTitle()
    val validationMessageOf = rememberProxyServerValidationMessageResolver()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun requestSave() {
        val basicIssues = serverEdit.validateBasic()
        if (basicIssues.isNotEmpty()) {
            errorMessage = validationMessageOf(basicIssues.first())
            return
        }
        val fullIssues = serverEdit.validateFull()
        if (fullIssues.isNotEmpty()) {
            if (serverEdit is AmneziaWg) {
                errorMessage = validationMessageOf(fullIssues.first())
                return
            }
        }
        errorMessage = null
        onSave(serverEdit)
    }

    AppWindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            if (serverEdit is Custom) {
                CustomServerEditorFields(
                    custom = serverEdit,
                    onError = { errorMessage = it },
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                ) {
                    proxyServerEditorContent(serverEdit, options)
                }
            }

            errorMessage?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = stringResource(Res.string.common_cancel),
                    onClick = onDismiss,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = ::requestSave,
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(stringResource(Res.string.common_save))
                }
            }
        }
    }
}

@Composable
private fun CustomServerEditorFields(
    custom: Custom,
    onError: (String) -> Unit,
) {
    var remarks by remember(custom) { mutableStateOf(custom.remarks) }
    var overrideInboundAndDns by remember(custom) { mutableStateOf(custom.overrideInboundAndDns) }
    var configJson by remember(custom) { mutableStateOf(custom.configJson) }
    val invalidJsonMsg = stringResource(Res.string.proxy_editor_custom_json_invalid)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SmallTitle(text = stringResource(Res.string.proxy_editor_properties))
        TextField(
            label = stringResource(Res.string.proxy_editor_remarks),
            value = remarks,
            onValueChange = {
                remarks = it
                custom.remarks = it
            },
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchPreference(
            title = stringResource(Res.string.proxy_editor_custom_override_inbound_dns),
            summary = stringResource(Res.string.proxy_editor_custom_override_inbound_dns_summary),
            checked = overrideInboundAndDns,
            onCheckedChange = {
                overrideInboundAndDns = it
                custom.overrideInboundAndDns = it
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(text = stringResource(Res.string.proxy_editor_custom_json))
            TextButton(
                text = stringResource(Res.string.proxy_editor_custom_format_json),
                onClick = {
                    runCatching {
                        val formatted = formatCustomXrayConfigJson(configJson)
                        configJson = formatted
                        custom.configJson = formatted
                    }.onFailure {
                        onError(invalidJsonMsg)
                    }
                },
            )
        }
        TextField(
            label = stringResource(Res.string.proxy_editor_custom_json),
            value = configJson,
            onValueChange = {
                configJson = it
                custom.configJson = it
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
        )
    }
}
