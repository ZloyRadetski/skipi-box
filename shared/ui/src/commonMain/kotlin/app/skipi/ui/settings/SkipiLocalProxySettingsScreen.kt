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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.theme.SkipiTheme
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
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class LocalProxySettingsState(
    val dynamicPort: Boolean,
    val port: String,
    val listenAllInterfaces: Boolean,
    val enableHttpEndpoint: Boolean,
    val authenticationEnabled: Boolean,
    val username: String,
    val password: String,
    val displayIp: String,
    val socksEndpoint: String,
    val httpEndpoint: String,
    val portError: String?,
)

data class LocalProxySettingsLabels(
    val screenTitle: String,
    val networkSectionTitle: String,
    val dynamicPortTitle: String,
    val dynamicPortSummary: String,
    val portLabel: String,
    val listenAllTitle: String,
    val listenAllSummary: String,
    val lanSectionTitle: String,
    val lanInstruction: String,
    val ipLabel: String,
    val socksEndpointLabel: String,
    val httpEndpointLabel: String,
    val securitySectionTitle: String,
    val authenticationTitle: String,
    val authenticationSummary: String,
    val credentialsSectionTitle: String,
    val authorizationTitle: String,
    val tapToCopy: String,
    val usernameLabel: String,
    val passwordLabel: String,
    val generateText: String,
    val showText: String,
    val hideText: String,
    val copyUsernameDescription: String,
    val copyPasswordDescription: String,
)

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
    onListenAllInterfacesChange: (Boolean) -> Unit,
    onAuthenticationChange: (Boolean) -> Unit,
    onGenerateCredentials: () -> Unit,
    onCopy: (text: String, label: String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize(),
        topBar = { key(labels.screenTitle) { SettingsBackTopBar(labels.screenTitle, isWideScreen, onBack) } },
    ) { innerPadding ->
        val contentPadding = settingsPageContentPadding(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "local_proxy_network_title") { SmallTitle(text = labels.networkSectionTitle) }
                item(key = "local_proxy_network_card") {
                    SkipiSettingsSectionCard {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            SwitchPreference(
                                title = labels.dynamicPortTitle,
                                summary = labels.dynamicPortSummary,
                                checked = state.dynamicPort,
                                onCheckedChange = onDynamicPortChange,
                            )
                            AnimatedVisibility(
                                visible = !state.dynamicPort,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    TextField(
                                        value = state.port,
                                        onValueChange = { onPortChange(sanitizeLocalProxyPort(it)) },
                                        label = labels.portLabel,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    state.portError?.let { error ->
                                        Text(
                                            text = error,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                        )
                                    }
                                }
                            }
                            SwitchPreference(
                                title = labels.listenAllTitle,
                                summary = labels.listenAllSummary,
                                checked = state.listenAllInterfaces,
                                onCheckedChange = onListenAllInterfacesChange,
                            )
                        }
                    }
                }
                if (state.listenAllInterfaces) {
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
                                if (state.enableHttpEndpoint) {
                                    Spacer(Modifier.height(10.dp))
                                    LanEndpointRow(labels.httpEndpointLabel, state.httpEndpoint) {
                                        onCopy(state.httpEndpoint, labels.httpEndpointLabel)
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "local_proxy_security_title") { SmallTitle(text = labels.securitySectionTitle) }
                item(key = "local_proxy_security_card") {
                    SkipiSettingsSectionCard {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            SwitchPreference(
                                title = labels.authenticationTitle,
                                summary = labels.authenticationSummary,
                                checked = state.authenticationEnabled,
                                onCheckedChange = onAuthenticationChange,
                            )
                        }
                    }
                }
                if (state.authenticationEnabled) {
                    item(key = "local_proxy_credentials_title") { SmallTitle(text = labels.credentialsSectionTitle) }
                    item(key = "local_proxy_credentials_card") {
                        SkipiSettingsSectionCard {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = labels.authorizationTitle,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = labels.tapToCopy,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        )
                                    }
                                    TextButton(text = labels.generateText, onClick = onGenerateCredentials)
                                }
                                Spacer(Modifier.height(14.dp))
                                CredentialRow(
                                    title = labels.usernameLabelOrFallback(),
                                    value = state.username.ifBlank { "—" },
                                    visible = true,
                                    showText = labels.showText,
                                    hideText = labels.hideText,
                                    copyDescription = labels.copyUsernameDescription,
                                    onCopy = { onCopy(state.username, labels.usernameLabelOrFallback()) },
                                )
                                Spacer(Modifier.height(10.dp))
                                CredentialRow(
                                    title = labels.passwordLabelOrFallback(),
                                    value = state.password.ifBlank { "—" },
                                    visible = passwordVisible,
                                    showText = labels.showText,
                                    hideText = labels.hideText,
                                    copyDescription = labels.copyPasswordDescription,
                                    onCopy = { onCopy(state.password, labels.passwordLabelOrFallback()) },
                                    onVisibilityChange = { passwordVisible = it },
                                )
                            }
                        }
                    }
                }
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
            .clickable(onClick = onCopy).padding(horizontal = 14.dp, vertical = 10.dp),
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

private fun LocalProxySettingsLabels.usernameLabelOrFallback(): String = usernameLabel
private fun LocalProxySettingsLabels.passwordLabelOrFallback(): String = passwordLabel
