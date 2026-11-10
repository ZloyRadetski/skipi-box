// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppWindowDropdownPreference
import app.skipi.ui.layout.fadeUnderTopBar
import app.skipi.ui.layout.withTopBarGap
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_hide
import app.skipi.ui.resources.common_show
import app.skipi.ui.resources.settings_local_proxy
import app.skipi.ui.resources.settings_local_proxy_auth_hint_auto
import app.skipi.ui.resources.settings_local_proxy_auth_hint_json
import app.skipi.ui.resources.settings_local_proxy_auth_hint_manual
import app.skipi.ui.resources.settings_local_proxy_auth_mode_auto
import app.skipi.ui.resources.settings_local_proxy_auth_mode_json
import app.skipi.ui.resources.settings_local_proxy_auth_mode_manual
import app.skipi.ui.resources.settings_local_proxy_auth_mode_off
import app.skipi.ui.resources.settings_local_proxy_copy_password
import app.skipi.ui.resources.settings_local_proxy_copy_username
import app.skipi.ui.resources.settings_local_proxy_dynamic_port
import app.skipi.ui.resources.settings_local_proxy_dynamic_port_summary
import app.skipi.ui.resources.settings_local_proxy_generate
import app.skipi.ui.resources.settings_local_proxy_http_auth_mode
import app.skipi.ui.resources.settings_local_proxy_http_auth_system_note
import app.skipi.ui.resources.settings_local_proxy_http_inbound
import app.skipi.ui.resources.settings_local_proxy_http_inbound_summary
import app.skipi.ui.resources.settings_local_proxy_http_port
import app.skipi.ui.resources.settings_local_proxy_lan_http_endpoint
import app.skipi.ui.resources.settings_local_proxy_lan_instruction
import app.skipi.ui.resources.settings_local_proxy_lan_ip_label
import app.skipi.ui.resources.settings_local_proxy_lan_sharing_section
import app.skipi.ui.resources.settings_local_proxy_lan_socks_endpoint
import app.skipi.ui.resources.settings_local_proxy_listen_all_interfaces
import app.skipi.ui.resources.settings_local_proxy_listen_all_interfaces_summary
import app.skipi.ui.resources.settings_local_proxy_network_params
import app.skipi.ui.resources.settings_local_proxy_password
import app.skipi.ui.resources.settings_local_proxy_port
import app.skipi.ui.resources.settings_local_proxy_security_section
import app.skipi.ui.resources.settings_local_proxy_socks_auth_mode
import app.skipi.ui.resources.settings_local_proxy_username
import org.jetbrains.compose.resources.stringResource
import platform.LocalProxyAuthMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Local proxy preference UI. One card per inbound holds everything about it (port, authorization, login),
 * so nothing about SOCKS5 or HTTP is scattered over the page. Network discovery, clipboard, service
 * changes and persistence stay in the host.
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiLocalProxySettingsScreen(
    state: LocalProxySettingsState,
    labels: LocalProxySettingsLabels,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onDynamicPortChange: (Boolean) -> Unit,
    onPortChange: (String) -> Unit,
    onHttpEnabledChange: (Boolean) -> Unit,
    onHttpPortChange: (String) -> Unit,
    onListenAllInterfacesChange: (Boolean) -> Unit,
    onSocksAuthModeChange: (LocalProxyAuthMode) -> Unit,
    onHttpAuthModeChange: (LocalProxyAuthMode) -> Unit,
    onSocksCredentialsChange: (username: String, password: String) -> Unit,
    onHttpCredentialsChange: (username: String, password: String) -> Unit,
    onGenerateSocksCredentials: () -> Unit,
    onGenerateHttpCredentials: () -> Unit,
    onCopy: (text: String, label: String) -> Unit,
    capabilities: LocalProxySettingsCapabilities = LocalProxySettingsCapabilities(),
    /**
     * Host rows that only make sense with the HTTP inbound running (Android's VPN HTTP proxy, Desktop's system
     * proxy). They sit inside the HTTP card, below its switch, so the inbound has one switch and these use it.
     */
    httpPlatformContent: (@Composable ColumnScope.() -> Unit)? = null,
    platformContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val httpOn = capabilities.httpInbound && state.enableHttpEndpoint
    val portsEditable = !capabilities.dynamicPort || !state.dynamicPort

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize(),
        topBar = { key(labels.screenTitle) { SettingsBackTopBar(labels.screenTitle, isWideScreen, onBack) } },
    ) { innerPadding ->
        val contentPadding = settingsPageContentPadding(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fadeUnderTopBar(contentPadding.calculateTopPadding()).fillMaxSize(),
                contentPadding = contentPadding.withTopBarGap(),
            ) {
                if (capabilities.dynamicPort || capabilities.listenAllInterfaces) {
                    item(key = "local_proxy_network_title") { SmallTitle(text = labels.networkSectionTitle) }
                    item(key = "local_proxy_network_card") {
                        SkipiSettingsSectionCard {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                if (capabilities.dynamicPort) {
                                    SwitchPreference(
                                        title = labels.dynamicPortTitle,
                                        summary = labels.dynamicPortSummary,
                                        checked = state.dynamicPort,
                                        onCheckedChange = onDynamicPortChange,
                                    )
                                }
                                if (capabilities.listenAllInterfaces) {
                                    SwitchPreference(
                                        title = labels.listenAllTitle,
                                        summary = labels.listenAllSummary,
                                        checked = state.listenAllInterfaces,
                                        onCheckedChange = onListenAllInterfacesChange,
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "local_proxy_socks_title") { SmallTitle(text = "SOCKS5") }
                item(key = "local_proxy_socks_card") {
                    SkipiSettingsSectionCard {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            PortRow(portsEditable, labels.portLabel, state.port, state.portError, onPortChange)
                            if (capabilities.authentication) {
                                AuthBlock(
                                    title = labels.socksAuthModeTitle,
                                    mode = state.socksAuthMode,
                                    username = state.username,
                                    password = state.password,
                                    showCredentials = capabilities.credentials,
                                    labels = labels,
                                    onModeChange = onSocksAuthModeChange,
                                    onCredentialsChange = onSocksCredentialsChange,
                                    onGenerate = onGenerateSocksCredentials,
                                    onCopy = onCopy,
                                )
                            }
                        }
                    }
                }

                if (capabilities.httpInbound) {
                    item(key = "local_proxy_http_title") { SmallTitle(text = "HTTP") }
                    item(key = "local_proxy_http_card") {
                        SkipiSettingsSectionCard {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                SwitchPreference(
                                    title = labels.httpInboundTitle,
                                    summary = labels.httpInboundSummary,
                                    checked = state.enableHttpEndpoint,
                                    onCheckedChange = onHttpEnabledChange,
                                )
                                AnimatedVisibility(
                                    visible = httpOn,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    Column {
                                        PortRow(portsEditable, labels.httpPortLabel, state.httpPort, state.httpPortError, onHttpPortChange)
                                        if (capabilities.authentication) {
                                            AuthBlock(
                                                title = labels.httpAuthModeTitle,
                                                mode = state.httpAuthMode,
                                                username = state.httpUsername,
                                                password = state.httpPassword,
                                                showCredentials = capabilities.credentials,
                                                labels = labels,
                                                onModeChange = onHttpAuthModeChange,
                                                onCredentialsChange = onHttpCredentialsChange,
                                                onGenerate = onGenerateHttpCredentials,
                                                onCopy = onCopy,
                                            )
                                            if (state.httpAuthMode != LocalProxyAuthMode.Off) {
                                                Text(
                                                    text = labels.httpAuthSystemNote,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                )
                                            }
                                        }
                                        httpPlatformContent?.invoke(this)
                                    }
                                }
                            }
                        }
                    }
                }

                if (capabilities.lanEndpoints && state.listenAllInterfaces) {
                    item(key = "local_proxy_lan_title") { SmallTitle(text = labels.lanSectionTitle) }
                    item(key = "local_proxy_lan_card") {
                        SkipiSettingsSectionCard {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = labels.lanInstruction,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                ValueRow(labels.ipLabel, state.displayIp, onCopy = { onCopy(state.displayIp, labels.ipLabel) })
                                ValueRow(
                                    labels.socksEndpointLabel,
                                    state.socksEndpoint,
                                    onCopy = { onCopy(state.socksEndpoint, labels.socksEndpointLabel) },
                                )
                                if (httpOn) {
                                    ValueRow(
                                        labels.httpEndpointLabel,
                                        state.httpEndpoint,
                                        onCopy = { onCopy(state.httpEndpoint, labels.httpEndpointLabel) },
                                    )
                                }
                            }
                        }
                    }
                }
                platformContent?.let { content -> item(key = "local_proxy_platform_extension") { Column(content = content) } }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}

/** A port is a compact row, title on the left and a short numeric field on the right, so it fits a phone. */
@Composable
private fun PortRow(visible: Boolean, label: String, value: String, error: String?, onValueChange: (String) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                TextField(
                    value = value,
                    onValueChange = { onValueChange(sanitizeLocalProxyPort(it)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(112.dp),
                )
            }
            error?.let { message ->
                Text(text = message, fontSize = 12.sp, color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

/** The authorization mode of one inbound and, for the modes that carry one, its login. */
@Composable
private fun AuthBlock(
    title: String,
    mode: LocalProxyAuthMode,
    username: String,
    password: String,
    showCredentials: Boolean,
    labels: LocalProxySettingsLabels,
    onModeChange: (LocalProxyAuthMode) -> Unit,
    onCredentialsChange: (username: String, password: String) -> Unit,
    onGenerate: () -> Unit,
    onCopy: (text: String, label: String) -> Unit,
) {
    AppWindowDropdownPreference(
        title = title,
        summary = labels.modeHint(mode),
        items = LocalProxyAuthModeOptions.map(labels::modeName),
        selectedIndex = LocalProxyAuthModeOptions.indexOf(mode).coerceAtLeast(0),
        onSelectedIndexChange = { index -> onModeChange(LocalProxyAuthModeOptions[index]) },
    )
    AnimatedVisibility(
        visible = showCredentials && mode.showsCredentials(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        var passwordVisible by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth()) {
            if (mode == LocalProxyAuthMode.Manual) {
                TextField(
                    value = username,
                    onValueChange = { onCredentialsChange(it, password) },
                    label = labels.usernameLabel,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                TextField(
                    value = password,
                    onValueChange = { onCredentialsChange(username, it) },
                    label = labels.passwordLabel,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                ValueRow(
                    label = labels.usernameLabel,
                    value = username.ifBlank { "—" },
                    copyDescription = labels.copyUsernameDescription,
                    onCopy = { onCopy(username, labels.usernameLabel) },
                )
                ValueRow(
                    label = labels.passwordLabel,
                    value = password.ifBlank { "—" },
                    masked = !passwordVisible,
                    onToggleMask = { passwordVisible = !passwordVisible },
                    showText = labels.showText,
                    hideText = labels.hideText,
                    copyDescription = labels.copyPasswordDescription,
                    onCopy = { onCopy(password, labels.passwordLabel) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onGenerate).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = labels.generateText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** A read-only value: small caption, readable monospace value, optional reveal and copy icons. No pill. */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    masked: Boolean = false,
    onToggleMask: (() -> Unit)? = null,
    showText: String = "",
    hideText: String = "",
    copyDescription: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCopy).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(
                text = if (masked) "••••••••••••" else value,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        onToggleMask?.let { toggle ->
            IconButton(onClick = toggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (masked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (masked) showText else hideText,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = MiuixIcons.Copy,
                contentDescription = copyDescription,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Localized local-proxy labels shared by every host. */
@Composable
fun rememberLocalProxySettingsLabels(): LocalProxySettingsLabels = LocalProxySettingsLabels(
    screenTitle = stringResource(Res.string.settings_local_proxy),
    networkSectionTitle = stringResource(Res.string.settings_local_proxy_network_params),
    dynamicPortTitle = stringResource(Res.string.settings_local_proxy_dynamic_port),
    dynamicPortSummary = stringResource(Res.string.settings_local_proxy_dynamic_port_summary),
    portLabel = stringResource(Res.string.settings_local_proxy_port),
    httpInboundTitle = stringResource(Res.string.settings_local_proxy_http_inbound),
    httpInboundSummary = stringResource(Res.string.settings_local_proxy_http_inbound_summary),
    httpPortLabel = stringResource(Res.string.settings_local_proxy_http_port),
    listenAllTitle = stringResource(Res.string.settings_local_proxy_listen_all_interfaces),
    listenAllSummary = stringResource(Res.string.settings_local_proxy_listen_all_interfaces_summary),
    lanSectionTitle = stringResource(Res.string.settings_local_proxy_lan_sharing_section),
    lanInstruction = stringResource(Res.string.settings_local_proxy_lan_instruction),
    ipLabel = stringResource(Res.string.settings_local_proxy_lan_ip_label),
    socksEndpointLabel = stringResource(Res.string.settings_local_proxy_lan_socks_endpoint),
    httpEndpointLabel = stringResource(Res.string.settings_local_proxy_lan_http_endpoint),
    securitySectionTitle = stringResource(Res.string.settings_local_proxy_security_section),
    socksAuthModeTitle = stringResource(Res.string.settings_local_proxy_socks_auth_mode),
    httpAuthModeTitle = stringResource(Res.string.settings_local_proxy_http_auth_mode),
    authModeAuto = stringResource(Res.string.settings_local_proxy_auth_mode_auto),
    authModeManual = stringResource(Res.string.settings_local_proxy_auth_mode_manual),
    authModeFromJson = stringResource(Res.string.settings_local_proxy_auth_mode_json),
    authModeOff = stringResource(Res.string.settings_local_proxy_auth_mode_off),
    authHintAuto = stringResource(Res.string.settings_local_proxy_auth_hint_auto),
    authHintManual = stringResource(Res.string.settings_local_proxy_auth_hint_manual),
    authHintFromJson = stringResource(Res.string.settings_local_proxy_auth_hint_json),
    httpAuthSystemNote = stringResource(Res.string.settings_local_proxy_http_auth_system_note),
    usernameLabel = stringResource(Res.string.settings_local_proxy_username),
    passwordLabel = stringResource(Res.string.settings_local_proxy_password),
    generateText = stringResource(Res.string.settings_local_proxy_generate),
    showText = stringResource(Res.string.common_show),
    hideText = stringResource(Res.string.common_hide),
    copyUsernameDescription = stringResource(Res.string.settings_local_proxy_copy_username),
    copyPasswordDescription = stringResource(Res.string.settings_local_proxy_copy_password),
)
