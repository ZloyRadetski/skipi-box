// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
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
import app.skipi.ui.resources.settings_local_proxy_http_credentials
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
import app.skipi.ui.resources.settings_local_proxy_socks_credentials
import app.skipi.ui.resources.settings_local_proxy_tap_to_copy
import app.skipi.ui.resources.settings_local_proxy_username
import org.jetbrains.compose.resources.stringResource
import platform.LocalProxyAuthMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Local proxy preference UI. Network discovery, clipboard, service changes and persistence stay in the host. */
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
    platformContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val httpOn = capabilities.httpInbound && state.enableHttpEndpoint

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
                            PortField(
                                visible = !capabilities.dynamicPort || !state.dynamicPort,
                                value = state.port,
                                label = labels.portLabel,
                                error = state.portError,
                                onValueChange = onPortChange,
                            )
                            if (capabilities.httpInbound) {
                                SwitchPreference(
                                    title = labels.httpInboundTitle,
                                    summary = labels.httpInboundSummary,
                                    checked = state.enableHttpEndpoint,
                                    onCheckedChange = onHttpEnabledChange,
                                )
                                PortField(
                                    visible = state.enableHttpEndpoint && (!capabilities.dynamicPort || !state.dynamicPort),
                                    value = state.httpPort,
                                    label = labels.httpPortLabel,
                                    error = state.httpPortError,
                                    onValueChange = onHttpPortChange,
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
                if (capabilities.lanEndpoints && state.listenAllInterfaces) {
                    item(key = "local_proxy_lan_title") { SmallTitle(text = labels.lanSectionTitle) }
                    item(key = "local_proxy_lan_card") {
                        SkipiSettingsSectionCard {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = labels.lanInstruction,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(14.dp))
                                LanEndpointRow(labels.ipLabel, state.displayIp) { onCopy(state.displayIp, labels.ipLabel) }
                                Spacer(Modifier.height(10.dp))
                                LanEndpointRow(labels.socksEndpointLabel, state.socksEndpoint) {
                                    onCopy(state.socksEndpoint, labels.socksEndpointLabel)
                                }
                                if (httpOn) {
                                    Spacer(Modifier.height(10.dp))
                                    LanEndpointRow(labels.httpEndpointLabel, state.httpEndpoint) {
                                        onCopy(state.httpEndpoint, labels.httpEndpointLabel)
                                    }
                                }
                            }
                        }
                    }
                }
                if (capabilities.authentication) {
                    item(key = "local_proxy_security_title") { SmallTitle(text = labels.securitySectionTitle) }
                    item(key = "local_proxy_security_card") {
                        SkipiSettingsSectionCard {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                AuthModeSelector(labels.socksAuthModeTitle, state.socksAuthMode, labels, onSocksAuthModeChange)
                                if (httpOn) {
                                    AuthModeSelector(labels.httpAuthModeTitle, state.httpAuthMode, labels, onHttpAuthModeChange)
                                    if (state.httpAuthMode != LocalProxyAuthMode.Off) {
                                        Text(
                                            text = labels.httpAuthSystemNote,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (capabilities.authentication && capabilities.credentials) {
                    if (state.socksAuthMode.showsCredentials()) {
                        credentialsItems(
                            keyPrefix = "local_proxy_socks_credentials",
                            title = labels.socksCredentialsTitle,
                            mode = state.socksAuthMode,
                            username = state.username,
                            password = state.password,
                            labels = labels,
                            wide = isWideScreen,
                            onChange = onSocksCredentialsChange,
                            onGenerate = onGenerateSocksCredentials,
                            onCopy = onCopy,
                        )
                    }
                    if (httpOn && state.httpAuthMode.showsCredentials()) {
                        credentialsItems(
                            keyPrefix = "local_proxy_http_credentials",
                            title = labels.httpCredentialsTitle,
                            mode = state.httpAuthMode,
                            username = state.httpUsername,
                            password = state.httpPassword,
                            labels = labels,
                            wide = isWideScreen,
                            onChange = onHttpCredentialsChange,
                            onGenerate = onGenerateHttpCredentials,
                            onCopy = onCopy,
                        )
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

@Composable
private fun PortField(visible: Boolean, value: String, label: String, error: String?, onValueChange: (String) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            TextField(
                value = value,
                onValueChange = { onValueChange(sanitizeLocalProxyPort(it)) },
                label = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { message ->
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AuthModeSelector(
    title: String,
    mode: LocalProxyAuthMode,
    labels: LocalProxySettingsLabels,
    onModeChange: (LocalProxyAuthMode) -> Unit,
) {
    AppWindowDropdownPreference(
        title = title,
        summary = labels.modeHint(mode),
        items = LocalProxyAuthModeOptions.map(labels::modeName),
        selectedIndex = LocalProxyAuthModeOptions.indexOf(mode).coerceAtLeast(0),
        onSelectedIndexChange = { index -> onModeChange(LocalProxyAuthModeOptions[index]) },
    )
}

private fun LazyListScope.credentialsItems(
    keyPrefix: String,
    title: String,
    mode: LocalProxyAuthMode,
    username: String,
    password: String,
    labels: LocalProxySettingsLabels,
    wide: Boolean,
    onChange: (username: String, password: String) -> Unit,
    onGenerate: () -> Unit,
    onCopy: (text: String, label: String) -> Unit,
) {
    // The generate button sits in the section header, so the card holds only the two values.
    item(key = "${keyPrefix}_title") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(text = title)
            TextButton(text = labels.generateText, onClick = onGenerate, modifier = Modifier.padding(end = 8.dp))
        }
    }
    item(key = "${keyPrefix}_card") {
        var passwordVisible by remember { mutableStateOf(false) }
        SkipiSettingsSectionCard {
            val first: @Composable () -> Unit = {
                if (mode == LocalProxyAuthMode.Manual) {
                    TextField(
                        value = username,
                        onValueChange = { onChange(it, password) },
                        label = labels.usernameLabel,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CredentialRow(
                        title = labels.usernameLabel,
                        value = username.ifBlank { "—" },
                        visible = true,
                        showText = labels.showText,
                        hideText = labels.hideText,
                        copyDescription = labels.copyUsernameDescription,
                        onCopy = { onCopy(username, labels.usernameLabel) },
                    )
                }
            }
            val second: @Composable () -> Unit = {
                if (mode == LocalProxyAuthMode.Manual) {
                    TextField(
                        value = password,
                        onValueChange = { onChange(username, it) },
                        label = labels.passwordLabel,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CredentialRow(
                        title = labels.passwordLabel,
                        value = password.ifBlank { "—" },
                        visible = passwordVisible,
                        showText = labels.showText,
                        hideText = labels.hideText,
                        copyDescription = labels.copyPasswordDescription,
                        onCopy = { onCopy(password, labels.passwordLabel) },
                        onVisibilityChange = { passwordVisible = it },
                    )
                }
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { first() }
                    Box(Modifier.weight(1f)) { second() }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    first()
                    second()
                }
            }
        }
    }
}

@Composable
private fun LanEndpointRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onCopy).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary.copy(alpha = 0.8f))
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
            Icon(MiuixIcons.Copy, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CredentialRow(
    title: String,
    value: String,
    visible: Boolean,
    showText: String,
    hideText: String,
    copyDescription: String,
    onCopy: () -> Unit,
    onVisibilityChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onCopy).padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary.copy(alpha = 0.8f))
            Spacer(Modifier.height(2.dp))
            Text(
                if (visible) value else "••••••••••••",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            onVisibilityChange?.let { change ->
                IconButton(onClick = { change(!visible) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) hideText else showText,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(MiuixIcons.Copy, contentDescription = copyDescription, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
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
    socksCredentialsTitle = stringResource(Res.string.settings_local_proxy_socks_credentials),
    httpCredentialsTitle = stringResource(Res.string.settings_local_proxy_http_credentials),
    tapToCopy = stringResource(Res.string.settings_local_proxy_tap_to_copy),
    usernameLabel = stringResource(Res.string.settings_local_proxy_username),
    passwordLabel = stringResource(Res.string.settings_local_proxy_password),
    generateText = stringResource(Res.string.settings_local_proxy_generate),
    showText = stringResource(Res.string.common_show),
    hideText = stringResource(Res.string.common_hide),
    copyUsernameDescription = stringResource(Res.string.settings_local_proxy_copy_username),
    copyPasswordDescription = stringResource(Res.string.settings_local_proxy_copy_password),
)
