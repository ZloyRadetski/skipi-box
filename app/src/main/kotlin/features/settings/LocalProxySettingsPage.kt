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
import app.generateRandomProxyCredential
import app.skipi.ui.settings.LocalProxySettingsLabels
import app.skipi.ui.settings.LocalProxySettingsState
import app.skipi.ui.settings.SkipiLocalProxySettingsScreen
import app.skipi.ui.settings.isValidLocalProxyPort
import engine.vpn.VpnDefaults
import engine.vpn.fallbackAppendHttpProxyPort
import kotlinx.coroutines.launch
import ui.clipboard.setPlainText
import java.net.Inet4Address
import java.net.NetworkInterface

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
    val httpPort = localPort.toIntOrNull()?.let(::fallbackAppendHttpProxyPort)
        ?: (VpnDefaults.LOCAL_PROXY_PORT + 1)
    val displayIp = remember { getLocalNetworkIpAddresses().firstOrNull() ?: "192.168.43.1" }
    val labels = LocalProxySettingsLabels(
        screenTitle = stringResource(R.string.settings_local_proxy),
        networkSectionTitle = stringResource(R.string.settings_local_proxy_network_params),
        dynamicPortTitle = stringResource(R.string.settings_local_proxy_dynamic_port),
        dynamicPortSummary = stringResource(R.string.settings_local_proxy_dynamic_port_summary),
        portLabel = stringResource(R.string.settings_local_proxy_port),
        listenAllTitle = stringResource(R.string.settings_local_proxy_listen_all_interfaces),
        listenAllSummary = stringResource(R.string.settings_local_proxy_listen_all_interfaces_summary),
        lanSectionTitle = stringResource(R.string.settings_local_proxy_lan_sharing_section),
        lanInstruction = stringResource(R.string.settings_local_proxy_lan_instruction),
        ipLabel = stringResource(R.string.settings_local_proxy_lan_ip_label),
        socksEndpointLabel = stringResource(R.string.settings_local_proxy_lan_socks_endpoint),
        httpEndpointLabel = stringResource(R.string.settings_local_proxy_lan_http_endpoint),
        securitySectionTitle = stringResource(R.string.settings_local_proxy_security_section),
        authenticationTitle = stringResource(R.string.settings_local_proxy_enable_auth),
        authenticationSummary = stringResource(R.string.settings_local_proxy_enable_auth_summary),
        credentialsSectionTitle = stringResource(R.string.settings_local_proxy_credentials_section),
        authorizationTitle = stringResource(R.string.settings_local_proxy_auth_title),
        tapToCopy = stringResource(R.string.settings_local_proxy_tap_to_copy),
        usernameLabel = usernameLabel,
        passwordLabel = passwordLabel,
        generateText = stringResource(R.string.settings_local_proxy_generate),
        showText = stringResource(R.string.common_show),
        hideText = stringResource(R.string.common_hide),
        copyUsernameDescription = stringResource(R.string.settings_local_proxy_copy_username),
        copyPasswordDescription = stringResource(R.string.settings_local_proxy_copy_password),
    )

    SkipiLocalProxySettingsScreen(
        state = LocalProxySettingsState(
            dynamicPort = appState.enableDynamicLocalProxyPort,
            port = appState.localProxyPort,
            listenAllInterfaces = appState.localProxyListenAllInterfaces,
            enableHttpEndpoint = appState.enableVpnAppendHttpProxy,
            authenticationEnabled = appState.enableLocalProxyAuth,
            username = appState.localProxyUsername,
            password = appState.localProxyPassword,
            displayIp = displayIp,
            socksEndpoint = "$displayIp:$localPort",
            httpEndpoint = "$displayIp:$httpPort",
            portError = if (isCustomPort && !isValidLocalProxyPort(appState.localProxyPort)) portInvalidMessage else null,
        ),
        labels = labels,
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = navigator::pop,
        onDynamicPortChange = { enabled -> updateAppState { it.copy(enableDynamicLocalProxyPort = enabled) } },
        onPortChange = { port -> updateAppState { it.copy(localProxyPort = port) } },
        onListenAllInterfacesChange = { enabled -> updateAppState { it.copy(localProxyListenAllInterfaces = enabled) } },
        onAuthenticationChange = { enabled -> updateAppState { it.copy(enableLocalProxyAuth = enabled) } },
        onGenerateCredentials = {
            updateAppState {
                it.copy(localProxyUsername = generateRandomProxyCredential(), localProxyPassword = generateRandomProxyCredential())
            }
            scope.launch { tipNotifier.show(generatedToast) }
        },
        onCopy = ::copyText,
    )
}

private fun getLocalNetworkIpAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.filter { it.isUp && !it.isLoopback && !it.isPointToPoint && !it.name.startsWith("skipi") && !it.name.startsWith("tun") }
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.filter { it is Inet4Address && !it.isLoopbackAddress && it.hostAddress != null }
        ?.mapNotNull { it.hostAddress }
        ?.distinct()
        ?.toList()
        .orEmpty()
}.getOrDefault(emptyList())
