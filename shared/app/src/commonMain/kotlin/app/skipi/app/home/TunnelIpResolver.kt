// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import features.tools.ipinfo.IpInfoData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Pauses before each retry: a freshly started tunnel often passes no traffic for a moment. */
val DefaultTunnelIpRetryDelaysMs: List<Long> = listOf(1_500L, 4_000L, 10_000L)

/**
 * Resolves the public address of the running tunnel for the Home panel, or null when every attempt
 * failed. IPv4 is preferred for display; a lookup that returns no address counts as failed.
 */
suspend fun resolveTunnelIp(
    retryDelaysMs: List<Long> = DefaultTunnelIpRetryDelaysMs,
    fetch: suspend () -> IpInfoData,
): ProxyHomeTunnelIp? {
    for (attempt in 0..retryDelaysMs.size) {
        if (attempt > 0) delay(retryDelaysMs[attempt - 1])
        val info = try {
            fetch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val address = info?.ipv4?.takeIf(String::isNotBlank) ?: info?.ipv6?.takeIf(String::isNotBlank)
        if (info != null && address != null) {
            return ProxyHomeTunnelIp(value = address, flag = info.flagEmoji.takeIf(String::isNotBlank))
        }
    }
    return null
}
