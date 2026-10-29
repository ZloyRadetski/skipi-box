// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.settings.isValidLocalProxyPort

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
    return copy(useSystemProxy = effective, useWindowsSystemProxy = effective)
}
