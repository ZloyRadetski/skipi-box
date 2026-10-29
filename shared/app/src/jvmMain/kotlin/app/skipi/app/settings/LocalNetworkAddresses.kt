// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings

import java.net.Inet4Address
import java.net.NetworkInterface

/** IPv4 addresses of the host's own LAN interfaces, i.e. what other devices use to reach the local proxy. */
fun localNetworkIpAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.filter { it.isUp && !it.isLoopback && !it.isPointToPoint && isLocalNetworkInterfaceName(it.name) }
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.filter { it is Inet4Address && !it.isLoopbackAddress && it.hostAddress != null }
        ?.mapNotNull { it.hostAddress }
        ?.distinct()
        ?.toList()
        .orEmpty()
}.getOrDefault(emptyList())

/** The tunnel's own adapters (`skipi*`, `tun*`) are not a LAN address anybody else can reach. */
internal fun isLocalNetworkInterfaceName(name: String): Boolean =
    !name.startsWith("skipi") && !name.startsWith("tun")
