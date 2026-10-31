// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import engine.network.NetworkDefaults

/** Resolves the persisted timeout using the existing Android latency-test bounds and fallback. */
fun resolveProxyHomeLatencyTimeoutMillis(value: String): Int =
    value.trim().toIntOrNull()?.coerceIn(MinPingTimeoutMillis, MaxPingTimeoutMillis)
        ?: DefaultPingTimeoutMillis

/** Resolves the persisted ping address with the existing Android rule: http(s) only, otherwise the connectivity check. */
fun resolveProxyHomeLatencyUrl(value: String): String =
    value.trim().takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?: NetworkDefaults.CONNECTIVITY_CHECK_URL

private const val DefaultPingTimeoutMillis = 5_000
private const val MinPingTimeoutMillis = 500
private const val MaxPingTimeoutMillis = 60_000
