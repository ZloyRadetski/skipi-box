// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.ui

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.skipi.ui.networkautomation.NetworkAutomationServerOption
import app.skipi.ui.networkautomation.SkipiNetworkAutomationScreen
import features.networkautomation.engine.NetworkAutomationEvaluator
import features.networkautomation.policy.NetworkAutomationRules
import features.proxy.server.display.displayName
import kotlinx.coroutines.launch

@Composable
fun SettingsNetworkAutomationPage(
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val services = LocalAppServices.current
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val scope = rememberCoroutineScope()
    val noWifiDetectedText = stringResource(R.string.network_automation_no_wifi_detected)
    val locationPermissionDeniedText = stringResource(R.string.network_automation_wifi_permission_denied)

    SkipiNetworkAutomationScreen(
        networkAutomationEnabled = appState.enableNetworkAutomation,
        onDemandEnabled = appState.enableOnDemandVpn,
        rules = appState.networkAutomationRules,
        servers = appState.proxyServers.map { server ->
            NetworkAutomationServerOption(id = server.id, name = server.displayName())
        },
        padding = padding,
        isWideScreen = LocalIsWideScreen.current,
        onBack = { navigator.pop() },
        onNetworkAutomationChange = { enabled -> updateAppState { it.copy(enableNetworkAutomation = enabled) } },
        onOnDemandChange = { enabled -> updateAppState { it.copy(enableOnDemandVpn = enabled) } },
        onSaveRule = { saved ->
            updateAppState { it.copy(networkAutomationRules = NetworkAutomationRules.upsert(it.networkAutomationRules, saved)) }
        },
        onDeleteRule = { id ->
            updateAppState { it.copy(networkAutomationRules = NetworkAutomationRules.remove(it.networkAutomationRules, id)) }
        },
        onRuleEnabledChange = { id, enabled ->
            updateAppState {
                it.copy(networkAutomationRules = NetworkAutomationRules.setEnabled(it.networkAutomationRules, id, enabled))
            }
        },
        onRequestCurrentWifi = { onSsidReceived ->
            scope.launch {
                if (!services.requestWifiSsidPermission()) {
                    Toast.makeText(context, locationPermissionDeniedText, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val current = NetworkAutomationEvaluator.getCurrentWifiSsid(context)
                if (!current.isNullOrBlank()) {
                    onSsidReceived(current)
                } else {
                    Toast.makeText(context, noWifiDetectedText, Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}
