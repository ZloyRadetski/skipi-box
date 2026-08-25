// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.animation.AnimatedVisibility
import ui.text.themedFontWeight
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.AppState
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.generateRandomProxyCredential
import features.settings.sheets.fiveDigitKeyboardOptions
import features.settings.sheets.isPort
import features.settings.sheets.sanitizeFiveDigitInput
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import ui.AppTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.clipboard.setPlainText
import ui.components.BackNavigationIcon
import ui.icons.IconsEye
import ui.icons.IconsEyeOff
import engine.vpn.VpnDefaults
import engine.vpn.fallbackAppendHttpProxyPort
import java.net.Inet4Address
import java.net.NetworkInterface
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import androidx.compose.ui.graphics.Color

@Composable
fun LocalProxySettingsPage(
    padding: PaddingValues,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val clipboard = LocalClipboard.current
    val tipNotifier = LocalAppServices.current.tipNotifier
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    var isPasswordVisible by remember { mutableStateOf(false) }

    val copyToastTemplate = stringResource(R.string.settings_local_proxy_copied_toast)
    val usernameLabel = stringResource(R.string.settings_local_proxy_username)
    val passwordLabel = stringResource(R.string.settings_local_proxy_password)
    val ipLabel = stringResource(R.string.settings_local_proxy_lan_ip_label)
    val socksEndpointLabel = stringResource(R.string.settings_local_proxy_lan_socks_endpoint)
    val httpEndpointLabel = stringResource(R.string.settings_local_proxy_lan_http_endpoint)
    val portInvalidMsg = stringResource(R.string.settings_local_proxy_port_invalid)

    fun copyText(text: String, label: String) {
        if (text.isBlank()) return
        scope.launch {
            clipboard.setPlainText(text)
            tipNotifier.show(String.format(copyToastTemplate, label))
        }
    }

    val isCustomPort = !appState.enableDynamicLocalProxyPort
    val portError = if (isCustomPort && !isPort(appState.localProxyPort)) {
        portInvalidMsg
    } else {
        null
    }

    Scaffold(
        containerColor = AppTheme.colors.background,
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.settings_local_proxy),
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = navigator::pop) },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.pageScrollModifiers(scrollBehavior),
                contentPadding = pageListPadding(contentPadding),
            ) {
                // Section 1: Network Parameters
                item(key = "section_network_title") {
                    SmallTitle(text = stringResource(R.string.settings_local_proxy_network_params))
                }

                item(key = "section_network_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            SwitchPreference(
                                title = stringResource(R.string.settings_local_proxy_dynamic_port),
                                summary = stringResource(R.string.settings_local_proxy_dynamic_port_summary),
                                checked = appState.enableDynamicLocalProxyPort,
                                onCheckedChange = { checked ->
                                    updateAppState { it.copy(enableDynamicLocalProxyPort = checked) }
                                },
                            )

                            AnimatedVisibility(
                                visible = !appState.enableDynamicLocalProxyPort,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                ) {
                                    TextField(
                                        value = appState.localProxyPort,
                                        onValueChange = { port ->
                                            updateAppState { it.copy(localProxyPort = sanitizeFiveDigitInput(port)) }
                                        },
                                        label = stringResource(R.string.settings_local_proxy_port),
                                        keyboardOptions = fiveDigitKeyboardOptions(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (portError != null) {
                                        Text(
                                            text = portError,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                        )
                                    }
                                }
                            }

                            SwitchPreference(
                                title = stringResource(R.string.settings_local_proxy_listen_all_interfaces),
                                summary = stringResource(R.string.settings_local_proxy_listen_all_interfaces_summary),
                                checked = appState.localProxyListenAllInterfaces,
                                onCheckedChange = { checked ->
                                    updateAppState { it.copy(localProxyListenAllInterfaces = checked) }
                                },
                            )
                        }
                    }
                }

                // Section: Hotspot & LAN Sharing info
                if (appState.localProxyListenAllInterfaces) {
                    item(key = "section_lan_title") {
                        SmallTitle(text = stringResource(R.string.settings_local_proxy_lan_sharing_section))
                    }

                    item(key = "section_lan_card") {
                        SettingsSectionCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_local_proxy_lan_instruction),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                val localIps = remember { getLocalNetworkIpAddresses() }
                                val displayIp = localIps.firstOrNull() ?: "192.168.43.1"
                                val socksPort = appState.localProxyPort.ifBlank { VpnDefaults.LOCAL_PROXY_PORT.toString() }
                                val socksEndpoint = "$displayIp:$socksPort"
                                val httpPort = socksPort.toIntOrNull()?.let { fallbackAppendHttpProxyPort(it) } ?: (VpnDefaults.LOCAL_PROXY_PORT + 1)
                                val httpEndpoint = "$displayIp:$httpPort"

                                LanEndpointRow(
                                    label = ipLabel,
                                    value = displayIp,
                                    onCopy = { copyText(displayIp, ipLabel) },
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                LanEndpointRow(
                                    label = socksEndpointLabel,
                                    value = socksEndpoint,
                                    onCopy = { copyText(socksEndpoint, socksEndpointLabel) },
                                )

                                if (appState.enableVpnAppendHttpProxy) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LanEndpointRow(
                                        label = httpEndpointLabel,
                                        value = httpEndpoint,
                                        onCopy = { copyText(httpEndpoint, httpEndpointLabel) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Authorization Switch
                item(key = "section_auth_title") {
                    SmallTitle(text = stringResource(R.string.settings_local_proxy_security_section))
                }

                item(key = "section_auth_card") {
                    SettingsSectionCard {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            SwitchPreference(
                                title = stringResource(R.string.settings_local_proxy_enable_auth),
                                summary = stringResource(R.string.settings_local_proxy_enable_auth_summary),
                                checked = appState.enableLocalProxyAuth,
                                onCheckedChange = { checked ->
                                    updateAppState { it.copy(enableLocalProxyAuth = checked) }
                                },
                            )
                        }
                    }
                }

                if (appState.enableLocalProxyAuth) {
                    item(key = "section_credentials_title") {
                        SmallTitle(text = stringResource(R.string.settings_local_proxy_credentials_section))
                    }

                    item(key = "section_credentials_card") {
                        SettingsSectionCard {
                            val generatedToastMessage = stringResource(R.string.settings_local_proxy_generated_toast)
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.settings_local_proxy_auth_title),
                                            fontSize = 15.sp,
                                            fontWeight = themedFontWeight(FontWeight.Bold),
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_local_proxy_tap_to_copy),
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        )
                                    }

                                    TextButton(
                                        text = stringResource(R.string.settings_local_proxy_generate),
                                        onClick = {
                                            val newUsername = generateRandomProxyCredential()
                                            val newPassword = generateRandomProxyCredential()
                                            updateAppState {
                                                it.copy(
                                                    localProxyUsername = newUsername,
                                                    localProxyPassword = newPassword,
                                                )
                                            }
                                            scope.launch {
                                                tipNotifier.show(generatedToastMessage)
                                            }
                                        },
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .clickable {
                                            copyText(appState.localProxyUsername, usernameLabel)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = usernameLabel,
                                            fontSize = 11.sp,
                                            fontWeight = themedFontWeight(FontWeight.Medium),
                                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = appState.localProxyUsername.ifBlank { "вЂ”" },
                                            fontSize = 15.sp,
                                            fontWeight = themedFontWeight(FontWeight.SemiBold),
                                            color = MiuixTheme.colorScheme.primary,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }

                                    IconButton(
                                        onClick = { copyText(appState.localProxyUsername, usernameLabel) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Copy,
                                            contentDescription = stringResource(R.string.settings_local_proxy_copy_username),
                                            tint = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .clickable {
                                            copyText(appState.localProxyPassword, passwordLabel)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = passwordLabel,
                                            fontSize = 11.sp,
                                            fontWeight = themedFontWeight(FontWeight.Medium),
                                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isPasswordVisible) appState.localProxyPassword.ifBlank { "вЂ”" } else "вЂўвЂўвЂўвЂўвЂўвЂўвЂўвЂўвЂўвЂўвЂўвЂў",
                                            fontSize = 15.sp,
                                            fontWeight = themedFontWeight(FontWeight.SemiBold),
                                            color = MiuixTheme.colorScheme.primary,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { isPasswordVisible = !isPasswordVisible },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) IconsEyeOff else IconsEye,
                                                contentDescription = stringResource(if (isPasswordVisible) R.string.common_hide else R.string.common_show),
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(2.dp))

                                        IconButton(
                                            onClick = { copyText(appState.localProxyPassword, passwordLabel) },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Copy,
                                                contentDescription = stringResource(R.string.settings_local_proxy_copy_password),
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanEndpointRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onCopy)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = themedFontWeight(FontWeight.Medium),
                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = themedFontWeight(FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
            )
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Copy,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun getLocalNetworkIpAddresses(): List<String> {
    return runCatching {
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
}
