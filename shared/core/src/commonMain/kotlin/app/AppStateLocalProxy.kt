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

/** The VPN's own HTTP proxy (Android) needs the inbound even when the user did not switch it on. */
val AppState.httpProxyInboundEnabled: Boolean
    get() = enableLocalHttpProxy || enableVpnAppendHttpProxy
