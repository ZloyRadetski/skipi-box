// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** Settings without a Shadowrocket representation, stored only in SKIPI metadata. */
@Composable
internal fun TrafficConfigAndroidSettingsEditor(
    show: Boolean,
    initialSettings: TrafficConfigAndroidSettings,
    onDismissRequest: () -> Unit,
    onSave: (TrafficConfigAndroidSettings) -> Unit,
) {
    if (!show) return
    var settings by remember(initialSettings, show) { mutableStateOf(initialSettings) }
    var mtu by remember(initialSettings, show) { mutableStateOf(initialSettings.tunMtu) }
    var vpnDns by remember(initialSettings, show) { mutableStateOf(initialSettings.tunVpnDns) }
    var ipv4Cidr by remember(initialSettings, show) { mutableStateOf(initialSettings.tunIpv4Cidr) }
    var ipv6Cidr by remember(initialSettings, show) { mutableStateOf(initialSettings.tunIpv6Cidr) }
    var muxConcurrency by remember(initialSettings, show) { mutableStateOf(initialSettings.muxConcurrency) }
    var fragmentPackets by remember(initialSettings, show) { mutableStateOf(initialSettings.fragmentPackets) }
    var fragmentLength by remember(initialSettings, show) { mutableStateOf(initialSettings.fragmentLength) }
    var fragmentInterval by remember(initialSettings, show) { mutableStateOf(initialSettings.fragmentInterval) }

    WindowBottomSheet(
        show = true,
        title = stringResource(R.string.configs_android_title),
        startAction = {
            TextButton(text = stringResource(R.string.common_cancel), onClick = onDismissRequest)
        },
        endAction = {
            TextButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    onSave(
                        settings.copy(
                            tunMtu = mtu.trim(),
                            tunVpnDns = vpnDns.trim(),
                            tunIpv4Cidr = ipv4Cidr.trim(),
                            tunIpv6Cidr = ipv6Cidr.trim(),
                            muxConcurrency = muxConcurrency.trim(),
                            fragmentPackets = fragmentPackets.trim(),
                            fragmentLength = fragmentLength.trim(),
                            fragmentInterval = fragmentInterval.trim(),
                        ),
                    )
                },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            item {
                ConfigSettingsSection(
                    title = stringResource(R.string.configs_android_core),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.configs_sniffing),
                        summary = stringResource(R.string.configs_sniffing_summary),
                        checked = settings.enableSniffing,
                        onCheckedChange = { settings = settings.copy(enableSniffing = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_sniffing_route_only),
                        checked = settings.enableSniffingRouteOnly,
                        enabled = settings.enableSniffing,
                        onCheckedChange = { settings = settings.copy(enableSniffingRouteOnly = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_mux),
                        checked = settings.enableMux,
                        onCheckedChange = { settings = settings.copy(enableMux = it) },
                    )
                    if (settings.enableMux) {
                        ConfigTextField(
                            value = muxConcurrency,
                            onValueChange = { muxConcurrency = it },
                            label = stringResource(R.string.configs_mux_concurrency),
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.configs_fragment),
                        checked = settings.enableFragment,
                        onCheckedChange = { settings = settings.copy(enableFragment = it) },
                    )
                    if (settings.enableFragment) {
                        ConfigTextField(
                            value = fragmentPackets,
                            onValueChange = { fragmentPackets = it },
                            label = stringResource(R.string.configs_fragment_packets),
                        )
                        ConfigTextField(
                            value = fragmentLength,
                            onValueChange = { fragmentLength = it },
                            label = stringResource(R.string.configs_fragment_length),
                        )
                        ConfigTextField(
                            value = fragmentInterval,
                            onValueChange = { fragmentInterval = it },
                            label = stringResource(R.string.configs_fragment_interval),
                        )
                    }
                }
                ConfigSettingsSection(
                    title = stringResource(R.string.configs_android_vpn),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.configs_local_dns),
                        checked = settings.enableVpnLocalDns,
                        onCheckedChange = { settings = settings.copy(enableVpnLocalDns = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_fake_dns),
                        checked = settings.enableFakeDns,
                        enabled = settings.enableVpnLocalDns,
                        onCheckedChange = { settings = settings.copy(enableFakeDns = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_append_http_proxy),
                        checked = settings.enableVpnAppendHttpProxy,
                        onCheckedChange = { settings = settings.copy(enableVpnAppendHttpProxy = it) },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.configs_hevtun),
                        checked = settings.enableVpnHevTun,
                        onCheckedChange = { settings = settings.copy(enableVpnHevTun = it) },
                    )
                    ConfigTextField(value = mtu, onValueChange = { mtu = it }, label = stringResource(R.string.configs_tun_mtu))
                    ConfigTextField(value = vpnDns, onValueChange = { vpnDns = it }, label = stringResource(R.string.configs_tun_dns))
                    ConfigTextField(value = ipv4Cidr, onValueChange = { ipv4Cidr = it }, label = stringResource(R.string.configs_tun_ipv4))
                    ConfigTextField(value = ipv6Cidr, onValueChange = { ipv6Cidr = it }, label = stringResource(R.string.configs_tun_ipv6))
                }
            }
        }
    }
}

@Composable
private fun ConfigSettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        top.yukonga.miuix.kmp.basic.SmallTitle(text = title)
        content()
    }
}

@Composable
private fun ConfigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    // key(value) forces the TextFieldState to be recreated whenever the
    // external value changes, keeping unidirectional data flow correct.
    // Without it, rememberTextFieldState(initialText = ...) only applies
    // once and ignores subsequent external updates.
    key(value) {
        TextField(
            state = rememberTextFieldState(initialText = value),
            inputTransformation = { onValueChange(asCharSequence().toString()) },
            label = label,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
    }
}
