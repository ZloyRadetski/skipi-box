// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

/** Resolves the persisted timeout using the existing Android latency-test bounds and fallback. */
fun resolveProxyHomeLatencyTimeoutMillis(value: String): Int =
    value.trim().toIntOrNull()?.coerceIn(MinPingTimeoutMillis, MaxPingTimeoutMillis)
        ?: DefaultPingTimeoutMillis

private const val DefaultPingTimeoutMillis = 5_000
private const val MinPingTimeoutMillis = 500
private const val MaxPingTimeoutMillis = 60_000
