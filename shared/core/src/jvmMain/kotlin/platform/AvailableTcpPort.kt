// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import java.net.InetAddress
import java.net.ServerSocket

/**
 * Reserves an ephemeral TCP port on the requested listener address.
 * The bounded retry and excluded-port behavior match Android's existing
 * `findAvailableTcpPort` operation; binding remains a host-JVM operation.
 */
fun allocateAvailableTcpPort(
    listenAddress: String,
    excludedPorts: Set<Int> = emptySet(),
    attempts: Int = 10,
): Int? {
    val address = runCatching { InetAddress.getByName(listenAddress) }.getOrNull() ?: return null
    return runCatching {
        repeat(attempts.coerceAtLeast(1)) {
            ServerSocket(0, 0, address).use { socket ->
                val port = socket.localPort
                if (port > 0 && port !in excludedPorts) return@runCatching port
            }
        }
        null
    }.getOrNull()
}
