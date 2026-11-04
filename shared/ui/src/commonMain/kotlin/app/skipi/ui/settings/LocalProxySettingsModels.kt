// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import platform.LocalProxyAuthMode

data class LocalProxySettingsState(
    val dynamicPort: Boolean,
    val port: String,
    val listenAllInterfaces: Boolean,
    /** The HTTP inbound is switched on. */
    val enableHttpEndpoint: Boolean,
    val httpPort: String,
    val socksAuthMode: LocalProxyAuthMode,
    val httpAuthMode: LocalProxyAuthMode,
    val username: String,
    val password: String,
    val httpUsername: String,
    val httpPassword: String,
    val displayIp: String,
    val socksEndpoint: String,
    val httpEndpoint: String,
    val portError: String?,
    val httpPortError: String? = null,
)

data class LocalProxySettingsLabels(
    val screenTitle: String,
    val networkSectionTitle: String,
    val dynamicPortTitle: String,
    val dynamicPortSummary: String,
    val portLabel: String,
    val httpInboundTitle: String,
    val httpInboundSummary: String,
    val httpPortLabel: String,
    val listenAllTitle: String,
    val listenAllSummary: String,
    val lanSectionTitle: String,
    val lanInstruction: String,
    val ipLabel: String,
    val socksEndpointLabel: String,
    val httpEndpointLabel: String,
    val securitySectionTitle: String,
    val socksAuthModeTitle: String,
    val httpAuthModeTitle: String,
    val authModeAuto: String,
    val authModeManual: String,
    val authModeFromJson: String,
    val authModeOff: String,
    val authHintAuto: String,
    val authHintManual: String,
    val authHintFromJson: String,
    val httpAuthSystemNote: String,
    val socksCredentialsTitle: String,
    val httpCredentialsTitle: String,
    val tapToCopy: String,
    val usernameLabel: String,
    val passwordLabel: String,
    val generateText: String,
    val showText: String,
    val hideText: String,
    val copyUsernameDescription: String,
    val copyPasswordDescription: String,
)

data class LocalProxySettingsCapabilities(
    val dynamicPort: Boolean = true,
    val httpInbound: Boolean = true,
    val listenAllInterfaces: Boolean = true,
    val authentication: Boolean = true,
    val credentials: Boolean = true,
    val lanEndpoints: Boolean = true,
)

/** The modes in the order the selector lists them. */
val LocalProxyAuthModeOptions: List<LocalProxyAuthMode> = listOf(
    LocalProxyAuthMode.Auto,
    LocalProxyAuthMode.Manual,
    LocalProxyAuthMode.FromJson,
    LocalProxyAuthMode.Off,
)

/** Only these modes carry a login the user sees: the generated one (auto) or the typed one (manual). */
fun LocalProxyAuthMode.showsCredentials(): Boolean =
    this == LocalProxyAuthMode.Auto || this == LocalProxyAuthMode.Manual

internal fun LocalProxySettingsLabels.modeName(mode: LocalProxyAuthMode): String = when (mode) {
    LocalProxyAuthMode.Auto -> authModeAuto
    LocalProxyAuthMode.Manual -> authModeManual
    LocalProxyAuthMode.FromJson -> authModeFromJson
    LocalProxyAuthMode.Off -> authModeOff
}

internal fun LocalProxySettingsLabels.modeHint(mode: LocalProxyAuthMode): String? = when (mode) {
    LocalProxyAuthMode.Auto -> authHintAuto
    LocalProxyAuthMode.Manual -> authHintManual
    LocalProxyAuthMode.FromJson -> authHintFromJson
    LocalProxyAuthMode.Off -> null
}
