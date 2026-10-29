// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.AppState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal class SettingsSheetState(
    private val updateAppState: ((AppState) -> AppState) -> Unit,
) {
    var showLocalProxySettings by mutableStateOf(false)
    var localProxySettingsDraft by mutableStateOf(LocalProxySettingsDraft())

    var showTunSettings by mutableStateOf(false)
    var tunSettingsDraft by mutableStateOf(TunSettingsDraft())

    var showDnsSettings by mutableStateOf(false)
    var dnsSettingsDraft by mutableStateOf(DnsSettingsDraft())

    var showMuxSettings by mutableStateOf(false)
    var muxSettingsDraft by mutableStateOf(MuxSettingsDraft())

    var showFragmentSettings by mutableStateOf(false)
    var fragmentSettingsDraft by mutableStateOf(FragmentSettingsDraft())

    var showSubscriptionPingSettings by mutableStateOf(false)
    var subscriptionPingSettingsDraft by mutableStateOf(SubscriptionPingSettingsDraft())

    var showServiceControl by mutableStateOf(false)
    var serviceControlDraft by mutableStateOf(features.settings.servicecontrol.ServiceControlSettings())

    fun openTunSettings(appState: AppState) {
        tunSettingsDraft = appState.toTunSettingsDraft()
        showTunSettings = true
    }

}

@Composable
internal fun rememberSettingsSheetState(
    updateAppState: ((AppState) -> AppState) -> Unit,
): SettingsSheetState {
    return remember(updateAppState) { SettingsSheetState(updateAppState) }
}

