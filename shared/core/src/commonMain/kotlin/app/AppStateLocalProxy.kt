// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import platform.LocalProxyAuthMode
import platform.LocalProxyCredentials

/**
 * Each local inbound keeps an on/off switch plus the last real mode, so a backup that only knows the
 * old SOCKS switch still reads correctly and switching "Off" back on returns to the previous mode.
 */
fun AppState.localProxySocksAuthMode(): LocalProxyAuthMode =
    if (enableLocalProxyAuth) localProxyAuthMode else LocalProxyAuthMode.Off

fun AppState.localProxyHttpAuthMode(): LocalProxyAuthMode =
    if (enableLocalHttpProxyAuth) localHttpProxyAuthMode else LocalProxyAuthMode.Off

fun AppState.withLocalProxySocksAuthMode(mode: LocalProxyAuthMode): AppState =
    if (mode == LocalProxyAuthMode.Off) copy(enableLocalProxyAuth = false)
    else copy(enableLocalProxyAuth = true, localProxyAuthMode = mode)

fun AppState.withLocalProxyHttpAuthMode(mode: LocalProxyAuthMode): AppState =
    if (mode == LocalProxyAuthMode.Off) copy(enableLocalHttpProxyAuth = false)
    else copy(enableLocalHttpProxyAuth = true, localHttpProxyAuthMode = mode)

fun AppState.localProxySocksCredentials() = LocalProxyCredentials(localProxyUsername, localProxyPassword)

fun AppState.localProxyHttpCredentials() = LocalProxyCredentials(localHttpProxyUsername, localHttpProxyPassword)

/** The HTTP proxy is on: its switch, or the legacy VPN switch of an older version, which is no longer shown. */
val AppState.httpProxyInboundEnabled: Boolean
    get() = enableLocalHttpProxy || enableVpnAppendHttpProxy

/** The one HTTP proxy switch; switching it off also clears the legacy VPN switch so nothing keeps the inbound on. */
fun AppState.withLocalHttpProxyInbound(enabled: Boolean): AppState =
    if (enabled) copy(enableLocalHttpProxy = true) else copy(enableLocalHttpProxy = false, enableVpnAppendHttpProxy = false)
