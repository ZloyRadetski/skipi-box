// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import engine.network.findAvailableTcpPort

/**
 * Reserves an ephemeral TCP port on the requested listener address.
 * The bounded retry and excluded-port behavior match Android's existing
 * `findAvailableTcpPort` operation; binding remains a host-JVM operation.
 */
fun allocateAvailableTcpPort(
    listenAddress: String,
    excludedPorts: Set<Int> = emptySet(),
    attempts: Int = 10,
): Int? = findAvailableTcpPort(listenAddress, excludedPorts, attempts)
