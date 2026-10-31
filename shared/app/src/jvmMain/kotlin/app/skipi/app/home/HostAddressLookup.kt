// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress

/** Resolves [host] for display, preferring IPv4 like the tunnel's own address; null when it does not resolve. */
suspend fun lookupHostAddress(host: String): String? = withContext(Dispatchers.IO) {
    val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrNull().orEmpty()
    (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())?.hostAddress
}
