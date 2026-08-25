// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.layout.Column
import ui.components.AppWindowDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/** Graphical editor for standard [General] properties which SKIPI can apply. */
@Composable
internal fun TrafficConfigGeneralEditor(
    show: Boolean,
    rawConfig: String,
    onRawConfigChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!show) return
    val general = remember(rawConfig) { rawConfig.analyzeShadowrocketConfig().general }
    var dnsServer by remember(rawConfig, show) { mutableStateOf(general["dns-server"] ?: "system") }
    var ipv6 by remember(rawConfig, show) { mutableStateOf(general["ipv6"].toConfigEditorBoolean()) }
    var preferIpv6 by remember(rawConfig, show) { mutableStateOf(general["prefer-ipv6"].toConfigEditorBoolean()) }

    AppWindowDialog(
        show = true,
        title = stringResource(R.string.configs_general_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                state = rememberTextFieldState(initialText = dnsServer),
                inputTransformation = { dnsServer = asCharSequence().toString() },
                label = stringResource(R.string.configs_dns_servers),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SwitchPreference(
                title = stringResource(R.string.configs_ipv6),
                checked = ipv6,
                onCheckedChange = { ipv6 = it },
            )
            SwitchPreference(
                title = stringResource(R.string.configs_ipv6_prefer),
                enabled = ipv6,
                checked = preferIpv6,
                onCheckedChange = { preferIpv6 = it },
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(text = stringResource(R.string.common_cancel), onClick = onDismissRequest)
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.common_save),
                    enabled = dnsServer.isNotBlank(),
                    onClick = {
                        var updated = rawConfig.withShadowrocketGeneralValue("dns-server", dnsServer.trim())
                        updated = updated.withShadowrocketGeneralValue("ipv6", ipv6.toString())
                        updated = updated.withShadowrocketGeneralValue("prefer-ipv6", preferIpv6.toString())
                        onRawConfigChange(updated)
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

private fun String?.toConfigEditorBoolean(): Boolean {
    return this?.trim()?.lowercase() in setOf("true", "yes", "1")
}
