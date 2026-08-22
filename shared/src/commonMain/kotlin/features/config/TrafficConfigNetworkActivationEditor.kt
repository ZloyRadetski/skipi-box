// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.layout.Column
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
import org.jetbrains.compose.resources.stringResource
import app.shared.res.Res
import app.shared.res.common_cancel
import app.shared.res.common_save
import app.shared.res.configs_network_cellular
import app.shared.res.configs_network_enabled
import app.shared.res.configs_network_enabled_summary
import app.shared.res.configs_network_priority
import app.shared.res.configs_network_title
import app.shared.res.configs_network_transport
import app.shared.res.configs_network_wifi
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun TrafficConfigNetworkActivationEditor(
    show: Boolean,
    initialActivation: TrafficConfigNetworkActivation,
    onDismissRequest: () -> Unit,
    onSave: (TrafficConfigNetworkActivation) -> Unit,
) {
    if (!show) return
    var enabled by remember(initialActivation, show) { mutableStateOf(initialActivation.enabled) }
    val transportLabels = listOf(
        stringResource(Res.string.configs_network_wifi),
        stringResource(Res.string.configs_network_cellular),
    )
    var transportIndex by remember(initialActivation, show) {
        mutableIntStateOf(initialActivation.transport.coerceIn(transportLabels.indices))
    }
    WindowDialog(
        show = true,
        title = stringResource(Res.string.configs_network_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(Res.string.configs_network_enabled),
                summary = stringResource(Res.string.configs_network_enabled_summary),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )
            WindowDropdownPreference(
                title = stringResource(Res.string.configs_network_transport),
                items = transportLabels,
                selectedIndex = transportIndex,
                enabled = enabled,
                onSelectedIndexChange = { transportIndex = it },
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(Res.string.configs_network_priority),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(text = stringResource(Res.string.common_cancel), onClick = onDismissRequest)
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(Res.string.common_save),
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
