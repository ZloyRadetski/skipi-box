// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.skipi.app.settings.generateRandomProxyCredential
import app.skipi.app.settings.localNetworkIpAddresses
import app.skipi.ui.settings.rememberLocalProxySettingsLabels
import app.skipi.ui.settings.LocalProxySettingsState
import app.skipi.ui.settings.SkipiSettingRowIcons
import app.skipi.ui.settings.SkipiLocalProxySettingsScreen
import app.skipi.ui.settings.SkipiSettingsSwitchRow
import app.skipi.ui.settings.isValidLocalProxyPort
import engine.vpn.VpnDefaults
import app.httpProxyInboundEnabled
import app.localProxyHttpAuthMode
import app.localProxySocksAuthMode
import app.withLocalHttpProxyInbound
import app.withLocalProxyHttpAuthMode
import app.withLocalProxySocksAuthMode
import kotlinx.coroutines.launch
import ui.clipboard.setPlainText

@Composable
fun LocalProxySettingsPage(padding: PaddingValues) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val clipboard = LocalClipboard.current
    val tipNotifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()
    val copyToastTemplate = stringResource(R.string.settings_local_proxy_copied_toast)
    val usernameLabel = stringResource(R.string.settings_local_proxy_username)
    val passwordLabel = stringResource(R.string.settings_local_proxy_password)
    val generatedToast = stringResource(R.string.settings_local_proxy_generated_toast)
    val portInvalidMessage = stringResource(R.string.settings_local_proxy_port_invalid)

    fun copyText(text: String, label: String) {
        if (text.isBlank()) return
        scope.launch {
            clipboard.setPlainText(text)
            tipNotifier.show(String.format(copyToastTemplate, label))
        }
    }

    val isCustomPort = !appState.enableDynamicLocalProxyPort
    val localPort = appState.localProxyPort.ifBlank { VpnDefaults.LOCAL_PROXY_PORT.toString() }
    val httpPort = appState.localHttpProxyPort.ifBlank { VpnDefaults.LOCAL_HTTP_PROXY_PORT.toString() }
    val httpInboundOn = appState.httpProxyInboundEnabled
    val httpPortError = when {
        !httpInboundOn || !isCustomPort -> null
        !isValidLocalProxyPort(appState.localHttpProxyPort) -> portInvalidMessage
        appState.localHttpProxyPort == appState.localProxyPort -> portInvalidMessage
        else -> null
    }
    val displayIp = remember { localNetworkIpAddresses().firstOrNull() ?: "192.168.43.1" }
    val labels = rememberLocalProxySettingsLabels()

    SkipiLocalProxySettingsScreen(
        state = LocalProxySettingsState(
            dynamicPort = appState.enableDynamicLocalProxyPort,
            port = appState.localProxyPort,
            listenAllInterfaces = appState.localProxyListenAllInterfaces,
            enableHttpEndpoint = httpInboundOn,
            httpPort = appState.localHttpProxyPort,
            socksAuthMode = appState.localProxySocksAuthMode(),
            httpAuthMode = appState.localProxyHttpAuthMode(),
            username = appState.localProxyUsername,
            password = appState.localProxyPassword,
            httpUsername = appState.localHttpProxyUsername,
            httpPassword = appState.localHttpProxyPassword,
            displayIp = displayIp,
            socksEndpoint = "$displayIp:$localPort",
            httpEndpoint = "$displayIp:$httpPort",
            portError = if (isCustomPort && !isValidLocalProxyPort(appState.localProxyPort)) portInvalidMessage else null,
            httpPortError = httpPortError,
        ),
        labels = labels,
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onDynamicPortChange = { enabled -> updateAppState { it.copy(enableDynamicLocalProxyPort = enabled) } },
        onPortChange = { port -> updateAppState { it.copy(localProxyPort = port) } },
        onHttpEnabledChange = { enabled -> updateAppState { it.withLocalHttpProxyInbound(enabled) } },
        onHttpPortChange = { port -> updateAppState { it.copy(localHttpProxyPort = port) } },
        onListenAllInterfacesChange = { enabled -> updateAppState { it.copy(localProxyListenAllInterfaces = enabled) } },
        onSocksAuthModeChange = { mode -> updateAppState { it.withLocalProxySocksAuthMode(mode) } },
        onHttpAuthModeChange = { mode -> updateAppState { it.withLocalProxyHttpAuthMode(mode) } },
        onSocksCredentialsChange = { username, password ->
            updateAppState { it.copy(localProxyUsername = username, localProxyPassword = password) }
        },
        onHttpCredentialsChange = { username, password ->
            updateAppState { it.copy(localHttpProxyUsername = username, localHttpProxyPassword = password) }
        },
        onGenerateSocksCredentials = {
            updateAppState {
                it.copy(localProxyUsername = generateRandomProxyCredential(), localProxyPassword = generateRandomProxyCredential())
            }
            scope.launch { tipNotifier.show(generatedToast) }
        },
        onGenerateHttpCredentials = {
            updateAppState {
                it.copy(
                    localHttpProxyUsername = generateRandomProxyCredential(),
                    localHttpProxyPassword = generateRandomProxyCredential(),
                )
            }
            scope.launch { tipNotifier.show(generatedToast) }
        },
        onCopy = ::copyText,
        // The VPN's own HTTP proxy needs the inbound, so it sits in the HTTP card instead of being a second switch for it.
        httpPlatformContent = {
            SkipiSettingsSwitchRow(
                title = stringResource(R.string.configs_append_http_proxy),
                icon = SkipiSettingRowIcons.Http,
                summary = stringResource(R.string.configs_append_http_proxy_summary),
                checked = appState.enableVpnAppendHttpProxy,
                onCheckedChange = { enabled -> updateAppState { it.copy(enableVpnAppendHttpProxy = enabled) } },
            )
        },
    )
}
