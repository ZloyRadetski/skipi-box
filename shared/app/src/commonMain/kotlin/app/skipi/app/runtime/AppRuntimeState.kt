// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.runtime

import platform.TunnelSnapshot

/**
 * Volatile process state.  Tunnel lifecycle and traffic always come from the
 * core [TunnelSnapshot]; there is intentionally no persisted `proxyRunning`
 * flag in the application layer.
 */
data class AppRuntimeState(
    val tunnel: TunnelSnapshot = TunnelSnapshot(),
    val latencyByServerId: Map<Int, Long> = emptyMap(),
    val testingServerIds: Set<Int> = emptySet(),
    val refreshingSubscriptionIds: Set<Int> = emptySet(),
    val message: RuntimeMessage? = null,
)

data class RuntimeMessage(
    val text: String,
    val isError: Boolean = false,
)
