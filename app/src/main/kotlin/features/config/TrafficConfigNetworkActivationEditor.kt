// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.layout.Column
import ui.components.AppWindowDialog
import ui.components.AppWindowDropdownPreference
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun TrafficConfigNetworkActivationEditor(
    show: Boolean,
    initialActivation: TrafficConfigNetworkActivation,
    onDismissRequest: () -> Unit,
    onSave: (TrafficConfigNetworkActivation) -> Unit,
) {
    if (!show) return
    var enabled by remember(initialActivation, show) { mutableStateOf(initialActivation.enabled) }
    val transportLabels = listOf(
        stringResource(R.string.configs_network_wifi),
        stringResource(R.string.configs_network_cellular),
    )
    var transportIndex by remember(initialActivation, show) {
        mutableIntStateOf(initialActivation.transport.coerceIn(transportLabels.indices))
    }
    AppWindowDialog(
        show = true,
        title = stringResource(R.string.configs_network_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.configs_network_enabled),
                summary = stringResource(R.string.configs_network_enabled_summary),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )
            AppWindowDropdownPreference(
                title = stringResource(R.string.configs_network_transport),
                items = transportLabels,
                selectedIndex = transportIndex,
                enabled = enabled,
                onSelectedIndexChange = { transportIndex = it },
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.configs_network_priority),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(text = stringResource(R.string.common_cancel), onClick = onDismissRequest)
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        onSave(
                            TrafficConfigNetworkActivation(
                                enabled = enabled,
                                transport = transportIndex,
                            ),
                        )
                    },
                )
            }
        }
    }
}
