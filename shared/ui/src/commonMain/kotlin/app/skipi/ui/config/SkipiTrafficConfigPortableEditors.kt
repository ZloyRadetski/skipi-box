// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.AppWindowDropdownPreference
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_ipv6
import app.skipi.ui.resources.configs_ipv6_prefer
import app.skipi.ui.resources.configs_ipv6_prefer_summary
import app.skipi.ui.resources.configs_ipv6_summary
import app.skipi.ui.resources.configs_network_cellular
import app.skipi.ui.resources.configs_network_enabled
import app.skipi.ui.resources.configs_network_enabled_summary
import app.skipi.ui.resources.configs_network_priority
import app.skipi.ui.resources.configs_network_transport_summary
import app.skipi.ui.resources.configs_network_transport
import app.skipi.ui.resources.configs_network_wifi
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Portable controls for the Shadowrocket General values currently applied by SKIPI. */
@Composable
fun SkipiTrafficConfigGeneralOptions(
    ipv6: Boolean,
    preferIpv6: Boolean,
    onIpv6Change: (Boolean) -> Unit,
    onPreferIpv6Change: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
    ) {
        SwitchPreference(
            title = stringResource(Res.string.configs_ipv6),
            summary = stringResource(Res.string.configs_ipv6_summary),
            checked = ipv6,
            onCheckedChange = onIpv6Change,
        )
        SwitchPreference(
            title = stringResource(Res.string.configs_ipv6_prefer),
            summary = stringResource(Res.string.configs_ipv6_prefer_summary),
            enabled = ipv6,
            checked = preferIpv6,
            onCheckedChange = onPreferIpv6Change,
        )
    }
}

/** Portable network activation controls; Android supplies detection and activation behavior. */
@Composable
fun SkipiTrafficConfigNetworkActivation(
    enabled: Boolean,
    transport: Int,
    onEnabledChange: (Boolean) -> Unit,
    onTransportChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transports = listOf(
        stringResource(Res.string.configs_network_wifi),
        stringResource(Res.string.configs_network_cellular),
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
    ) {
        Column(Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(Res.string.configs_network_enabled),
                summary = stringResource(Res.string.configs_network_enabled_summary),
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
            AppWindowDropdownPreference(
                title = stringResource(Res.string.configs_network_transport),
                items = transports,
                selectedIndex = transport.coerceIn(transports.indices),
                enabled = enabled,
                onSelectedIndexChange = onTransportChange,
            )
            Text(
                text = stringResource(Res.string.configs_network_transport_summary),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(Res.string.configs_network_priority),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
