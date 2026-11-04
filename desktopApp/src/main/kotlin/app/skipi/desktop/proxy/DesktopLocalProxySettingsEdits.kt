// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.settings.generateRandomProxyCredential
import app.skipi.ui.settings.isValidLocalProxyPort
import platform.LocalProxyAuthMode

/**
 * The SOCKS port as typed; null when it is not a usable port or collides with the HTTP port, so
 * nothing is stored (the settings library refuses equal ports).
 */
internal fun DesktopAppSettings.withLocalProxyPort(text: String): DesktopAppSettings? {
    if (!isValidLocalProxyPort(text)) return null
    val port = text.toInt()
    return if (port == localHttpProxyPort) null else copy(localProxyPort = port, localProxyPortRaw = text)
}

/** The HTTP port must be a usable port and cannot collide with the SOCKS port. */
internal fun DesktopAppSettings.withLocalHttpProxyPort(text: String): DesktopAppSettings? {
    if (!isValidLocalProxyPort(text)) return null
    val port = text.toInt()
    return if (port == localProxyPort) null else copy(localHttpProxyPort = port)
}

/** Like Android, the switch is the only way to pick the bind address: wildcard or loopback. */
internal fun DesktopAppSettings.withListenAllInterfaces(enabled: Boolean): DesktopAppSettings =
    copy(localProxyListenAddress = if (enabled) "0.0.0.0" else "127.0.0.1")

internal fun DesktopAppSettings.withSystemProxy(enabled: Boolean, supported: Boolean): DesktopAppSettings {
    val effective = supported && enabled
    return copy(useSystemProxy = effective, useWindowsSystemProxy = effective, enableLocalHttpProxy = enableLocalHttpProxy || effective)
}

/** The HTTP inbound. The system proxy needs it, so switching the inbound off switches the system proxy off. */
internal fun DesktopAppSettings.withHttpInbound(enabled: Boolean): DesktopAppSettings =
    if (enabled) copy(enableLocalHttpProxy = true)
    else copy(enableLocalHttpProxy = false, useSystemProxy = false, useWindowsSystemProxy = false)

internal fun DesktopAppSettings.socksAuthMode(): LocalProxyAuthMode =
    if (enableLocalProxyAuth) localProxyAuthMode else LocalProxyAuthMode.Off

internal fun DesktopAppSettings.httpAuthMode(): LocalProxyAuthMode =
    if (enableLocalHttpProxyAuth) localHttpProxyAuthMode else LocalProxyAuthMode.Off

/** Choosing a mode that shows a login generates one when none is stored; a stored login is never replaced. */
internal fun DesktopAppSettings.withSocksAuthMode(mode: LocalProxyAuthMode): DesktopAppSettings {
    if (mode == LocalProxyAuthMode.Off) return copy(enableLocalProxyAuth = false)
    val withMode = copy(enableLocalProxyAuth = true, localProxyAuthMode = mode)
    return if (mode.needsStoredLogin() && localProxyUsername.isBlank()) withMode.withGeneratedSocksCredentials() else withMode
}

internal fun DesktopAppSettings.withHttpAuthMode(mode: LocalProxyAuthMode): DesktopAppSettings {
    if (mode == LocalProxyAuthMode.Off) return copy(enableLocalHttpProxyAuth = false)
    val withMode = copy(enableLocalHttpProxyAuth = true, localHttpProxyAuthMode = mode)
    return if (mode.needsStoredLogin() && localHttpProxyUsername.isBlank()) withMode.withGeneratedHttpCredentials() else withMode
}

internal fun DesktopAppSettings.withSocksCredentials(username: String, password: String): DesktopAppSettings =
    copy(localProxyUsername = username, localProxyPassword = password)

internal fun DesktopAppSettings.withHttpCredentials(username: String, password: String): DesktopAppSettings =
    copy(localHttpProxyUsername = username, localHttpProxyPassword = password)

internal fun DesktopAppSettings.withGeneratedSocksCredentials(): DesktopAppSettings =
    withSocksCredentials(generateRandomProxyCredential(), generateRandomProxyCredential())

internal fun DesktopAppSettings.withGeneratedHttpCredentials(): DesktopAppSettings =
    withHttpCredentials(generateRandomProxyCredential(), generateRandomProxyCredential())

private fun LocalProxyAuthMode.needsStoredLogin(): Boolean = this == LocalProxyAuthMode.Auto || this == LocalProxyAuthMode.Manual
