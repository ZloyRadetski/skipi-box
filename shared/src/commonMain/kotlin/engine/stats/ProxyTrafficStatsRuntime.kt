// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import engine.xray.XrayStatsApiTag

data class ProxyTrafficStatsRuntime(
    val listenAddress: String,
    val port: Int,
    val serverName: String,
    val apiTag: String = XrayStatsApiTag,
    val finalOutboundTag: String = "proxy",
    val selectedServerId: Int = -1,
    val startedAtElapsedRealtime: Long = 0L,
    val paused: Boolean = false,
    val pausedAtElapsedRealtime: Long = 0L,
)
